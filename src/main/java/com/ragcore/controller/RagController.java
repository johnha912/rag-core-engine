package com.ragcore.controller;

import com.ragcore.service.RagOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for the RAG Core Engine.
 * Exposes endpoints for file upload, querying, and status checking.
 */
@RestController
@RequestMapping("/api")
public class RagController {

  private final RagOrchestrator orchestrator;

  @Autowired
  public RagController(RagOrchestrator orchestrator) {
    this.orchestrator = orchestrator;
  }

  // ─── POST /api/upload ───────────────────────────────────────────
  @PostMapping("/upload")
  public ResponseEntity<String> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "domain", defaultValue = "general") String domain) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("No file provided.");
    }
    try {
      orchestrator.index(file, domain);
      return ResponseEntity.ok("File uploaded and indexed: "
          + file.getOriginalFilename()
          + " (domain: " + domain + ")");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body("Failed to index file: " + e.getMessage());
    }
  }

  // ─── POST /api/query ────────────────────────────────────────────
  @PostMapping("/query")
  public ResponseEntity<String> query(@RequestParam("question") String question) {
    if (question == null || question.isBlank()) {
      return ResponseEntity.badRequest().body("Question must not be empty.");
    }
    String answer = orchestrator.query(question);
    return ResponseEntity.ok(answer);
  }

  // ─── GET /api/status ────────────────────────────────────────────
  @GetMapping("/status")
  public ResponseEntity<String> status() {
    String status = "Indexing: " + orchestrator.isIndexing()
        + " | Chunks stored: " + orchestrator.getChunkCount();
    return ResponseEntity.ok(status);
  }

  // ─── DELETE /api/reset ──────────────────────────────────────────
  @DeleteMapping("/reset")
  public ResponseEntity<String> reset() {
    orchestrator.reset();
    return ResponseEntity.ok("Vector store cleared. Ready for new documents.");
  }
}