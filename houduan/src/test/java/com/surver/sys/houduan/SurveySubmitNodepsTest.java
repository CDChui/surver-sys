package com.surver.sys.houduan;

import com.fasterxml.jackson.databind.JsonNode;
import com.surver.sys.houduan.support.NodepsApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SurveySubmitNodepsTest extends NodepsApiTestSupport {

    @Test
    void submitSameSurveyTwice_shouldReturn40009OnSecondSubmit() throws Exception {
        String adminToken = loginAdminToken();

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", "q1");
        question.put("type", "single");
        question.put("title", "Q1");
        question.put("required", true);
        question.put("options", List.of(
                Map.of("label", "A"),
                Map.of("label", "B")
        ));

        Map<String, Object> createBody = new LinkedHashMap<>();
        createBody.put("title", "nodeps-survey");
        createBody.put("description", "integration-test");
        createBody.put("questions", List.of(question));

        ResponseEntity<String> createResponse = post("/api/surveys", createBody, adminToken);
        JsonNode createRoot = readJson(createResponse);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createRoot.path("code").asInt()).isEqualTo(20000);
        long surveyId = createRoot.path("data").path("id").asLong();
        assertThat(surveyId).isGreaterThan(0L);

        String role1Token = loginRole1TokenByOauth();

        ResponseEntity<String> publicResponse = get("/api/surveys/" + surveyId + "/public", role1Token);
        JsonNode publicRoot = readJson(publicResponse);
        assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicRoot.path("code").asInt()).isEqualTo(20000);

        Map<String, Object> submitBody = new LinkedHashMap<>();
        submitBody.put("surveyId", surveyId);
        submitBody.put("answers", Map.of("q1", "A"));

        ResponseEntity<String> firstSubmit = post("/api/surveys/" + surveyId + "/responses", submitBody, role1Token);
        JsonNode firstRoot = readJson(firstSubmit);
        assertThat(firstSubmit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstRoot.path("code").asInt()).isEqualTo(20000);

        ResponseEntity<String> secondSubmit = post("/api/surveys/" + surveyId + "/responses", submitBody, role1Token);
        JsonNode secondRoot = readJson(secondSubmit);
        assertThat(secondSubmit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondRoot.path("code").asInt()).isEqualTo(40009);
    }
}
