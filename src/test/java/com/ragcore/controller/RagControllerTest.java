package com.ragcore.controller;

import com.ragcore.service.RagOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagController.class)
class RagControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private RagOrchestrator orchestrator;

  @Test
  void statusEndpoint_returnsOk() throws Exception {
    when(orchestrator.isIndexing()).thenReturn(false);
    when(orchestrator.getChunkCount()).thenReturn(0);

    mockMvc.perform(get("/api/status"))
        .andExpect(status().isOk())
        .andExpect(content().string("Indexing: false | Chunks stored: 0"));
  }

  @Test
  void queryEndpoint_validQuestion_returnsOk() throws Exception {
    when(orchestrator.query("What is RAG?")).thenReturn("Mock answer");

    mockMvc.perform(post("/api/query")
            .param("question", "What is RAG?"))
        .andExpect(status().isOk())
        .andExpect(content().string("Mock answer"));
  }

  @Test
  void queryEndpoint_emptyQuestion_returnsBadRequest() throws Exception {
    mockMvc.perform(post("/api/query")
            .param("question", ""))
        .andExpect(status().isBadRequest());
  }

  @Test
  void uploadEndpoint_validFile_defaultDomain_returnsOk() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "test.txt", "text/plain", "hello world".getBytes()
    );

    mockMvc.perform(multipart("/api/upload").file(file))
        .andExpect(status().isOk())
        .andExpect(content().string(
            "File uploaded and indexed: test.txt (domain: general)"));
  }

  @Test
  void uploadEndpoint_withDomain_returnsOk() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file", "contract.pdf", "application/pdf", "legal".getBytes()
    );

    mockMvc.perform(multipart("/api/upload")
            .file(file)
            .param("domain", "rental_law"))
        .andExpect(status().isOk())
        .andExpect(content().string(
            "File uploaded and indexed: contract.pdf (domain: rental_law)"));
  }

  @Test
  void uploadEndpoint_emptyFile_returnsBadRequest() throws Exception {
    MockMultipartFile emptyFile = new MockMultipartFile(
        "file", "empty.txt", "text/plain", new byte[0]
    );

    mockMvc.perform(multipart("/api/upload").file(emptyFile))
        .andExpect(status().isBadRequest());
  }
}