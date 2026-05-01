package com.example.guardianplants.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.guardianplants.service.RequestTraceService;
import com.example.guardianplants.service.TtsHealthService;
import com.example.guardianplants.service.TtsService;
import com.example.guardianplants.config.RateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TtsController.class)
@AutoConfigureMockMvc(addFilters = false)
class TtsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TtsService ttsService;

    @MockBean
    private TtsHealthService ttsHealthService;

    @MockBean
    private RequestTraceService traceService;

    @MockBean
    private RateLimitConfig rateLimitConfig;

    @Test
    void synthesizeRejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/tts/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\",\"speaker\":3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("text is required")));
    }

    @Test
    void synthesizeReturnsWav() throws Exception {
        when(traceService.generateTraceId()).thenReturn("trace-test");
        when(ttsService.synthesize(anyString(), anyInt())).thenReturn(new byte[] {1, 2, 3});

        mockMvc.perform(post("/api/tts/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"speaker\":3}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/wav"))
            .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void synthesizeReturnsAacWhenRequested() throws Exception {
        when(traceService.generateTraceId()).thenReturn("trace-test");
        when(ttsService.synthesize(anyString(), anyInt())).thenReturn(new byte[] {1, 2, 3});
        when(ttsService.encodeAac(new byte[] {1, 2, 3}))
            .thenReturn(new TtsService.EncodedAudio(new byte[] {4, 5}, "audio/mp4", "m4a", "aac"));

        mockMvc.perform(post("/api/tts/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"speaker\":3,\"format\":\"aac\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mp4"))
            .andExpect(header().string("X-Audio-Format", "aac"))
            .andExpect(header().string("X-Audio-Extension", "m4a"))
            .andExpect(content().bytes(new byte[] {4, 5}));
    }

    @Test
    void synthesizeRejectsInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/tts/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"speaker\":3,\"format\":\"mp3\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("format must be wav or aac")));
    }

    @Test
    void synthesizeReturnsJsonWhenVoiceVoxFails() throws Exception {
        when(traceService.generateTraceId()).thenReturn("trace-test");
        when(ttsService.synthesize(anyString(), anyInt()))
            .thenThrow(new RuntimeException("VoiceVOX synthesis failed: buffer limit"));

        mockMvc.perform(post("/api/tts/synthesize")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\",\"speaker\":3}"))
            .andExpect(status().isBadGateway())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.error", is("VoiceVOX unavailable: VoiceVOX synthesis failed: buffer limit")));

        verify(traceService).recordError(
            "trace-test",
            "tts",
            "voicevox_synthesis",
            "VoiceVOX synthesis failed: buffer limit"
        );
    }

    @Test
    void healthReturnsVoiceVoxStatus() throws Exception {
        when(ttsHealthService.checkVoiceVoxHealth()).thenReturn(true);

        mockMvc.perform(get("/api/tts/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voicevox", is("ok")))
            .andExpect(jsonPath("$.voicevoxHealthy", is(true)));
    }
}
