package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.dto.AuthDtos.LoginRequest;
import com.smartdataquerying.dto.AuthDtos.LoginResponse;
import com.smartdataquerying.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AppProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppProperties properties, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        if (!properties.admin().username().equals(request.username())
                || !matches(request.password(), properties.admin().passwordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }
        return new LoginResponse(jwtService.issue(request.username()), request.username());
    }

    private boolean matches(String raw, String encoded) {
        if (encoded != null && encoded.startsWith("{plain}")) {
            return encoded.substring("{plain}".length()).equals(raw);
        }
        return passwordEncoder.matches(raw, encoded);
    }
}
