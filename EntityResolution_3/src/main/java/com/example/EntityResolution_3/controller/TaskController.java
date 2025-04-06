package com.example.EntityResolution_3.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.EntityResolution_3.model.Company;
import com.example.EntityResolution_3.processor.ParquetProcessor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.similarity.JaccardSimilarity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/deduplication")
public class TaskController {

    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);

    @Autowired
    private ElasticsearchClient elasticSearchClient ;

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/companies_db","root","Tiggynika123$");
    }

    @Value("${parquet.file.path}")
    private String parquetFilePath;

    //Main Entry Point
    @PostMapping("/process")
    public String processDeduplication(@RequestParam String parquetFilePath) throws Exception{

        //Read the companies from the Parquet file
       List<Company> companies = ParquetProcessor.readParquetFile(parquetFilePath);

        //Cluster similiar companies using Jaccard similarity
       List<Set<Company>> clusters = clusterCompanies(companies);

        //Store the single representatives and clustered duplicates in MySQL DB
       storeResultsInDatabase(clusters);
       // indexToElasticSearch(clusters);
        return "Deduplication Completed";
    }


    //For every company, compare it to all others using the areSimilar method
    //If they are similar add id to the same Set<Company> (a cluster)
    //Each cluster is added to a synchronized list
    //TODO: Works fine for smaller databases. Find a better solution for large datasets
    private static List<Set<Company>> clusterCompanies(List<Company> companies) throws Exception{

        List<Set<Company>> clusters = Collections.synchronizedList(new ArrayList<>());

        companies.parallelStream().forEach(company -> {
            Set<Company> cluster = new HashSet<>();
            cluster.add(company);

            companies.stream()
                    .filter(other -> areSimilar(company,other))
                    .forEach(cluster::add);

            synchronized (clusters) {
                clusters.add(cluster);
            }
        });

        return clusters;
    }

    //Compare each field, name & address having more weight than the other fields
    //Returns total score and compare it to the SIMILARITY_THRESHOLD
    private static boolean areSimilar(Company c1, Company c2){

        JaccardSimilarity jaccard = new JaccardSimilarity();

        double nameSimilarity = jaccard.apply(normalize(c1.getCompany_name()),normalize(c2.getCompany_name()));
        double addressSimilarity = jaccard.apply(normalize(c1.getMain_address_raw_text()), normalize(c2.getMain_address_raw_text()));
        double emailSimilarity = jaccard.apply(normalize(c1.getPrimary_email()), normalize(c2.getPrimary_email()));
        double phoneSimilarity = jaccard.apply(normalize(c1.getPrimary_phone()), normalize(c2.getPrimary_phone()));
        double websiteSimilarity = jaccard.apply(normalize(c1.getWebsite_url()), normalize(c2.getWebsite_url()));

        double totalScore = (nameSimilarity * 0.4) + (addressSimilarity * 0.3) +
                            (emailSimilarity * 0.1) + (phoneSimilarity * 0.1) +
                            (websiteSimilarity * 0.1);

        return totalScore >= SIMILARITY_THRESHOLD;
    }
    //Data cleaning before similarity - Strips punctuation&/symbols and lowers case and ensures fuzzy match isn't confused by formatting differences
    private static String normalize(String text){

        return text.toLowerCase().replaceAll("[^a-z0-9]","").trim();
    }

    //Inserts the representative of each cluster (first company) and then inserts all other members wih a reference to the cluster
    private static void storeResultsInDatabase(List<Set<Company>> clusters) throws Exception {

        try (Connection conn = getConnection()){
            conn.setAutoCommit(false);

            String sql = "INSERT INTO deduplicated_companies (company_name, address, email, phone, website_url, cluster_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "company_name = VALUES(company_name), " +
                    "address = VALUES(address), " +
                    "email = VALUES(email), " +
                    "phone = VALUES(phone), " +
                    "website_url = VALUES(website_url), "+
                    "cluster_id = VALUES(cluster_id)";

            String getLastInsertedId = "SELECT LAST_INSERT_ID()";

            //Cluster members - Will only update cluster ID if a match is already there
            String insertDuplicateSql = "INSERT INTO deduplicated_companies (company_name, address, email, phone, website_url, cluster_id) "+
                                        "VALUES (?, ?, ?, ?, ?, ?)" +
                                        "ON DUPLICATE KEY UPDATE cluster_id = VALUES(cluster_id)";

            try (PreparedStatement representativeStatement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement duplicateStatement = conn.prepareStatement(insertDuplicateSql)){
                for(Set<Company> cluster : clusters) {
                    Company representative = cluster.iterator().next();

                    String company = representative.getCompany_name();
                    String address = representative.getMain_address_raw_text();
                    String email = representative.getPrimary_email();
                    String phone = representative.getPrimary_phone();
                    String website = representative.getWebsite_url();
                    representativeStatement.setString(1,notBlank(company) ? company : "UNKNOWN");
                    representativeStatement.setString(2,notBlank(address) ? address : "UNKNOWN");
                    representativeStatement.setString(3,notBlank(email) ? email : "UNKNOWN");
                    representativeStatement.setString(4,notBlank(phone) ? phone : "UNKNOWN");
                    representativeStatement.setString(5,notBlank(website) ? website : null);
                    representativeStatement.setInt(6, 0);
                    representativeStatement.executeUpdate();

                    //The first company in each cluster is saved
                    ResultSet rs = representativeStatement.getGeneratedKeys();
                    int clusterId = 0;
                    if (rs.next()){
                        clusterId = rs.getInt(1);
                    }
                    rs.close();

                    for (Company duplicate : cluster){
                        if(!duplicate.equals(representative)){
                            duplicateStatement.setString(1,notBlank(company) ? company : "UNKNOWN");
                            duplicateStatement.setString(2,notBlank(address) ? address : "UNKNOWN");
                            duplicateStatement.setString(3,notBlank(email) ? email : "UNKNOWN");
                            duplicateStatement.setString(4, notBlank(phone) ? phone : "UNKNOWN");
                            duplicateStatement.setString(5, notBlank(website) ? website : null);
                            duplicateStatement.setInt(6, clusterId);
                            duplicateStatement.addBatch();
                        }
                    }
                    duplicateStatement.executeBatch();
                }
            }
            conn.commit();
        }

    }

    //TODO: Solve ElasticSearch not starting
   /* private void indexToElasticSearch(List<Set<Company>> clusters) throws IOException {

        for (Set<Company> cluster : clusters) {
            if (cluster.isEmpty()) continue; // Avoid errors with empty clusters

            Company representative = cluster.iterator().next(); // Get a representative company

            // Creating the document as a Map
            Map<String, Object> document = new HashMap<>();
            document.put("company_name", representative.getCompany_name());
            document.put("main_address_raw_text", representative.getMain_address_raw_text());
            document.put("email", representative.getPrimary_email());
            document.put("phone", representative.getPrimary_phone());

            // Construct the index request using the ElasticsearchClient API
            IndexRequest request = new IndexRequest.Builder()
                    .index("companies")  // Index name
                    .document(document)   // Document content
                    .build();

            // Perform the index operation
            elasticSearchClient.index(request);
        }
    }*/

    //Checks whether a value is non-empty before inserting
    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

}
