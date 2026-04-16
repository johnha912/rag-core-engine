package com.ragcore.adapter.domain.film;

import org.springframework.stereotype.Component;

/**
 * Cleans raw text extracted from screenplay files.
 * Removes screenplay-specific noise while preserving scene structure.
 */
@Component
public class ScriptTextCleaner {

  /**
   * Cleans raw screenplay text by removing common noise patterns.
   *
   * @param rawText the raw extracted text
   * @return the cleaned text, or empty string if input is null
   */
  public String clean(String rawText) {
    if (rawText == null) return "";

    String text = rawText;

    // Remove "CONTINUED:" and "(CONTINUED)" markers
    text = text.replaceAll("(?im)^\\s*(CONTINUED:|\\(CONTINUED\\))\\s*$", "");

    // Remove standalone page numbers
    text = text.replaceAll("(?m)^\\s*\\d+\\.?\\s*$", "");

    // Remove "MORE" continuation markers
    text = text.replaceAll("(?im)^\\s*\\(MORE\\)\\s*$", "");

    // Collapse 3+ consecutive newlines into 2
    text = text.replaceAll("(\\n\\s*){3,}", "\n\n");

    // Collapse multiple spaces into one
    text = text.replaceAll("[ \\t]{2,}", " ");

    // Trim each line
    text = text.lines()
        .map(String::strip)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    return text.strip();
  }
}