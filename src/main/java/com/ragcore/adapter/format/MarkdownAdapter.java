package com.ragcore.adapter.format;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.adapter.domain.general.TextCleaner;
import com.ragcore.adapter.domain.general.TextSplitter;
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

@Component
public class MarkdownAdapter implements DocumentAdapter {

  private static final Pattern HEADING_PATTERN = Pattern.compile(
      "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE
  );

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  public MarkdownAdapter(TextCleaner cleaner, TextSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("TextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("TextSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    List<Section> sections = splitByHeadings(rawText);
    List<Chunk> chunks = new ArrayList<>();
    int sectionIndex = 0;

    for (Section section : sections) {
      String cleaned = cleaner.clean(section.content);
      if (cleaned.isEmpty()) continue;

      List<String> splitTexts = splitter.split(cleaned);
      for (int i = 0; i < splitTexts.size(); i++) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("heading", section.heading);
        metadata.put("headingLevel", String.valueOf(section.level));
        metadata.put("sectionIndex", String.valueOf(sectionIndex));
        metadata.put("chunkIndex", String.valueOf(i));
        chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
      }
      sectionIndex++;
    }

    return chunks;
  }

  private List<Section> splitByHeadings(String rawText) {
    List<Section> sections = new ArrayList<>();
    Matcher matcher = HEADING_PATTERN.matcher(rawText);

    int lastEnd = 0;
    String currentHeading = "Introduction";
    int currentLevel = 0;

    while (matcher.find()) {
      String contentBefore = rawText.substring(lastEnd, matcher.start()).strip();
      if (!contentBefore.isEmpty()) {
        sections.add(new Section(currentHeading, currentLevel, contentBefore));
      }
      currentHeading = matcher.group(2).strip();
      currentLevel = matcher.group(1).length();
      lastEnd = matcher.end();
    }

    String remaining = rawText.substring(lastEnd).strip();
    if (!remaining.isEmpty()) {
      sections.add(new Section(currentHeading, currentLevel, remaining));
    }

    return sections;
  }

  private static class Section {
    final String heading;
    final int level;
    final String content;

    Section(String heading, int level, String content) {
      this.heading = heading;
      this.level = level;
      this.content = content;
    }
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    return fileName.toLowerCase().endsWith(".md");
  }

  @Override
  public String getDomainName() {
    return "Markdown";
  }
}