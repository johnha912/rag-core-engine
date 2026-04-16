package com.ragcore.config;

import com.ragcore.service.ChatService;
import com.ragcore.service.rerank.LlmReranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

  @Value("${openai.chat.model:gpt-4o-mini}")
  private String chatModel;

  private String getApiKey() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set.");
    }
    return apiKey;
  }

  @Bean
  public ChatService chatService() {
    return new ChatService(getApiKey(), chatModel);
  }

  @Bean
  public LlmReranker reranker() {
    return new LlmReranker(getApiKey());
  }

  /**
   * Executor for SSE streaming tasks.
   *
   * <p>Replaces bare {@code new Thread()} in {@link com.ragcore.controller.RagController}.
   * Bounded pool (max 20 threads) prevents thread exhaustion under load.
   * {@code setWaitForTasksToCompleteOnShutdown(true)} ensures in-flight streams
   * are drained gracefully when the application stops.</p>
   */
  @Bean(name = "sseExecutor")
  public Executor sseExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("sse-stream-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}