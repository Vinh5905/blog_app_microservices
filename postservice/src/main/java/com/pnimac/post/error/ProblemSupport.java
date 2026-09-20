package com.pnimac.post.error;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class ProblemSupport {
    private ProblemSupport() {
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail, String requestUri) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:blogapp:problem:" + code.toLowerCase().replace('_', '-')));
        problem.setInstance(URI.create(requestUri));
        problem.setProperty("code", code);
        return problem;
    }

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), create(status, code, detail, request.getRequestURI()));
    }
}
