package com.ragcore.adapter.domain.legal;

import org.springframework.stereotype.Component;

/**
 * Cleans raw text extracted from legal documents such as California rental law files.
 * Removes page noise while preserving section structure and numbering.
 */
@Component
public class LegalTextCleaner {

  /**
   * Cleans raw legal document text by removing common noise patterns.
   *
   * <p>Operations performed in order:
   * <ol>
   *   <li>Remove standalone page numbers and "Page X of Y" footers</li>
   *   <li>Remove dash-wrapped page numbers (e.g., "- 3 -")</li>
   *   <li>Normalize extra whitespace within section/article markers</li>
   *   <li>Remove non-printable control characters</li>
   *   <li>Collapse 3+ consecutive blank lines into one</li>
   *   <li>Collapse multiple spaces/tabs into a single space</li>
   *   <li>Strip leading/trailing whitespace from each line</li>
   * </ol>
   *
   * @param rawText the raw extracted text
   * @return the cleaned text, or empty string if input is null
   */
  public String clean(String rawText) {
    if (rawText == null) return "";

    String text = rawText;

    // Remove "Page X of Y", "Page X", and standalone page numbers
    text = text.replaceAll("(?im)^\\s*page\\s+\\d+\\s*(of\\s+\\d+)?\\s*$", "");
    text = text.replaceAll("(?m)^\\s*\\d+\\s*$", "");
    text = text.replaceAll("(?m)^\\s*-\\s*\\d+\\s*-\\s*$", "");

    // Normalize extra whitespace inside section/article/§ markers
    // e.g., "Section  1." -> "Section 1.", "§  1940" -> "§ 1940"
    text = text.replaceAll("(?i)(Section|Article|Sec\\.|§)\\s{2,}", "$1 ");

    // Remove control characters while preserving all printable Unicode (including §, ©, etc.)
    text = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t\\r]]", "");

    // Collapse 3+ consecutive newlines into 2
    text = text.replaceAll("(\\n\\s*){3,}", "\n\n");

    // Collapse multiple spaces/tabs into a single space
    text = text.replaceAll("[ \\t]{2,}", " ");

    // Trim each line
    text = text.lines()
        .map(String::strip)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    return text.strip();
  }
}
