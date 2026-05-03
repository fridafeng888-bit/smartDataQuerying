package com.smartdataquerying.dto;

import java.util.List;
import java.util.Map;

public class ChatDtos {
    public record CreateSessionRequest(String title) {
    }

    public record AskRequest(Long sessionId, Long datasourceId, String question) {
    }

    public record AskResponse(Long executionId, String sql, String explanation, long durationMs,
                              int rowCount, List<String> columns, List<Map<String, Object>> rows) {
    }

    public record ValidateRequest(Long datasourceId, String sql) {
    }

    public record ValidateResponse(boolean valid, String sql, String message) {
    }
}

