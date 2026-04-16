package com.ragcore.adapter.domain.legal;

import com.ragcore.adapter.BaseDocumentAdapter;
import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RentalLawAdapter extends BaseDocumentAdapter {

  private final LegalTextCleaner cleaner;
  private final LegalSplitter splitter;

  public RentalLawAdapter(LegalTextCleaner cleaner, LegalSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("LegalTextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("LegalSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  protected List<Chunk> buildChunks(String rawText, String fileName) {
    String cleaned = cleaner.clean(rawText);
    List<String> sections = splitter.split(cleaned);
    List<Chunk> chunks = new ArrayList<>();
    int maxChunkSize = 6000;

    for (int i = 0; i < sections.size(); i++) {
      String section = sections.get(i);
      String firstLine = section.split("\n")[0].strip();

      Map<String, String> baseMetadata = new HashMap<>();
      baseMetadata.put("sectionIndex", String.valueOf(i + 1));
      baseMetadata.put("sectionNumber", splitter.extractSectionNumber(firstLine));
      baseMetadata.put("sectionTitle", firstLine.isEmpty() ? "Unknown" : firstLine);

      if (section.length() > maxChunkSize) {
        int part = 0;
        for (int start = 0; start < section.length(); start += maxChunkSize) {
          int end = Math.min(start + maxChunkSize, section.length());
          String subChunk = section.substring(start, end).strip();
          if (!subChunk.isBlank()) {
            Map<String, String> metadata = new HashMap<>(baseMetadata);
            metadata.put("part", String.valueOf(++part));
            chunks.add(new Chunk(subChunk, metadata, fileName));
          }
        }
      } else {
        chunks.add(new Chunk(section, baseMetadata, fileName));
      }
    }
    return chunks;
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx");
  }

  @Override
  public String getDomainName() { return "Rental Law"; }
}