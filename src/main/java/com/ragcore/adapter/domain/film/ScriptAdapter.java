package com.ragcore.adapter.domain.film;

import com.ragcore.adapter.BaseDocumentAdapter;
import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScriptAdapter extends BaseDocumentAdapter {

  private final ScriptTextCleaner cleaner;
  private final ScriptSplitter splitter;

  public ScriptAdapter(ScriptTextCleaner cleaner, ScriptSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("ScriptTextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("ScriptSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  protected List<Chunk> buildChunks(String rawText, String fileName) {
    String cleaned = cleaner.clean(rawText);
    List<String> scenes = splitter.split(cleaned);
    List<Chunk> chunks = new ArrayList<>();

    for (int i = 0; i < scenes.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      metadata.put("sceneIndex", String.valueOf(i + 1));
      metadata.put("sceneHeading", extractHeading(scenes.get(i)));
      chunks.add(new Chunk(scenes.get(i), metadata, fileName));
    }
    return chunks;
  }

  private String extractHeading(String scene) {
    String firstLine = scene.split("\n")[0].strip();
    return firstLine.isEmpty() ? "Unknown Scene" : firstLine;
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".docx") || lower.endsWith(".md") || lower.endsWith(".fdx");
  }

  @Override
  public String getDomainName() { return "Film Script"; }
}