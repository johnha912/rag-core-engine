package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link DocumentAdapter} for screenplay files.
 * Supports .pdf, .docx, and .md formats.
 * Splits by scene headings (INT./EXT.) for semantically accurate chunks.
 */
@Component
public class ScriptAdapter implements DocumentAdapter {

  private final ScriptTextCleaner cleaner;
  private final ScriptSplitter splitter;

  public ScriptAdapter(ScriptTextCleaner cleaner, ScriptSplitter splitter) {
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String lower = fileName.toLowerCase();

    if (lower.endsWith(".pdf")) return parsePdf(inputStream, fileName);
    if (lower.endsWith(".docx")) return parseDocx(inputStream, fileName);
    if (lower.endsWith(".md")) return parseMd(inputStream, fileName);

    throw new IllegalArgumentException("Unsupported file type: " + fileName);
  }

  private List<Chunk> parsePdf(InputStream inputStream, String fileName) throws Exception {
    byte[] bytes = inputStream.readAllBytes();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      String rawText = stripper.getText(doc);
      return buildChunks(rawText, fileName);
    }
  }

  private List<Chunk> parseDocx(InputStream inputStream, String fileName) throws Exception {
    try (XWPFDocument doc = new XWPFDocument(inputStream)) {
      StringBuilder sb = new StringBuilder();
      for (XWPFParagraph para : doc.getParagraphs()) {
        String text = para.getText().strip();
        if (!text.isBlank()) sb.append(text).append("\n");
      }
      return buildChunks(sb.toString(), fileName);
    }
  }

  private List<Chunk> parseMd(InputStream inputStream, String fileName) throws Exception {
    String rawText = new String(inputStream.readAllBytes());
    return buildChunks(rawText, fileName);
  }

  /**
   * Cleans text, splits into scenes, and wraps each scene as a Chunk.
   */
  private List<Chunk> buildChunks(String rawText, String fileName) {
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

  /**
   * Extracts the first line of a scene as its heading.
   */
  private String extractHeading(String scene) {
    String firstLine = scene.split("\\n")[0].strip();
    return firstLine.isEmpty() ? "Unknown Scene" : firstLine;
  }

  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".docx") || lower.endsWith(".md") || lower.endsWith(".fdx");
  }

  @Override
  public String getDomainName() {
    return "Film Script";
  }
}