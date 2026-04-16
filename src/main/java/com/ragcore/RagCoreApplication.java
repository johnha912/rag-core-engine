package com.ragcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the RAG Core Engine application.
 *
 * <p>This class bootstraps the Spring Boot application, which automatically
 * scans for components, services, and controllers in the {@code com.ragcore}
 * package and its sub-packages.</p>
 */
@SpringBootApplication
@EnableAsync
public class RagCoreApplication {

  /**
   * Starts the RAG Core Engine Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(RagCoreApplication.class, args);
  }
}