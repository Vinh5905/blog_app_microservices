package com.pnimac.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesIT {
    private static final WireMockServer AUTH = server();
    private static final WireMockServer POST = server();
    private static final WireMockServer COMMENT = server();

    static {
        AUTH.start();
        POST.start();
        COMMENT.start();
    }

    @Autowired WebTestClient client;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("AUTH_SERVICE_URL", AUTH::baseUrl);
        registry.add("POST_SERVICE_URL", POST::baseUrl);
        registry.add("COMMENT_SERVICE_URL", COMMENT::baseUrl);
        registry.add("MANAGEMENT_PORT", () -> "0");
    }

    @BeforeEach
    void reset() {
        AUTH.resetAll();
        POST.resetAll();
        COMMENT.resetAll();
    }

    @AfterAll
    static void stopServers() {
        AUTH.stop();
        POST.stop();
        COMMENT.stop();
    }

    /**
     * Verifies that /api/auth traffic reaches only the auth upstream while preserving
     * the complete path and query string. This protects the same-origin API contract
     * from route overlap, StripPrefix, or accidental path rewriting.
     */
    @Test
    void routesAuthPathWithoutRewritingIt() {
        AUTH.stubFor(get(urlPathEqualTo("/api/auth/user")).withQueryParam("source", equalTo("test"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"service\":\"auth\"}")));

        client.get().uri("/api/auth/user?source=test").exchange().expectStatus().isOk()
                .expectHeader().contentType("application/json").expectBody().jsonPath("$.service").isEqualTo("auth");

        AUTH.verify(getRequestedFor(urlPathEqualTo("/api/auth/user")).withQueryParam("source", equalTo("test")));
        POST.verify(0, getRequestedFor(urlPathEqualTo("/api/auth/user")));
        COMMENT.verify(0, getRequestedFor(urlPathEqualTo("/api/auth/user")));
    }

    /**
     * Verifies that /api/post traffic reaches the post upstream, not the auth upstream,
     * and that the upstream response is returned unchanged. This protects the dedicated
     * post route from the duplicate or overlapping route configuration found originally.
     */
    @Test
    void routesPostPathToPostOnly() {
        POST.stubFor(get(urlPathEqualTo("/api/post/getAllPosts"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("[]")));
        client.get().uri("/api/post/getAllPosts").exchange().expectStatus().isOk().expectBody().json("[]");
        POST.verify(getRequestedFor(urlPathEqualTo("/api/post/getAllPosts")));
        AUTH.verify(0, getRequestedFor(urlPathEqualTo("/api/post/getAllPosts")));
    }

    /**
     * Verifies that /api/comment traffic reaches the comment upstream and that the
     * upstream status and body are propagated unchanged. This prevents the gateway from
     * masking backend errors or routing comment requests to another service.
     */
    @Test
    void routesCommentPathAndPropagatesStatus() {
        COMMENT.stubFor(get(urlPathEqualTo("/api/comment/getComments/7"))
                .willReturn(aResponse().withStatus(418).withBody("comment-upstream")));
        client.get().uri("/api/comment/getComments/7").exchange().expectStatus().isEqualTo(418)
                .expectBody(String.class).isEqualTo("comment-upstream");
        COMMENT.verify(getRequestedFor(urlPathEqualTo("/api/comment/getComments/7")));
    }

    /**
     * Verifies that a path outside the configured API routes returns 404. This protects
     * the gateway from unintentionally exposing a catch-all route to backend services.
     */
    @Test
    void unknownPathIsNotRouted() {
        client.get().uri("/not-an-api").exchange().expectStatus().isNotFound();
    }

    private static WireMockServer server() {
        return new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }
}
