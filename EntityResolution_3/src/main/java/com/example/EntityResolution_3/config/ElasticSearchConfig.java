package com.example.EntityResolution_3.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Unused class for elastic search
// TODO: Solve ElasticSearch not starting
@Configuration
public class ElasticSearchConfig {

	@Bean
	public RestClient restClient() {
		return RestClient.builder(new HttpHost("localhost", 9200, "http")).build();
	}

	@Bean
	public ElasticsearchClient elasticsearchClient(RestClient restClient) {
		RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
		return new ElasticsearchClient(transport);
	}
}