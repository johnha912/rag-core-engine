package com.ragcore.service;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
import com.ragcore.service.rerank.Reranker;
import com.ragcore.service.vector.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central coordinator of the RAG pipeline.
 *
 * <p>Connects all major components:</p>
 * <ul>
 *   <li><b>Index flow:</b> upload &rarr; DocumentAdapter (parse) &rarr; VectorStore (embed + store)</li>
 *   <li><b>Query flow:</b> question &rarr; VectorStore (search) &rarr; ChatService (generate) &rarr; answer</li>
 * </ul>
 *
 * <p>{@link AtomicBoolean} and {@link AtomicInteger} are used so that status reads from
 * concurrent threads always see the latest values without explicit synchronization.</p>
 */
@Service
public class RagOrchestrator {

  private final List<DocumentAdapter> adapters;
  private final VectorStore vectorStore;
  private final ChatService chatService;
  private final Reranker reranker;

  private final AtomicBoolean indexing = new AtomicBoolean(false);
  private final AtomicInteger chunkCount = new AtomicInteger(0);

  /**
   * Tracks the domain of the most recently indexed document so that
   * {@link #query} can select the appropriate system prompt without
   * requiring callers to pass the domain on every request.
   *
   * <p>Volatile ensures visibility across threads without heavier synchronization;
   * single-writer (only {@link #index} mutates it) so volatile is sufficient.</p>
   */
  private volatile String currentDomain = "general";

  @Autowired
  public RagOrchestrator(List<DocumentAdapter> adapters,
                         VectorStore vectorStore,
                         ChatService chatService,
                         Reranker reranker) {
    this.adapters = adapters;
    this.vectorStore = vectorStore;
    this.chatService = chatService;
    this.reranker = reranker;
  }

