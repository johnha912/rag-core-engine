package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link DocumentAdapter} for HTML files. Uses Jsoup to extract body text and strip tag
 * noise, then cleans and splits it into chunks.
 */
@Component
public class HtmlAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  /**
   * Constructs an HtmlAdapter with the given cleaner and splitter.
   *
   * @param cleaner  the text cleaner to remove HTML noise
   * @param splitter the text splitter to divide text into chunks
   * @throws IllegalArgumentException if cleaner or splitter is null
   */
  public HtmlAdapter(TextCleaner cleaner, TextSplitter splitter) {
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
   * Parses an HTML input stream into a list of {@link Chunk} objects. Extracts text from the
   * document body, using section headings (h1–h6) to create semantically meaningful chunks with
   * heading metadata.
   *
   * @param inputStream the input stream of the HTML file
   * @param fileName    the original file name
   * @return a list of chunks extracted from the HTML document
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

    String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    Document doc = Jsoup.parse(html);

    // Remove script and style elements to avoid noise
    doc.select("script, style, nav, footer, header").remove();

    List<Chunk> chunks = new ArrayList<>();
    Elements bodyChildren = doc.body() != null ? doc.body().children() : new Elements();

    String currentHeading = "Introduction";
    StringBuilder sectionText = new StringBuilder();
    int sectionIndex = 0;

    for (Element element : bodyChildren) {
      if (isHeading(element)) {
        // Flush previous section
        if (sectionText.length() > 0) {
          chunks.addAll(
              createChunksFromSection(sectionText.toString(), currentHeading,
                  sectionIndex, fileName));
          sectionIndex++;
        }
        currentHeading = element.text().strip();
        sectionText = new StringBuilder();
      } else {
        String text = element.text().strip();
        if (!text.isEmpty()) {
          sectionText.append(text).append("\n");
        }
      }
    }

    // Flush last section
    if (sectionText.length() > 0) {
      chunks.addAll(
          createChunksFromSection(sectionText.toString(), currentHeading,
              sectionIndex, fileName));
    }

    // Fallback: if no sections found, parse entire body text
    if (chunks.isEmpty()) {
      String bodyText = doc.body() != null ? doc.body().text() : "";
      String cleaned = cleaner.clean(bodyText);
      if (!cleaned.isEmpty()) {
        List<String> splitTexts = splitter.split(cleaned);
        for (int i = 0; i < splitTexts.size(); i++) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("section", "Full Document");
          metadata.put("chunkIndex", String.valueOf(i));
          chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
        }
      }
    }

    return chunks;
  }

  /**
   * Checks if the given element is an HTML heading (h1–h6).
   *
   * @param element the Jsoup element to check
   * @return true if the element is a heading tag
   */
  private boolean isHeading(Element element) {
    String tagName = element.tagName().toLowerCase();
    return tagName.matches("h[1-6]");
  }

  /**
   * Creates chunks from a section of text with heading metadata.
   *
   * @param sectionText  the raw section text
   * @param heading      the heading title for this section
   * @param sectionIndex the index of this section in the document
   * @param fileName     the source file name
   * @return a list of chunks for this section
   */
  private List<Chunk> createChunksFromSection(String sectionText, String heading,
      int sectionIndex, String fileName) {
    List<Chunk> chunks = new ArrayList<>();
    String cleaned = cleaner.clean(sectionText);

    if (cleaned.isEmpty()) {
      return chunks;
    }

    List<String> splitTexts = splitter.split(cleaned);
    for (int i = 0; i < splitTexts.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      metadata.put("heading", heading);
      metadata.put("sectionIndex", String.valueOf(sectionIndex));
      metadata.put("chunkIndex", String.valueOf(i));
      chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
    }

    return chunks;
  }

  /**
   * Returns true if the given file name represents an HTML file.
   *
   * @param fileName the file name to check
   * @return true if the file extension is .html or .htm
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    String lower = fileName.toLowerCase();
    return lower.endsWith(".html") || lower.endsWith(".htm");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "HTML"
   */
  @Override
  public String getDomainName() {
    return "HTML";
  }
}