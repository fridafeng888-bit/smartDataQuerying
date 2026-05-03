package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.ChatDtos.*;
import com.smartdataquerying.model.ChatMessage;
import com.smartdataquerying.model.ChatSession;
import com.smartdataquerying.model.QueryExecution;
import com.smartdataquerying.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping("/api/chat/sessions")
    public ApiResponse<ChatSession> createSession(@RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(service.createSession(request));
    }

    @GetMapping("/api/chat/sessions")
    public ApiResponse<List<ChatSession>> sessions() {
        return ApiResponse.ok(service.sessions());
    }

    @GetMapping("/api/chat/sessions/{id}/messages")
    public ApiResponse<List<ChatMessage>> messages(@PathVariable Long id) {
        return ApiResponse.ok(service.messages(id));
    }

    @PostMapping("/api/chat/ask")
    public ApiResponse<AskResponse> ask(@RequestBody AskRequest request) {
        return ApiResponse.ok(service.ask(request));
    }

    @PostMapping("/api/query/validate")
    public ApiResponse<ValidateResponse> validate(@RequestBody ValidateRequest request) {
        return ApiResponse.ok(service.validate(request.datasourceId(), request.sql()));
    }

    @GetMapping("/api/query/executions")
    public ApiResponse<List<QueryExecution>> executions() {
        return ApiResponse.ok(service.executions());
    }
}

