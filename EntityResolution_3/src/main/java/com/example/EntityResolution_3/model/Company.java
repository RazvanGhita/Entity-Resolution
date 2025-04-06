package com.example.EntityResolution_3.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

// Company has only the fields which I thought to be necessary for deduplication
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company implements Serializable {

    String company_name;
    String main_address_raw_text;
    String primary_phone;
    String primary_email;
    String website_url;

    }


