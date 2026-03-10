package com.surver.sys.houduan;

import com.fasterxml.jackson.databind.JsonNode;
import com.surver.sys.houduan.support.NodepsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApiNodepsTest extends NodepsApiTestSupport {

    @Test
    void settings_withoutToken_shouldReturn40101() throws Exception {
        ResponseEntity<String> response = get("/api/settings", null);
        JsonNode root = readJson(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(root.path("code").asInt()).isEqualTo(40101);
    }

    @Test
    void settings_withRole1Token_shouldReturn40301() throws Exception {
        String role1Token = loginRole1TokenByOauth();
        ResponseEntity<String> response = get("/api/settings", role1Token);
        JsonNode root = readJson(response);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(root.path("code").asInt()).isEqualTo(40301);
    }
}
