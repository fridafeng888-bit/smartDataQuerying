package com.smartdataquerying.dto;

public class LlmDtos {
    public record LlmConfigRequest(String baseUrl, String model, String apiKey) {
    }

    public record LlmConfigResponse(Long id, String baseUrl, String model, boolean hasApiKey) {
    }

    public record LlmTestResponse(boolean connected, String message) {
    }
}

