package com.pnimac.comment.error;

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
    private final ObjectMapper mapper;

    public ProblemAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        ProblemSupport.write(mapper, request, response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required");
    }

    public void invalidToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemSupport.write(mapper, request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                "JWT token is invalid or expired");
    }
}
