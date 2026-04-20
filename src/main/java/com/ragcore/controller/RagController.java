package com.ragcore.controller;

import com.google.gson.Gson;
import com.ragcore.service.RagOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * REST controller for the RAG Core Engine.
 * Exposes endpoints for file upload, querying, status checking, and SSE streaming.
 */
@RestController
@RequestMapping("/api")
public class RagController {

  private final RagOrchestrator orchestrator;
  private final Executor sseExecutor;
  private final Gson gson = new Gson();

  public RagController(RagOrchestrator orchestrator,
                       @Qualifier("sseExecutor") Executor sseExecutor) {
    this.orchestrator = orchestrator;
    this.sseExecutor  = sseExecutor;
  }

  // ─── POST /api/upload ───────────────────────────────────────────
  //
  // Files are additive: each upload appends to the existing vector store.
  // To start fresh before uploading, call DELETE /api/reset first.
  //
  // Example — index two files for cross-document search:
  //   POST /api/upload?domain=film+script   (file: screenplay.pdf)
  //   POST /api/upload?domain=film+script   (file: subtitles.srt)
  //   POST /api/query   { question: "..." }   ← searches across both files
  //
  // Example — switch to a new corpus:
  //   DELETE /api/reset
  //   POST /api/upload?domain=rental+law    (file: lease.pdf)
  @PostMapping("/upload")
  public ResponseEntity<String> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "domain", defaultValue = "general") String domain) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("No file provided.");
    }
    try {
      orchestrator.index(file, domain);
      return ResponseEntity.ok(
          "Indexed: " + file.getOriginalFilename()
          + " | domain: " + domain
          + " | total chunks in store: " + orchestrator.getChunkCount()
          + " | to clear all documents call DELETE /api/reset");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body("Failed to index file: " + e.getMessage());
    }
  }

  // ─── POST /api/query ────────────────────────────────────────────
  @PostMapping("/query")
  public ResponseEntity<String> query(
      @RequestParam("question") String question,
      @RequestParam(value = "conversationId", required = false) String conversationId) {

    if (question == null || question.isBlank()) {
      return ResponseEntity.badRequest().body("Question must not be empty.");
    }
    String answer = orchestrator.query(question, conversationId);
    return ResponseEntity.ok(answer);
  }

  // ─── DELETE /api/conversation/{id} ─────────────────────────────
  @DeleteMapping("/conversation/{id}")
  public ResponseEntity<String> clearConversation(@PathVariable("id") String id) {
    orchestrator.clearConversation(id);
    return ResponseEntity.ok("Conversation '" + id + "' cleared.");
  }

  // ─── GET /api/status ────────────────────────────────────────────
  @GetMapping("/status")
  public ResponseEntity<String> status() {
    String status = "Indexing: " + orchestrator.isIndexing()
        + " | Chunks stored: " + orchestrator.getChunkCount();
    return ResponseEntity.ok(status);
  }

  // ─── GET /api/stream ────────────────────────────────────────────
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@RequestParam("question") String question) {
    SseEmitter emitter = new SseEmitter(60_000L);

    if (question == null || question.isBlank()) {
      emitter.completeWithError(new IllegalArgumentException("Question must not be empty."));
      return emitter;
    }

    sseExecutor.execute(() -> {
      try {
        orchestrator.queryStream(question, token -> {
          try {
            if (token.equals("[NO_CONTENT]")) {
              emitter.send(SseEmitter.event()
                  .data("\"No relevant content found. Please upload a document first.\""));
            } else {
              emitter.send(SseEmitter.event()
                  .data(gson.toJson(token)));
            }
          } catch (Exception e) {
            emitter.completeWithError(e);
          }
        });
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();

      } catch (Exception e) {
        emitter.completeWithError(e);
      }
    });

    return emitter;
  }

  // ─── DELETE /api/reset ──────────────────────────────────────────
  @DeleteMapping("/reset")
  public ResponseEntity<String> reset() {
    orchestrator.reset();
    return ResponseEntity.ok("Vector store cleared. Ready for new documents.");
  }
}