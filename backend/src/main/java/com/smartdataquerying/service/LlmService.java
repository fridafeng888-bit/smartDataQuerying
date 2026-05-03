package com.smartdataquerying.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.dto.LlmDtos.LlmConfigRequest;
import com.smartdataquerying.dto.LlmDtos.LlmConfigResponse;
import com.smartdataquerying.dto.LlmDtos.LlmTestResponse;
import com.smartdataquerying.model.LlmConfig;
import com.smartdataquerying.repository.LlmConfigRepository;
import com.smartdataquerying.security.CryptoService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class LlmService {
    private final LlmConfigRepository repository;
    private final CryptoService cryptoService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public LlmService(LlmConfigRepository repository, CryptoService cryptoService,
                      AppProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public LlmConfigResponse get() {
        LlmConfig config = load();
        return new LlmConfigResponse(config.id, config.baseUrl, config.model,
                config.encryptedApiKey != null && !config.encryptedApiKey.isBlank());
    }

    public LlmConfigResponse save(LlmConfigRequest request) {
        LlmConfig config = repository.findFirstByOrderByIdAsc().orElseGet(LlmConfig::new);
        config.baseUrl = stripTrailingSlash(request.baseUrl());
        config.model = request.model();
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.encryptedApiKey = cryptoService.encrypt(request.apiKey());
        }
        return toResponse(repository.save(config));
    }

    public LlmTestResponse test() {
        try {
            String content = complete("Return JSON only: {\"ok\":true}");
            return new LlmTestResponse(true, content);
        } catch (Exception ex) {
            return new LlmTestResponse(false, ex.getMessage());
        }
    }

    public String complete(String prompt) {
        LlmConfig config = load();
        String apiKey = cryptoService.decrypt(config.encryptedApiKey);
        Map<String, Object> body = Map.of(
                "model", config.model,
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a strict Text-to-SQL assistant. Return JSON only."),
                        Map.of("role", "user", "content", prompt)
                )
        );
        WebClient client = WebClient.builder()
                .baseUrl(stripTrailingSlash(config.baseUrl))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        String raw = client.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(raw);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid LLM response: " + raw, ex);
        }
    }

    private LlmConfig load() {
        return repository.findFirstByOrderByIdAsc().orElseGet(() -> {
            LlmConfig config = new LlmConfig();
            config.baseUrl = stripTrailingSlash(properties.llm().baseUrl());
            config.model = properties.llm().model();
            config.encryptedApiKey = cryptoService.encrypt(properties.llm().apiKey());
            return repository.save(config);
        });
    }

    private LlmConfigResponse toResponse(LlmConfig config) {
        return new LlmConfigResponse(config.id, config.baseUrl, config.model,
                config.encryptedApiKey != null && !config.encryptedApiKey.isBlank());
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://api.openai.com/v1";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

