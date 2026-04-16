package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements {@link DocumentAdapter} for storyboard scripts.
 * Splits by SHOT markers, preserving shot number, angle, and description.
 * Supports .txt and .md formats.
 */
@Component
public class StoryboardAdapter implements DocumentAdapter {

  // Matches shot headings like "SHOT 1", "SHOT 1A", "SHOT 12 - CLOSE UP"
  private static final Pattern SHOT_PATTERN = Pattern.compile(
      "(?m)^SHOT\\s+\\w+[^\\n]*",
      Pattern.CASE_INSENSITIVE
  );

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    return buildChunks(rawText, fileName);
  }

  private List<Chunk> buildChunks(String text, String fileName) {
    List<Chunk> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) return chunks;

    Matcher matcher = SHOT_PATTERN.matcher(text);
    List<Integer> shotStarts = new ArrayList<>();
    List<String> shotHeadings = new ArrayList<>();

    while (matcher.find()) {
      shotStarts.add(matcher.start());
      shotHeadings.add(matcher.group().strip());
    }

    // No SHOT markers found — treat whole text as one chunk
    if (shotStarts.isEmpty()) {
      chunks.add(new Chunk(text.strip(), new HashMap<>(), fileName));
      return chunks;
    }

    for (int i = 0; i < shotStarts.size(); i++) {
      int start = shotStarts.get(i);
      int end = (i + 1 < shotStarts.size()) ? shotStarts.get(i + 1) : text.length();
      String shotText = text.substring(start, end).strip();

      if (!shotText.isBlank()) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("shotNumber", String.valueOf(i + 1));
        metadata.put("shotHeading", shotHeadings.get(i));
        metadata.put("angle", extractAngle(shotHeadings.get(i)));
        chunks.add(new Chunk(shotText, metadata, fileName));
      }
    }

    return chunks;
  }

  /**
   * Extracts camera angle from shot heading if present.
   * e.g. "SHOT 1 - CLOSE UP" → "CLOSE UP"
   */
  private String extractAngle(String heading) {
    if (heading.contains("-")) {
      return heading.substring(heading.indexOf("-") + 1).strip();
    }
    return "UNSPECIFIED";
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".storyboard") || lower.endsWith(".txt");
  }

  @Override
  public String getDomainName() {
    return "Storyboard";
  }
}