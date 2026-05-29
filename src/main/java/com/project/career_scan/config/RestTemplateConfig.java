package com.project.career_scan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // How long to wait for connection to Groq server (10 seconds)
        factory.setConnectTimeout(10_000);

        // How long to wait for Groq to respond after connected (30 seconds)
        factory.setReadTimeout(30_000);

        return new RestTemplate(factory);
    }
}
