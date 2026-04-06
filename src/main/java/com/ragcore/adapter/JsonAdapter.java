package com.ragcore.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link DocumentAdapter} for JSON files. Handles both JSON arrays (each element becomes
 * a chunk) and JSON objects (the whole object becomes one or more chunks). Object fields are
 * formatted as "key: value" text for embedding-friendly output.
 */
@Component
public class JsonAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  /**
   * Constructs a JsonAdapter with the given cleaner and splitter.
   *
   * @param cleaner  the text cleaner to normalize extracted text
   * @param splitter the text splitter to divide text into chunks
   * @throws IllegalArgumentException if cleaner or splitter is null
   */
  public JsonAdapter(TextCleaner cleaner, TextSplitter splitter) {
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
   * Parses a JSON input stream into a list of {@link Chunk} objects.
   *
   * <ul>
   *   <li>If the root is a JSON array, each element is converted to text and chunked
   *       independently, with {@code elementIndex} metadata.</li>
   *   <li>If the root is a JSON object, it is converted to text and chunked as a single unit.</li>
   * </ul>
   *
   * @param inputStream the input stream of the JSON file
   * @param fileName    the original file name
   * @return a list of chunks extracted from the JSON document
   * @throws Exception if the stream cannot be read or the JSON is malformed
   */
  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null.");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("File name cannot be null or blank.");
    }

    List<Chunk> chunks = new ArrayList<>();
    JsonElement root = JsonParser.parseReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    if (root.isJsonArray()) {
      JsonArray array = root.getAsJsonArray();
      for (int i = 0; i < array.size(); i++) {
        String text = elementToText(array.get(i));
        String cleaned = cleaner.clean(text);
        if (cleaned.isEmpty()) {
          continue;
        }
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

  /**
   * Recursively converts a {@link JsonElement} into a human-readable text string.
   *
   * <ul>
   *   <li>Primitives: their string value.</li>
   *   <li>Objects: "key: value" pairs separated by newlines; nested non-primitive values
   *       use their raw JSON representation.</li>
   *   <li>Arrays: each element on its own line.</li>
   * </ul>
   *
   * @param element the JSON element to convert
   * @return a text representation of the element
   */
  private String elementToText(JsonElement element) {
    if (element.isJsonNull()) {
      return "";
    }
    if (element.isJsonPrimitive()) {
      return element.getAsString();
    }
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        JsonElement val = entry.getValue();
        String valueStr = val.isJsonPrimitive() ? val.getAsString() : val.toString();
        sb.append(entry.getKey()).append(": ").append(valueStr);
      }
      return sb.toString();
    }
    if (element.isJsonArray()) {
      StringBuilder sb = new StringBuilder();
      for (JsonElement e : element.getAsJsonArray()) {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        sb.append(elementToText(e));
      }
      return sb.toString();
    }
    return "";
  }

  /**
   * Returns true if the given file name represents a JSON file.
   *
   * @param fileName the file name to check
   * @return true if the file extension is .json
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    return fileName.toLowerCase().endsWith(".json");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "JSON"
   */
  @Override
  public String getDomainName() {
    return "JSON";
  }
}