package com.ragcore.controller;

import com.ragcore.model.EvalQuestion;
import com.ragcore.model.EvalReport;
import com.ragcore.model.EvalResult;
import com.ragcore.service.EvalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EvalController.class)
class EvalControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private EvalService evalService;

  @Test
  void runEvaluation_default_returnsReport() throws Exception {
    EvalQuestion q = new EvalQuestion("q1", "Q?", "A", List.of(), List.of());
    EvalResult r = new EvalResult("q1", "Q?", "A", 1.0, 1.0,
        List.of(), List.of(), List.of(), List.of());
    EvalReport report = new EvalReport(1, 1.0, 1.0, List.of(r));

    when(evalService.loadQuestions("eval-questions.json")).thenReturn(List.of(q));
    when(evalService.evaluate(List.of(q))).thenReturn(report);

    mockMvc.perform(post("/api/eval/run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalQuestions").value(1))
        .andExpect(jsonPath("$.averageRetrievalHitRate").value(1.0))
        .andExpect(jsonPath("$.averageAnswerKeywordRate").value(1.0));
  }

  @Test
  void runEvaluation_invalidTestSet_returnsBadRequest() throws Exception {
    when(evalService.loadQuestions("missing.json"))
        .thenThrow(new IllegalArgumentException("Resource not found: missing.json"));

    mockMvc.perform(post("/api/eval/run")
            .param("testSet", "missing.json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("Resource not found: missing.json"));
  }

  @Test
  void listQuestions_returnsQuestionList() throws Exception {
    EvalQuestion q1 = new EvalQuestion("q1", "What?", "A", List.of("k"), List.of("s"));
    EvalQuestion q2 = new EvalQuestion("q2", "How?", "B", List.of(), List.of());

    when(evalService.loadQuestions("eval-questions.json")).thenReturn(List.of(q1, q2));

    mockMvc.perform(get("/api/eval/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value("q1"))
        .andExpect(jsonPath("$[1].id").value("q2"));
  }
}