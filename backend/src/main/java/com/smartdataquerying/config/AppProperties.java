package com.smartdataquerying.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Admin admin, Jwt jwt, Encryption encryption, Query query, Llm llm, Excel excel) {
    public record Admin(String username, String passwordHash) {
    }

    public record Jwt(String secret, long ttlMinutes) {
    }

    public record Encryption(String secret) {
    }

    public record Query(long timeoutMs, int defaultLimit, int maxLimit) {
    }

    public record Llm(String baseUrl, String apiKey, String model) {
    }

    public record Excel(int maxRows, int sampleRows) {
    }
}
