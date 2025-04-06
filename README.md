# Entity-Resolution
Company deduplication task.

Problem: 
As I understood, this challenge is to identify and eliminate duplicate company entries from a dataset stored in a Parquet file. These duplicates often have slightly varied attributes such as company names written differently, different addresses for the same entity, missing data, etc.

Thinking method: 
I approached this challenge with the following steps in mind : Read & Parse Data (read file with AvroParquetReader) -> Preprocess Data (normalize text fields) -> Define Unique Identifiers (I chose the combination of company name, address, email, phone and website) -> Detect Duplicates -> Group & Resolve Duplicates -> Output the now "cleaned" data

Why this technology: 
I chose Java because is well-suited for data-intensive and for structed entity resolution due to robust libraries for handling text comparison (Apache Lucene), databases (Hibernate), and parallel processing. In addition I could implement the Spring Boot framework for security access when working with sensitive data, simplified REST API creation and database integration and also easy integration wih JDBC and libraries like Apache Parquet / Apache Commons, although I met some problems which are talked about in Issues.

Why this solution: 
I chose similarity-based clustering approach (with Jaccard Similarity) because it allows comparison of text-based fields(e.g., names, emails) even when they are not identical and gives you flexibility to weigh certain fields more than others ( e.g., company name, address). This works because, unlike deterministic deduplication, this method accounts for fuzzy matches and near-duplicates. Jaccard Similarity is used to measure overlap between normalized text tokens. Normalized fields (removed symbols, lowercase etc.) are standerdized variations before comparison. This ensures fields like "Google Inc." and "google" are treated as similar.
MySQL was used for persistent storage because of it's reliablity, easy to use, and supporting constraints out of the box (ON DUPLICATE KEY UPDATE) to prevent re-inserting the same entity.
This method is accurate, flexible, extendable and traceable (cluster_id helps visualize which records were linked), but lacks efficiency (see Issues).

Issues:
At first I tried implementing Apache Spark for handling a large number of records efficiently and Elasticsearch for fast fuzzy search, but after a long time of trying to solve SparkSession i chose to abandon this method as I read that after SpringBoot 3+ and Java 17+, javax dependecy was changed to jakarta and SparkSession couldn't work without some elements from javax. So I had to choose to downgrade Java and SpringBoot versions or try something else. So I simplified the task using AvroParquetReader, but sacrificed efficiency. This method handles small and moderate sized datasets with good performance, but struggles with large datasets.
Another issue that I had was with Elasticsearch, which I implemented in the project, but later couldn't use because I tried almost everything to start Elasticsearch but to no use as it always got stuck before starting.

Fixes:
Finding a fix for Elasticsearch for faster data reading and move clustering to Elasticsearch, avoiding in-memory comparisons.
Implementing streaming the parquet file instead of loading entirely (with Apache Arrow).
Multithreaded / Parallel Proccesing with Partitioning the data.








