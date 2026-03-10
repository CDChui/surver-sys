package com.surver.sys.houduan;

import com.fasterxml.jackson.databind.JsonNode;
import com.surver.sys.houduan.support.NodepsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiNodepsTest extends NodepsApiTestSupport {

    @Test
    void localLogin_shouldReturnTokenAndRole() throws Exception {
        ResponseEntity<String> response = post("/api/auth/local/login",
                Map.of("username", "admin", "password", "123456"), null);
        JsonNode root = readJson(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(root.path("code").asInt()).isEqualTo(20000);
        assertThat(root.path("data").path("token").asText()).isNotBlank();
        assertThat(root.path("data").path("role").asText()).isEqualTo("ROLE3");
    }

    @Test
    void localLogin_withWrongPassword_shouldReturn40001() throws Exception {
        ResponseEntity<String> response = post("/api/auth/local/login",
                Map.of("username", "admin", "password", "wrong-pass"), null);
        JsonNode root = readJson(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(root.path("code").asInt()).isEqualTo(40001);
    }
}
