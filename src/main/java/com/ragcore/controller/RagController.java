package com.ragcore.controller;

import com.ragcore.service.RagOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

/**
 * REST controller for the RAG Core Engine.
 * Exposes endpoints for file upload, querying, status checking, and SSE streaming.
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

    new Thread(() -> {
      try {
        List<com.ragcore.model.Chunk> relevant = orchestrator.searchOnly(question);

        if (relevant.isEmpty()) {
          // 与 token 格式保持一致，前端 JSON.parse() 才不会报错
          emitter.send(SseEmitter.event()
              .data("\"No relevant content found. Please upload a document first.\""));
          emitter.send(SseEmitter.event().data("[DONE]"));
          emitter.complete();
          return;
        }

        orchestrator.getChatService().askStream(question, relevant, token -> {
          try {
            // JSON 包一层，防止 SSE 协议剥离 token 前导空格
            emitter.send(SseEmitter.event()
                .data("\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\""));
          } catch (Exception e) {
            emitter.completeWithError(e);
          }
        });

        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();

      } catch (Exception e) {
        emitter.completeWithError(e);
      }
    }).start();

    return emitter;
  }

  // ─── DELETE /api/reset ──────────────────────────────────────────
  @DeleteMapping("/reset")
  public ResponseEntity<String> reset() {
    orchestrator.reset();
    return ResponseEntity.ok("Vector store cleared. Ready for new documents.");
  }
}