package com.hmall.search.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "hm.es")
public class ElasticSearchConfig {

    private String host;
    private Integer port;

    @javax.annotation.PostConstruct
    public void init() {
        log.info("ES配置加载：host={}, port={}", host, port);
    }

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://" + host + ":" + port)
        ));
    }
}
