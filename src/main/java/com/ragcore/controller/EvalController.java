package com.ragcore.controller;

import com.ragcore.model.EvalQuestion;
import com.ragcore.model.EvalReport;
import com.ragcore.service.EvalService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for running RAG quality evaluations.
 *
 * <p>Provides endpoints to trigger an evaluation run using a pre-built test set
 * and return detailed metrics on retrieval hit rate and answer accuracy.</p>
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

  private static final String DEFAULT_TEST_SET = "eval-questions.json";

  private final EvalService evalService;

  @Autowired
  public EvalController(EvalService evalService) {
    this.evalService = evalService;
  }

  /**
   * Runs the evaluation pipeline against the default or specified test set.
   *
   * <p>Documents must be uploaded and indexed before running evaluation.
   * The endpoint loads Q&A pairs from the classpath resource, runs each question
   * through the RAG pipeline, and returns an aggregated report.</p>
   *
   * @param testSet optional classpath resource name (default: "eval-questions.json")
   * @return JSON evaluation report with per-question detail and aggregate metrics
   */
  @PostMapping("/run")
  public ResponseEntity<?> runEvaluation(
      @RequestParam(value = "testSet", defaultValue = DEFAULT_TEST_SET) String testSet) {
    try {
      List<EvalQuestion> questions = evalService.loadQuestions(testSet);
      EvalReport report = evalService.evaluate(questions);
      return ResponseEntity.ok(report);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body("Evaluation failed: " + e.getMessage());
    }
  }

  /**
   * Returns a summary of the default test set (number of questions, IDs).
   */
  @GetMapping("/questions")
  public ResponseEntity<?> listQuestions(
      @RequestParam(value = "testSet", defaultValue = DEFAULT_TEST_SET) String testSet) {
    try {
      List<EvalQuestion> questions = evalService.loadQuestions(testSet);
      return ResponseEntity.ok(questions);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}