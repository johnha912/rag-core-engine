package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link DocumentAdapter} for CSV files. Reads the first row as column headers, then
 * converts each subsequent data row into a "header: value" text representation that is cleaned and
 * split into chunks.
 */
@Component
public class CsvAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  /**
   * Constructs a CsvAdapter with the given cleaner and splitter.
   *
   * @param cleaner  the text cleaner to normalize row text
   * @param splitter the text splitter to divide text into chunks
   * @throws IllegalArgumentException if cleaner or splitter is null
   */
  public CsvAdapter(TextCleaner cleaner, TextSplitter splitter) {
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
   * Parses a CSV input stream into a list of {@link Chunk} objects. The first line is treated as
   * the header row. Each data row is converted to a "header: value, ..." string and chunked.
   *
   * @param inputStream the input stream of the CSV file
   * @param fileName    the original file name
   * @return a list of chunks, one or more per data row
   * @throws Exception if the stream cannot be read
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
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(inputStream, StandardCharsets.UTF_8));

    String headerLine = reader.readLine();
    if (headerLine == null || headerLine.isBlank()) {
      return chunks;
    }

    String[] headers = parseCsvLine(headerLine);
    String line;
    int rowIndex = 0;

    while ((line = reader.readLine()) != null) {
      if (line.isBlank()) {
        continue;
      }

      String[] values = parseCsvLine(line);
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < headers.length; i++) {
        String value = i < values.length ? values[i].strip() : "";
        if (!value.isEmpty()) {
          if (sb.length() > 0) {
            sb.append(", ");
          }
          sb.append(headers[i].strip()).append(": ").append(value);
        }
      }

      String content = cleaner.clean(sb.toString());
      if (!content.isEmpty()) {
        List<String> splitTexts = splitter.split(content);
        for (int i = 0; i < splitTexts.size(); i++) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("rowIndex", String.valueOf(rowIndex));
          metadata.put("chunkIndex", String.valueOf(i));
          chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
        }
      }

      rowIndex++;
    }

    return chunks;
  }

  /**
   * Parses a single CSV line into an array of field values, respecting double-quoted fields.
   * Escaped quotes ({@code ""}) inside quoted fields are handled correctly.
   *
   * @param line the raw CSV line
   * @return an array of field strings
   */
  private String[] parseCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++; // skip the escaped quote
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString());
    return fields.toArray(new String[0]);
  }

  /**
   * Returns true if the given file name represents a CSV file.
   *
   * @param fileName the file name to check
   * @return true if the file extension is .csv
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    return fileName.toLowerCase().endsWith(".csv");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "CSV"
   */
  @Override
  public String getDomainName() {
    return "CSV";
  }
}