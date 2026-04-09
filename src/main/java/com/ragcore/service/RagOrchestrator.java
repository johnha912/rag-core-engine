package com.ragcore.service;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
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

  private final AtomicBoolean indexing = new AtomicBoolean(false);
  private final AtomicInteger chunkCount = new AtomicInteger(0);

  @Autowired
  public RagOrchestrator(List<DocumentAdapter> adapters,
                         VectorStore vectorStore,
                         ChatService chatService) {
    this.adapters = adapters;
    this.vectorStore = vectorStore;
    this.chatService = chatService;
  }

  /**
   * Indexes an uploaded file through the full pipeline: parse → embed → store.
   *
   * @param file the uploaded file from the HTTP request
   * @throws IllegalArgumentException if no adapter supports the file type
   * @throws Exception if parsing or storing fails
   */
  public void index(MultipartFile file, String domain) throws Exception {
    indexing.set(true);
    try {
      String fileName = file.getOriginalFilename();

      DocumentAdapter adapter;

      if (domain == null || domain.isBlank() || domain.equals("general")) {
        // general 模式：用原来的 supports() 自动选择
        adapter = adapters.stream()
            .filter(a -> a.supports(fileName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No adapter found for file: " + fileName));
      } else {
        // 指定 domain：按 getDomainName() 精准匹配
        adapter = adapters.stream()
            .filter(a -> a.getDomainName().equalsIgnoreCase(domain.replace("_", " ")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No adapter found for domain: " + domain));
      }

      List<Chunk> chunks = adapter.parse(file.getInputStream(), fileName);
      vectorStore.store(chunks);
      chunkCount.addAndGet(chunks.size());

    } finally {
      indexing.set(false);
    }
  }

  /**
   * Answers a question using the full RAG pipeline.
   *
   * <p>Retrieves the top-5 most relevant chunks from the vector store and passes them
   * as context to {@link ChatService} to generate a grounded answer. Returns a fallback
   * message if no documents have been indexed yet.</p>
   *
   * @param question the user's question
   * @return the AI-generated answer with source citations, or a fallback message
   */
  public String query(String question) {
    List<Chunk> relevant = vectorStore.search(question, 10);

    if (relevant.isEmpty()) {
      return "No relevant content found in the indexed documents. "
          + "Please upload a document first.";
    }

    try {
      ChatService.ChatResponse response = chatService.ask(question, relevant);

      StringBuilder result = new StringBuilder(response.getAnswer());
      if (!response.getSources().isEmpty()) {
        result.append("\n\nSources: ").append(String.join(", ", response.getSources()));
      }
      return result.toString();

    } catch (Exception e) {
      throw new RuntimeException("Failed to generate answer: " + e.getMessage(), e);
    }
  }

  /** Returns {@code true} if a file is currently being indexed. */
  public boolean isIndexing() {
    return indexing.get();
  }

  /** Returns the total number of chunks indexed so far. */
  public int getChunkCount() {
    return chunkCount.get();
  }
}
