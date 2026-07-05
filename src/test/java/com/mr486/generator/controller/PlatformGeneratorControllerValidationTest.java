package com.mr486.generator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Vérifie que l'endpoint {@code POST /api/generate/platform} rejette les requêtes invalides
 * avec un statut {@code 400} grâce à {@code @Valid}, avant tout traitement du pipeline.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformGeneratorControllerValidationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void accepts_empty_body_with_200() throws Exception {
        mockMvc.perform(post("/api/generate/platform")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
    }

    @Test
    void rejects_service_name_path_traversal_with_400() throws Exception {
        String body = "{\"resources\":[{\"serviceName\":\"../../evil\",\"className\":\"Order\"}]}";
        mockMvc.perform(post("/api/generate/platform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejects_name_with_crlf_with_400() throws Exception {
        String body = "{\"name\":\"a\\r\\nSet-Cookie: x=y\"}";
        mockMvc.perform(post("/api/generate/platform")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }
}
