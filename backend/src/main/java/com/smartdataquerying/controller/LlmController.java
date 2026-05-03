package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.LlmDtos.LlmConfigRequest;
import com.smartdataquerying.dto.LlmDtos.LlmConfigResponse;
import com.smartdataquerying.dto.LlmDtos.LlmTestResponse;
import com.smartdataquerying.service.LlmService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/llm")
public class LlmController {
    private final LlmService service;

    public LlmController(LlmService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<LlmConfigResponse> get() {
        return ApiResponse.ok(service.get());
    }

    @PatchMapping
    public ApiResponse<LlmConfigResponse> save(@RequestBody LlmConfigRequest request) {
        return ApiResponse.ok(service.save(request));
    }

    @PostMapping("/test")
    public ApiResponse<LlmTestResponse> test() {
        return ApiResponse.ok(service.test());
    }
}

