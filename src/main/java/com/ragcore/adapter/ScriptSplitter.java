package com.ragcore.adapter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits screenplay text into chunks by scene headings (INT./EXT.).
 * Each scene becomes one chunk, preserving full semantic context.
 */
@Component
public class ScriptSplitter {

  // Matches scene headings like "INT. COFFEE SHOP - DAY" or "EXT. STREET - NIGHT"
  private static final Pattern SCENE_PATTERN = Pattern.compile(
      "(?m)^(INT\\.|EXT\\.|INT\\/EXT\\.|I\\/E\\.)[^\\n]*",
      Pattern.CASE_INSENSITIVE
  );

  /**
   * Splits screenplay text into scenes.
   * If no scene headings are found, falls back to paragraph splitting.
   *
   * @param text the cleaned screenplay text
   * @return list of scene chunks
   */
  public List<String> split(String text) {
    List<String> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) return chunks;

    Matcher matcher = SCENE_PATTERN.matcher(text);
    List<Integer> sceneStarts = new ArrayList<>();
    List<String> sceneHeadings = new ArrayList<>();

    // Find all scene heading positions
    while (matcher.find()) {
      sceneStarts.add(matcher.start());
      sceneHeadings.add(matcher.group().strip());
    }

    // No scene headings found — fall back to paragraph splitting
    if (sceneStarts.isEmpty()) {
      return fallbackSplit(text);
    }

    // Split text at each scene heading
    for (int i = 0; i < sceneStarts.size(); i++) {
      int start = sceneStarts.get(i);
      int end = (i + 1 < sceneStarts.size()) ? sceneStarts.get(i + 1) : text.length();
      String scene = text.substring(start, end).strip();
      if (!scene.isBlank()) {
        chunks.add(scene);
      }
    }

    return chunks;
  }

  /**
   * Fallback: splits by double newline (paragraphs) if no scene headings found.
   */
  private List<String> fallbackSplit(String text) {
    List<String> chunks = new ArrayList<>();
    String[] paragraphs = text.split("\\n\\n+");
    for (String para : paragraphs) {
      String trimmed = para.strip();
      if (!trimmed.isBlank()) {
        chunks.add(trimmed);
      }
    }
    return chunks;
  }

  /**
   * Returns the regex pattern used to detect scene headings.
   */
  public Pattern getScenePattern() {
    return SCENE_PATTERN;
  }
}