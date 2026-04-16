package com.ragcore.adapter.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.adapter.domain.general.TextCleaner;
import com.ragcore.adapter.domain.general.TextSplitter;
import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JsonAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  public JsonAdapter(TextCleaner cleaner, TextSplitter splitter) {
    if (cleaner == null) throw new IllegalArgumentException("TextCleaner cannot be null.");
    if (splitter == null) throw new IllegalArgumentException("TextSplitter cannot be null.");
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    List<Chunk> chunks = new ArrayList<>();
    JsonElement root = JsonParser.parseReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    if (root.isJsonArray()) {
      JsonArray array = root.getAsJsonArray();
      for (int i = 0; i < array.size(); i++) {
        String text = elementToText(array.get(i));
        String cleaned = cleaner.clean(text);
        if (cleaned.isEmpty()) continue;

        List<String> splitTexts = splitter.split(cleaned);
        for (int j = 0; j < splitTexts.size(); j++) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("type", "array");
          metadata.put("elementIndex", String.valueOf(i));
          metadata.put("chunkIndex", String.valueOf(j));
          chunks.add(new Chunk(splitTexts.get(j), metadata, fileName));
        }
      }
    } else if (root.isJsonObject()) {
      String text = elementToText(root);
      String cleaned = cleaner.clean(text);
      if (!cleaned.isEmpty()) {
        List<String> splitTexts = splitter.split(cleaned);
        for (int i = 0; i < splitTexts.size(); i++) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("type", "object");
          metadata.put("chunkIndex", String.valueOf(i));
          chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
        }
      }
    }

    return chunks;
  }

  private String elementToText(JsonElement element) {
    if (element.isJsonNull()) return "";
    if (element.isJsonPrimitive()) return element.getAsString();
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        if (sb.length() > 0) sb.append("\n");
        String valueStr = entry.getValue().isJsonPrimitive()
            ? entry.getValue().getAsString()
            : entry.getValue().toString();
        sb.append(entry.getKey()).append(": ").append(valueStr);
      }
      return sb.toString();
    }
    if (element.isJsonArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonElement e : element.getAsJsonArray()) {
        if (sb.length() > 0) sb.append("\n");
        sb.append(elementToText(e));
      }
      return sb.toString();
    }
    return "";
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    return fileName.toLowerCase().endsWith(".json");
  }

  @Override
  public String getDomainName() {
    return "JSON";
  }
}