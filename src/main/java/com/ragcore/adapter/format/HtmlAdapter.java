package com.ragcore.adapter.format;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.adapter.domain.general.TextCleaner;
import com.ragcore.adapter.domain.general.TextSplitter;
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

@Component
public class HtmlAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  public HtmlAdapter(TextCleaner cleaner, TextSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("TextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("TextSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    Document doc = Jsoup.parse(html);
    doc.select("script, style, nav, footer, header").remove();

    List<Chunk> chunks = new ArrayList<>();
    Elements bodyChildren = doc.body() != null ? doc.body().children() : new Elements();

    String currentHeading = "Introduction";
    StringBuilder sectionText = new StringBuilder();
    int sectionIndex = 0;

    for (Element element : bodyChildren) {
      if (isHeading(element)) {
        if (sectionText.length() > 0) {
          chunks.addAll(createChunksFromSection(sectionText.toString(), currentHeading, sectionIndex, fileName));
          sectionIndex++;
        }
        currentHeading = element.text().strip();
        sectionText = new StringBuilder();
      } else {
        String text = element.text().strip();
        if (!text.isEmpty()) sectionText.append(text).append("\n");
      }
    }

    if (sectionText.length() > 0) {
      chunks.addAll(createChunksFromSection(sectionText.toString(), currentHeading, sectionIndex, fileName));
    }

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

  private boolean isHeading(Element element) {
    return element.tagName().toLowerCase().matches("h[1-6]");
  }

  private List<Chunk> createChunksFromSection(String sectionText, String heading, int sectionIndex, String fileName) {
    List<Chunk> chunks = new ArrayList<>();
    String cleaned = cleaner.clean(sectionText);
    if (cleaned.isEmpty()) return chunks;

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

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".html") || lower.endsWith(".htm");
  }

  @Override
  public String getDomainName() {
    return "HTML";
  }
}