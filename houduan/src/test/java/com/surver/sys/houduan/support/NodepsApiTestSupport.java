package com.surver.sys.houduan.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("nodeps")
public abstract class NodepsApiTestSupport {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, null, token);
    }

    protected ResponseEntity<String> post(String path, Object body, String token) {
        return exchange(path, HttpMethod.POST, body, token);
    }

    protected JsonNode readJson(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotBlank();
        return objectMapper.readTree(response.getBody());
    }

    protected String loginAdminToken() throws Exception {
        ResponseEntity<String> response = post("/api/auth/local/login",
                Map.of("username", "admin", "password", "123456"), null);
        JsonNode root = readJson(response);
        assertThat(root.path("code").asInt()).isEqualTo(20000);
        String token = root.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    protected String loginRole1TokenByOauth() throws Exception {
        ResponseEntity<String> response = post("/api/auth/oauth/callback",
                Map.of(
                        "providerId", "iam-default",
                        "code", "mock-code",
                        "state", "mock-state",
                        "redirectPath", "/mobile/home"
                ),
                null
        );
        JsonNode root = readJson(response);
        assertThat(root.path("code").asInt()).isEqualTo(20000);
        assertThat(root.path("data").path("role").asText()).isEqualTo("ROLE1");
        String token = root.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, method, entity, String.class);
    }
}
