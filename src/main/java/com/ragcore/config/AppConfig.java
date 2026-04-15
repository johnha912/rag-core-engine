package com.ragcore.config;

import com.ragcore.service.ChatService;
import com.ragcore.service.LlmReranker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for beans that cannot be auto-detected by component scanning.
 *
 * <p>{@link ChatService} requires an API key passed at construction time, so Spring
 * cannot instantiate it automatically. This class provides an explicit factory method
 * that reads the key from the environment and constructs the bean.</p>
 */
@Configuration
public class AppConfig {

  /**
   * Creates the {@link ChatService} bean using the {@code OPENAI_API_KEY} environment variable.
   *
   * @return a configured {@link ChatService} instance
   * @throws IllegalStateException if {@code OPENAI_API_KEY} is not set
   */
  @Bean
  public ChatService chatService() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set.");
    }
    return new ChatService(apiKey);
  }

  @Bean
  public LlmReranker reranker() {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY environment variable is not set.");
    }
    return new LlmReranker(apiKey);
  }
}
