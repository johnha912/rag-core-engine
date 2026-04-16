package com.ragcore.config;

import com.ragcore.service.ChatService;
import com.ragcore.service.rerank.LlmReranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}