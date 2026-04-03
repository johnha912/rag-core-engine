package com.ragcore.adapter;

/**
 * Utility class that cleans raw text extracted from PDF or other documents. Removes noise such as
 * headers, footers, extra whitespace, and special characters that would degrade embedding quality.
 */
public class TextCleaner {

  /**
   * Cleans the given raw text by removing common PDF noise.
   *
   * <p>Operations performed in order:
   * <ol>
   *   <li>Strip common header/footer patterns (page numbers, "Page X of Y")</li>
   *   <li>Remove non-printable / control characters</li>
   *   <li>Collapse multiple blank lines into a single newline</li>
   *   <li>Collapse multiple spaces into a single space</li>
   *   <li>Trim leading and trailing whitespace</li>
   * </ol>
   *
   * @param rawText the raw extracted text
   * @return the cleaned text, or an empty string if input is null
   */
  public String clean(String rawText) {
    if (rawText == null) {
      return "";
    }

    String text = rawText;

    // Remove common header/footer patterns like "Page 1 of 10", "- 3 -", standalone page numbers
    text = text.replaceAll("(?im)^\\s*page\\s+\\d+\\s*(of\\s+\\d+)?\\s*$", "");
    text = text.replaceAll("(?m)^\\s*-\\s*\\d+\\s*-\\s*$", "");
    text = text.replaceAll("(?m)^\\s*\\d+\\s*$", "");

    // Remove non-printable characters (keep newlines, tabs, and standard printable ASCII + Unicode letters)
    text = text.replaceAll("[^\\p{Print}\\n\\t]", "");

    // Collapse 3+ consecutive newlines into 2 (one blank line)
    text = text.replaceAll("(\\n\\s*){3,}", "\n\n");

    // Collapse multiple spaces/tabs into single space
    text = text.replaceAll("[ \\t]{2,}", " ");

    // Trim each line
    text = text.lines()
        .map(String::strip)
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    return text.strip();
  }
}