package com.example.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ReportControllerTest {
    private static final String AGENT = "agentTokenThisIsLongEnough1234";
    private static final String VIEWER = "viewerTokenThisIsLongEnough123";
    private MockMvc api;

    @BeforeEach void setUp() {
        api = standaloneSetup(new ReportController(AGENT, VIEWER)).build();
    }

    @Test void healthDoesNotExposeReports() throws Exception {
        api.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }

    @Test void rejectsUnauthorizedReadsAndWrites() throws Exception {
        api.perform(get("/api/reports")).andExpect(status().isUnauthorized());
        api.perform(post("/api/report").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        api.perform(get("/api/reports").header("X-Viewer-Token", AGENT))
                .andExpect(status().isUnauthorized());
    }

    @Test void acceptsReportListsItAndAllowsAuthorizedDeletion() throws Exception {
        api.perform(post("/api/report").header("X-Agent-Token", AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"phone-A\",\"recordedAt\":1000,\"apps\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("received"));
        api.perform(get("/api/reports").header("X-Viewer-Token", VIEWER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['phone-A']").exists());
        api.perform(delete("/api/reports/phone-A").header("X-Viewer-Token", VIEWER))
                .andExpect(status().isNoContent());
        api.perform(get("/api/reports").header("X-Viewer-Token", VIEWER))
                .andExpect(content().json("{}"));
    }

    @Test void validatesReportAndProtectsDeletion() throws Exception {
        api.perform(post("/api/report").header("X-Agent-Token", AGENT)
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/report").header("X-Agent-Token", AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"bad/id\",\"recordedAt\":1000,\"apps\":[]}"))
                .andExpect(status().isBadRequest());
        api.perform(delete("/api/reports/phone-A").header("X-Viewer-Token", AGENT))
                .andExpect(status().isUnauthorized());
    }
}
