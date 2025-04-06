package com.example.EntityResolution_3.processor;

import com.example.EntityResolution_3.model.Company;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.hadoop.fs.Path;
import java.util.*;

public class ParquetProcessor {

    public static List<Company> readParquetFile(String parquetFilePath) throws Exception {
        List<Company> companies = new ArrayList<>();

        try (ParquetReader<GenericRecord> reader = AvroParquetReader.<GenericRecord>builder(new Path(parquetFilePath)).build()) {
            GenericRecord record;
            while ((record = reader.read()) != null) {
                Company company = new Company();
                company.setCompany_name(record.get("company_name") != null ? record.get("company_name").toString() : "");
                company.setMain_address_raw_text(record.get("main_address_raw_text") != null ? record.get("main_address_raw_text").toString() : "");
                company.setPrimary_email(record.get("primary_email") != null ? record.get("primary_email").toString() : "");
                company.setPrimary_phone(record.get("primary_phone") != null ? record.get("primary_phone").toString() : "");
                company.setWebsite_url(record.get("website_url") != null ? record.get("website_url").toString() : "");
                companies.add(company);
            }
        }
        return companies;
    }
}