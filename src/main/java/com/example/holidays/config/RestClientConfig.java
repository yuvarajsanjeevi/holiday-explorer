package com.example.holidays.config;

import com.example.holidays.exception.UpstreamUnavailableException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.RestClient;

/** Timeouts come from {@code spring.http.client.*}; without them a stuck call upstream holds a request thread forever. */
@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    @Bean
    public RestClient nagerRestClient(RestClient.Builder builder, NagerDateProperties properties) {
        log.info("Holiday API base URL: {}", properties.baseUrl());
        return builder.baseUrl(properties.baseUrl()).build();
    }

    /** 5xx and I/O failures only - a 4xx will not succeed on a second try and just adds load to a free API. */
    @Bean
    public RetryTemplate nagerRetryTemplate(NagerDateProperties properties) {
        log.info("Retrying upstream failures up to {} time(s), starting at {} and capped at {}",
                properties.maxRetries(), properties.retryDelay(), properties.retryMaxDelay());
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(2.0)
                .maxDelay(properties.retryMaxDelay())
                .includes(UpstreamUnavailableException.class, IOException.class)
                .build();
        return new RetryTemplate(policy);
    }
}
