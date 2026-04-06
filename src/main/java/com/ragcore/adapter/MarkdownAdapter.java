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
 * Implements {@link DocumentAdapter} for Markdown files. Splits by heading level (# ## ###) to
 * preserve document structure, then cleans and splits each section into chunks.
 */
@Component
public class MarkdownAdapter implements DocumentAdapter {

  /**
   * Regex pattern to match Markdown headings (# through ######).
   */
  private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$",
      Pattern.MULTILINE);

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  /**
   * Constructs a MarkdownAdapter with the given cleaner and splitter.
   *
   * @param cleaner  the text cleaner to remove noise
   * @param splitter the text splitter to divide text into chunks
   * @throws IllegalArgumentException if cleaner or splitter is null
   */
  public MarkdownAdapter(TextCleaner cleaner, TextSplitter splitter) {
    if (cleaner == null) {
      throw new IllegalArgumentException("TextCleaner cannot be null.");
    }
    if (splitter == null) {
      throw new IllegalArgumentException("TextSplitter cannot be null.");
    }
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  /**
   * Parses a Markdown input stream into a list of {@link Chunk} objects. Splits the document by
   * heading boundaries to preserve semantic structure. Each chunk includes metadata about its
   * heading title and heading level.
   *
   * @param inputStream the input stream of the Markdown file
   * @param fileName    the original file name
   * @return a list of chunks extracted from the Markdown document
   * @throws Exception if the document cannot be read or parsed
   */
  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null.");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("File name cannot be null or blank.");
    }

    String rawText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    List<Section> sections = splitByHeadings(rawText);
    List<Chunk> chunks = new ArrayList<>();
    int sectionIndex = 0;

    for (Section section : sections) {
      String cleaned = cleaner.clean(section.content);
      if (cleaned.isEmpty()) {
        continue;
      }

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

  /**
   * Splits raw Markdown text into sections based on heading boundaries. Text before the first
   * heading is assigned to an "Introduction" section.
   *
   * @param rawText the raw Markdown text
   * @return a list of sections, each with heading, level, and content
   */
  private List<Section> splitByHeadings(String rawText) {
    List<Section> sections = new ArrayList<>();
    Matcher matcher = HEADING_PATTERN.matcher(rawText);

    int lastEnd = 0;
    String currentHeading = "Introduction";
    int currentLevel = 0;

    while (matcher.find()) {
      // Capture text before this heading as content of the previous section
      String contentBefore = rawText.substring(lastEnd, matcher.start()).strip();
      if (!contentBefore.isEmpty()) {
        sections.add(new Section(currentHeading, currentLevel, contentBefore));
      }

      // Update heading info
      currentHeading = matcher.group(2).strip();
      currentLevel = matcher.group(1).length();
      lastEnd = matcher.end();
    }

    // Capture remaining text after the last heading
    String remaining = rawText.substring(lastEnd).strip();
    if (!remaining.isEmpty()) {
      sections.add(new Section(currentHeading, currentLevel, remaining));
    }

    return sections;
  }

  /**
   * Internal data holder for a Markdown section.
   */
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

  /**
   * Returns true if the given file name represents a Markdown file.
   *
   * @param fileName the file name to check
   * @return true if the file extension is .md
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    return fileName.toLowerCase().endsWith(".md");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "Markdown"
   */
  @Override
  public String getDomainName() {
    return "Markdown";
  }
}