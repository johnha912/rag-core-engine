package com.ragcore.adapter.domain.legal;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits legal document text into chunks by section or article headings.
 *
 * <p>Recognizes common California rental law section patterns:</p>
 * <ul>
 *   <li>{@code Section 1}, {@code Section 1.1}, {@code SECTION 1}</li>
 *   <li>{@code Article I}, {@code Article 1}, {@code ARTICLE IV}</li>
 *   <li>{@code § 1940}, {@code § 1940.5} (California Civil Code style)</li>
 *   <li>{@code 1. DEFINITIONS}, {@code 2. TERM OF TENANCY} (numbered + ALL-CAPS title)</li>
 * </ul>
 *
 * <p>If no recognized headings are found, falls back to paragraph splitting.</p>
 */
@Component
public class LegalSplitter {

  // Matches section/article/§ headings and numbered ALL-CAPS clause titles
  private static final Pattern SECTION_PATTERN = Pattern.compile(
      "(?m)^("
      + "(?:Section|SECTION|Sec\\.)\\s+\\d+[\\d\\.]*[^\\n]*"  // Section 1, Section 1.1 TITLE
      + "|(?:Article|ARTICLE)\\s+(?:[IVXLCDM]+|\\d+)[^\\n]*"  // Article I, Article 1
      + "|§\\s*\\d+[\\d\\.]*[^\\n]*"                           // § 1940, § 1940.5
      + "|\\d+\\.\\s+[A-Z][A-Z ]{2,}[^\\n]*"                  // 1. DEFINITIONS, 2. TERM
      + ")"
  );

  /**
   * Splits legal text into sections.
   * Falls back to paragraph splitting if no section headings are detected.
   *
   * @param text the cleaned legal document text
   * @return list of section chunks; empty list if text is null or blank
   */
  public List<String> split(String text) {
    List<String> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) return chunks;

    Matcher matcher = SECTION_PATTERN.matcher(text);
    List<Integer> sectionStarts = new ArrayList<>();

    while (matcher.find()) {
      sectionStarts.add(matcher.start());
    }

    if (sectionStarts.isEmpty()) {
      return fallbackSplit(text);
    }

    // Any preamble text before the first section heading becomes its own chunk
    if (sectionStarts.get(0) > 0) {
      String preamble = text.substring(0, sectionStarts.get(0)).strip();
      if (!preamble.isBlank()) {
        chunks.add(preamble);
      }
    }

    for (int i = 0; i < sectionStarts.size(); i++) {
      int start = sectionStarts.get(i);
      int end = (i + 1 < sectionStarts.size()) ? sectionStarts.get(i + 1) : text.length();
      String section = text.substring(start, end).strip();
      if (!section.isBlank()) {
        chunks.add(section);
      }
    }

    return chunks;
  }

  /**
   * Extracts a section number from a heading line for use as metadata.
   *
   * <p>Examples: "Section 1.2 Definitions" → "1.2",
   * "§ 1940.5 Habitability" → "1940.5",
   * "3. RENT PAYMENT" → "3"</p>
   *
   * @param heading the first line of a section chunk
   * @return the extracted section number, or empty string if none found
   */
  public String extractSectionNumber(String heading) {
    if (heading == null || heading.isBlank()) return "";

    // § 1940 or § 1940.5
    Matcher m = Pattern.compile("§\\s*(\\d+(?:\\.\\d+)*)").matcher(heading);
    if (m.find()) return m.group(1);

    // Section 1, Section 1.1
    m = Pattern.compile("(?i)(?:Section|Sec\\.)\\s+(\\d+(?:\\.\\d+)*)").matcher(heading);
    if (m.find()) return m.group(1);

    // Article I or Article 1
    m = Pattern.compile("(?i)Article\\s+([IVXLCDM]+|\\d+)").matcher(heading);
    if (m.find()) return m.group(1);

    // 1. TITLE or 2. TERM
    m = Pattern.compile("^(\\d+)\\.").matcher(heading.strip());
    if (m.find()) return m.group(1);

    return "";
  }

  /**
   * Returns the regex pattern used to detect section headings.
   */
  public Pattern getSectionPattern() {
    return SECTION_PATTERN;
  }

  /**
   * Fallback: splits by double newline (paragraphs) when no headings are found.
   */
  private List<String> fallbackSplit(String text) {
    List<String> chunks = new ArrayList<>();
    for (String para : text.split("\\n\\n+")) {
      String trimmed = para.strip();
      if (!trimmed.isBlank()) {
        chunks.add(trimmed);
      }
    }
    return chunks;
  }
}
