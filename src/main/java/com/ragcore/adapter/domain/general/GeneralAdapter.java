package com.ragcore.adapter.domain.general;

import com.ragcore.adapter.BaseDocumentAdapter;
import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeneralAdapter extends BaseDocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  public GeneralAdapter(TextCleaner cleaner, TextSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("TextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("TextSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  protected List<Chunk> buildChunks(String rawText, String fileName) {
    String cleaned = cleaner.clean(rawText);
    if (cleaned.isEmpty()) return new ArrayList<>();

    List<String> splitTexts = splitter.split(cleaned);
    List<Chunk> chunks = new ArrayList<>();
    for (int i = 0; i < splitTexts.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      metadata.put("chunkIndex", String.valueOf(i));
      chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
    }
    return chunks;
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".txt");
  }

  @Override
  public String getDomainName() { return "General"; }
}