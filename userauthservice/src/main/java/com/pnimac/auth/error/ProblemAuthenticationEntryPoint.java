package com.pnimac.auth.error;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        ProblemSupport.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required");
    }

    public void invalidToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemSupport.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "JWT token is invalid or expired");
    }
}