  /**
   * Indexes an uploaded file through the full pipeline: parse → embed → store.
   *
   * <p>Files are <em>additive</em>: each upload appends to the existing vector store
   * so multiple documents can be queried together. To start fresh (e.g. switching to
   * a completely unrelated corpus), call {@link #reset()} first or use DELETE /api/reset.</p>
   *
   * <p>{@code currentDomain} is updated to the most recently indexed domain and drives
   * the LLM role instruction at query time. If you mix domains without resetting,
   * the last uploaded domain's persona will apply to all queries.</p>
   *
   * @param file   the uploaded file from the HTTP request
   * @param domain the content domain (e.g. "film script", "rental law", "general")
   * @throws IllegalArgumentException if no adapter supports the file or domain
   * @throws Exception                if parsing or storing fails
   */
  public void index(MultipartFile file, String domain) throws Exception {
    indexing.set(true);
    try {
      String fileName = file.getOriginalFilename();

      DocumentAdapter adapter;

      if (domain == null || domain.isBlank() || domain.equals("general")) {
        adapter = adapters.stream()
            .filter(a -> a.supports(fileName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No adapter found for file: " + fileName));
      } else if (domain.equals("technical")) {
        // Technical domain: route by file extension
        adapter = adapters.stream()
            .filter(a -> a.supports(fileName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No adapter found for file: " + fileName));
      } else {
        adapter = adapters.stream()
            .filter(a -> a.getDomainName().equalsIgnoreCase(domain.replace("_", " ")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No adapter found for domain: " + domain));
      }

      List<Chunk> chunks = adapter.parse(file.getInputStream(), fileName);
      vectorStore.store(chunks);
      chunkCount.addAndGet(chunks.size());
      currentDomain = (domain == null || domain.isBlank()) ? "general" : domain;

    } finally {
      indexing.set(false);
    }
  }

  /**
   * Answers a question using the full RAG pipeline (stateless, no conversation history).
   *
   * @param question the user's question
   * @return the AI-generated answer with source citations, or a fallback message
   */
  public String query(String question) {
    return query(question, null);
  }

  /**
   * Answers a question using the full RAG pipeline with optional conversation history.
   *
   * <p>Retrieves the top-10 most relevant chunks from the vector store and passes them
   * as context to {@link ChatService} to generate a grounded answer. When a
   * {@code conversationId} is provided, prior messages from that session are included
   * so the model can resolve follow-up references like "tell me more about that".</p>
   *
   * @param question       the user's question
   * @param conversationId optional session ID for multi-turn conversations; null for stateless
   * @return the AI-generated answer with source citations, or a fallback message
   */
  public String query(String question, String conversationId) {
    List<Chunk> relevant = vectorStore.search(question, 10);

    if (relevant.isEmpty()) {
      return "No relevant content found in the indexed documents. "
          + "Please upload a document first.";
    }

    // Re-rank the top-10 candidates and keep the best 5 for the LLM prompt.
    List<Chunk> reranked = reranker.rerank(question, relevant);
    List<Chunk> top5 = reranked.subList(0, Math.min(5, reranked.size()));

    try {
      String roleInstruction = buildRoleInstruction(currentDomain);
      ChatService.ChatResponse response =
          chatService.ask(question, top5, conversationId, roleInstruction);

      StringBuilder result = new StringBuilder(response.getAnswer());
      if (!response.getSources().isEmpty()) {
        result.append("\n\nSources: ").append(String.join(", ", response.getSources()));
      }
      return result.toString();

    } catch (Exception e) {
      throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
    }
  }

  /**
   * Returns a domain-specific role instruction for the LLM system prompt.
   *
   * <p>Each domain gets a persona tailored to its content type.
   * Adding a new domain means adding one case here — no other class needs to change.</p>
   *
   * @param domain the domain string set during {@link #index}
   * @return a role instruction string; never null or blank
   */
  String buildRoleInstruction(String domain) {
    if (domain == null || domain.isBlank()) domain = "general";
    return switch (domain.toLowerCase()) {
      case "film script", "film" -> """
          You are an expert screenplay analyst with deep knowledge of screenwriting structure, \
          three-act format, scene headings, action lines, and dialogue conventions. \
          Answer the user's question based on the provided script excerpts. \
          Cite the scene or page when referencing specific content. \
          Structure your answer clearly.""";

      case "rental law", "legal" -> """
          You are an expert legal document assistant specialising in California tenant rights law, \
          including California Civil Code Section 1941 (implied warranty of habitability), \
          constructive eviction, and quiet enjoyment rights. \
          Answer based on the provided legal text. \
          Cite the relevant statute, section, and source page in your answer. \
          Structure advice with numbered steps.""";

      case "subtitle" -> """
          You are a media content analyst. \
          Answer the user's question based on the provided subtitle transcript. \
          When referencing dialogue, cite the timestamp or speaker if available.""";

      case "storyboard" -> """
          You are a visual storytelling expert. \
          Answer the user's question based on the provided storyboard descriptions. \
          Reference panel numbers or scene headings when available.""";

      case "technical" -> """
          You are a technical documentation assistant. \
          Answer the user's question based on the provided technical content. \
          Use precise terminology, cite the source section, and prefer bullet points for steps.""";

      default -> """
          You are a helpful document analysis assistant. \
          Answer the user's question based on the provided context. \
          If the context does not contain enough information, say so clearly. \
          Always cite which source and page your answer comes from. \
          Structure your answer clearly with numbered steps when giving advice.""";
    };
  }

  /**
   * Clears conversation history for the given session.
   *
   * @param conversationId the session to clear
   */
  public void clearConversation(String conversationId) {
    chatService.clearConversation(conversationId);
  }

  /** Returns {@code true} if a file is currently being indexed. */
  public boolean isIndexing() {
    return indexing.get();
  }

  /** Returns the total number of chunks indexed so far. */
  public int getChunkCount() {
    return chunkCount.get();
  }

  /**
   * Clears all stored chunks and resets the chunk count.
   * Use before uploading a new set of documents to avoid cross-domain confusion.
   */
  public void reset() {
    vectorStore.clear();
    chunkCount.set(0);
    currentDomain = "general";
  }

  public List<Chunk> searchOnly(String question) {
    return vectorStore.search(question, 10);
  }

  public void queryStream(String question, java.util.function.Consumer<String> tokenCallback) {
    List<Chunk> relevant = searchOnly(question);
    if (relevant.isEmpty()) {
      tokenCallback.accept("[NO_CONTENT]");
      return;
    }
    try {
      chatService.askStream(question, relevant, tokenCallback);
    } catch (Exception e) {
      tokenCallback.accept("[NO_CONTENT]");
    }
  }
}

