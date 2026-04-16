package com.ragcore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter that validates the {@code X-API-Key} request header.
 *
 * <h3>Behaviour</h3>
 * <ul>
 *   <li><b>Security disabled</b> (default / dev mode): if {@code api.security.key} is blank,
 *       every request passes through without any check. This makes local development
 *       friction-free without requiring environment setup.</li>
 *   <li><b>Security enabled</b>: when {@code RAG_API_KEY} is set, every request to
 *       {@code /api/**} must include the header {@code X-API-Key: <key>}.
 *       Requests without it — or with the wrong value — receive {@code 401 Unauthorized}.</li>
 *   <li><b>Health endpoint exempt</b>: {@code /actuator/health} is always allowed so
 *       load-balancer health checks work without credentials.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * # Enable:
 * export RAG_API_KEY=my-secret-key
 * mvn spring-boot:run
 *
 * # Call with key:
 * curl -H "X-API-Key: my-secret-key" http://localhost:8080/api/status
 *
 * # Missing key → 401
 * curl http://localhost:8080/api/status
 * }</pre>
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  private static final String API_KEY_HEADER = "X-API-Key";

  @Value("${api.security.key:}")
  private String configuredKey;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain chain)
      throws ServletException, IOException {

    // Security disabled in dev mode — skip all checks.
    if (configuredKey == null || configuredKey.isBlank()) {
      chain.doFilter(request, response);
      return;
    }

    // Health endpoint is always public.
    String path = request.getRequestURI();
    if (path.startsWith("/actuator/")) {
      chain.doFilter(request, response);
      return;
    }

    // Validate the key.
    String providedKey = request.getHeader(API_KEY_HEADER);
    if (configuredKey.equals(providedKey)) {
      chain.doFilter(request, response);
    } else {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.getWriter().write(
          "{\"error\": \"Unauthorized\", "
          + "\"message\": \"Missing or invalid X-API-Key header.\"}");
    }
  }
}
