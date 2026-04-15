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
 * Implements {@link DocumentAdapter} for SRT subtitle files.
 * Each subtitle entry becomes one Chunk with timecode metadata.
 * Supports querying like "what was said at minute 5?" or "find the scene where X says Y".
 */
@Component
public class SubtitleAdapter implements DocumentAdapter {

  // Matches SRT timecode: "00:01:23,456 --> 00:01:26,789"
  private static final Pattern TIMECODE_PATTERN = Pattern.compile(
      "(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})\\s-->\\s(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})"
  );

  // Matches subtitle index number on its own line
  private static final Pattern INDEX_PATTERN = Pattern.compile(
      "(?m)^\\d+$"
  );

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    return parseSubtitles(rawText, fileName);
  }

  private List<Chunk> parseSubtitles(String text, String fileName) {
    List<Chunk> chunks = new ArrayList<>();
    if (text == null || text.isBlank()) return chunks;

    // Split by double newline — each SRT entry is separated by blank line
    String[] entries = text.split("\\n\\s*\\n");

    for (String entry : entries) {
      entry = entry.strip();
      if (entry.isBlank()) continue;

      String[] lines = entry.split("\\n");
      if (lines.length < 2) continue;

      // Find timecode line
      String startTime = null;
      String endTime = null;
      StringBuilder dialogueBuilder = new StringBuilder();

      for (String line : lines) {
        Matcher timeMatcher = TIMECODE_PATTERN.matcher(line);
        if (timeMatcher.find()) {
          startTime = timeMatcher.group(1);
          endTime = timeMatcher.group(2);
        } else if (!INDEX_PATTERN.matcher(line.strip()).matches()) {
          // Not an index number, must be dialogue
          if (dialogueBuilder.length() > 0) dialogueBuilder.append(" ");
          dialogueBuilder.append(line.strip());
        }
      }

      String dialogue = dialogueBuilder.toString().strip();
      if (dialogue.isBlank() || startTime == null) continue;

      Map<String, String> metadata = new HashMap<>();
      metadata.put("startTime", startTime);
      metadata.put("endTime", endTime);
      metadata.put("minute", extractMinute(startTime));

      chunks.add(new Chunk(dialogue, metadata, fileName));
    }

    return chunks;
  }

  /**
   * Extracts the minute from a timecode for easier querying.
   * "00:05:23,456" → "5"
   */
  private String extractMinute(String timecode) {
    try {
      String[] parts = timecode.split(":");
      int hours = Integer.parseInt(parts[0]);
      int minutes = Integer.parseInt(parts[1]);
      return String.valueOf(hours * 60 + minutes);
    } catch (Exception e) {
      return "0";
    }
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".srt") || lower.endsWith(".vtt");
  }

  @Override
  public String getDomainName() {
    return "Subtitle";
  }
}