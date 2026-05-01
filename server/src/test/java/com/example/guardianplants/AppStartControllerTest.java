package com.example.guardianplants;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.example.guardianplants.service.LogRetentionService;
import com.example.guardianplants.config.RateLimitConfig;

@WebMvcTest(AppStartController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppStartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private LogRetentionService logRetentionService;

    @MockBean
    private RateLimitConfig rateLimitConfig;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    void appStartStoresLog() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(), any(), any(), any()))
            .thenReturn(123L);

        mockMvc.perform(post("/api/app-start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "deviceId": "android-test",
                      "appVersion": "1.0.0",
                      "details": {"source": "test"}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("stored")))
            .andExpect(jsonPath("$.id", is(123)));
    }
}
