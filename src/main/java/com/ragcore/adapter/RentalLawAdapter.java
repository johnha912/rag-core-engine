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
 * Implements {@link DocumentAdapter} for California rental law and lease agreement files.
 * Supports {@code .pdf} and {@code .docx} formats.
 *
 * <p>Uses {@link LegalTextCleaner} to remove page noise and {@link LegalSplitter} to split
 * text at section/article boundaries. Each chunk's metadata includes the section number
 * and section title for precise RAG source citations.</p>
 */
@Component
public class RentalLawAdapter implements DocumentAdapter {

  private final LegalTextCleaner cleaner;
  private final LegalSplitter splitter;

  public RentalLawAdapter(LegalTextCleaner cleaner, LegalSplitter splitter) {
    this.cleaner = cleaner;
    this.splitter = splitter;
  }

  /**
   * Parses a rental law document into a list of {@link Chunk} objects split by section.
   *
   * @param inputStream the input stream of the document
   * @param fileName    the original file name (used for format detection and metadata)
   * @return a list of chunks, one per detected section or paragraph
   * @throws IllegalArgumentException if inputStream or fileName is null/blank, or format unsupported
   * @throws Exception                if the document cannot be read or parsed
   */
  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) {
      throw new IllegalArgumentException("InputStream cannot be null.");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("fileName cannot be null or blank.");
    }

    String lower = fileName.toLowerCase();

    if (lower.endsWith(".pdf")) return parsePdf(inputStream, fileName);
    if (lower.endsWith(".docx")) return parseDocx(inputStream, fileName);

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

  /**
   * Cleans, splits, and wraps text as Chunks with section metadata.
   */
  private List<Chunk> buildChunks(String rawText, String fileName) {
    String cleaned = cleaner.clean(rawText);
    List<String> sections = splitter.split(cleaned);
    List<Chunk> chunks = new ArrayList<>();

    // Max ~6000 chars per chunk to stay safely under OpenAI's 8192 token limit
    int maxChunkSize = 6000;

    for (int i = 0; i < sections.size(); i++) {
      String section = sections.get(i);
      String firstLine = section.split("\\n")[0].strip();

      Map<String, String> baseMetadata = new HashMap<>();
      baseMetadata.put("sectionIndex", String.valueOf(i + 1));
      baseMetadata.put("sectionNumber", splitter.extractSectionNumber(firstLine));
      baseMetadata.put("sectionTitle", firstLine.isEmpty() ? "Unknown" : firstLine);

      // If section is too long, split further by characters
      if (section.length() > maxChunkSize) {
        int part = 0;
        for (int start = 0; start < section.length(); start += maxChunkSize) {
          int end = Math.min(start + maxChunkSize, section.length());
          String subChunk = section.substring(start, end).strip();
          if (!subChunk.isBlank()) {
            Map<String, String> metadata = new HashMap<>(baseMetadata);
            metadata.put("part", String.valueOf(part + 1));
            chunks.add(new Chunk(subChunk, metadata, fileName));
            part++;
          }
        }
      } else {
        chunks.add(new Chunk(section, baseMetadata, fileName));
      }
    }
    return chunks;
  }

  /**
   * Returns true for {@code .pdf} and {@code .docx} files.
   *
   * @param fileName the file name to check
   * @return true if the extension is {@code .pdf} or {@code .docx}
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "Rental Law"
   */
  @Override
  public String getDomainName() {
    return "Rental Law";
  }
}
