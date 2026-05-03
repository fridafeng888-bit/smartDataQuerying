package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.AuthDtos.LoginRequest;
import com.smartdataquerying.dto.AuthDtos.LoginResponse;
import com.smartdataquerying.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication authentication) {
        return ApiResponse.ok(Map.of("username", authentication.getName()));
    }
}

