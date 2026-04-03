package com.ragcore.adapter;

import com.ragcore.model.Chunk;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements {@link DocumentAdapter} for PDF files. Uses Apache PDFBox to extract text,
 * then cleans and splits it into chunks. Also supports plain TXT files as a fallback.
 */
public class BasePdfAdapter implements DocumentAdapter {

  private final TextCleaner cleaner;
  private final TextSplitter splitter;

  /**
   * Constructs a BasePdfAdapter with the given cleaner and splitter.
   *
   * @param cleaner  the text cleaner to remove PDF noise
   * @param splitter the text splitter to divide text into chunks
   * @throws IllegalArgumentException if cleaner or splitter is null
   */
  public BasePdfAdapter(TextCleaner cleaner, TextSplitter splitter) {
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
   * Parses a PDF or TXT input stream into a list of {@link Chunk} objects.
   *
   * @param inputStream the input stream of the document
   * @param fileName    the original file name (for metadata and format detection)
   * @return a list of chunks extracted from the document
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

    String lowerName = fileName.toLowerCase();

    if (lowerName.endsWith(".pdf")) {
      return parsePdf(inputStream, fileName);
    } else if (lowerName.endsWith(".txt")) {
      return parseTxt(inputStream, fileName);
    } else {
      throw new IllegalArgumentException("Unsupported file type: " + fileName);
    }
  }

  /**
   * Extracts text from a PDF input stream page by page.
   *
   * @param inputStream the PDF input stream
   * @param fileName    the original file name
   * @return list of chunks with page metadata
   * @throws Exception if PDFBox fails to read the stream
   */
  private List<Chunk> parsePdf(InputStream inputStream, String fileName) throws Exception {
    List<Chunk> chunks = new ArrayList<>();

    byte[] bytes = inputStream.readAllBytes();
    try (PDDocument document = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      int totalPages = document.getNumberOfPages();

      for (int page = 1; page <= totalPages; page++) {
        stripper.setStartPage(page);
        stripper.setEndPage(page);

        String rawText = stripper.getText(document);
        String cleanedText = cleaner.clean(rawText);

        if (cleanedText.isEmpty()) {
          continue;
        }

        List<String> splitTexts = splitter.split(cleanedText);

        for (int i = 0; i < splitTexts.size(); i++) {
          Map<String, String> metadata = new HashMap<>();
          metadata.put("page", String.valueOf(page));
          metadata.put("chunkIndex", String.valueOf(i));

          chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
        }
      }
    }

    return chunks;
  }

  /**
   * Reads a plain text input stream, cleans and splits it into chunks.
   *
   * @param inputStream the TXT input stream
   * @param fileName    the original file name
   * @return list of chunks
   * @throws Exception if the stream cannot be read
   */
  private List<Chunk> parseTxt(InputStream inputStream, String fileName) throws Exception {
    String rawText = new String(inputStream.readAllBytes());
    String cleanedText = cleaner.clean(rawText);

    if (cleanedText.isEmpty()) {
      return new ArrayList<>();
    }

    List<String> splitTexts = splitter.split(cleanedText);
    List<Chunk> chunks = new ArrayList<>();

    for (int i = 0; i < splitTexts.size(); i++) {
      Map<String, String> metadata = new HashMap<>();
      metadata.put("page", "1");
      metadata.put("chunkIndex", String.valueOf(i));

      chunks.add(new Chunk(splitTexts.get(i), metadata, fileName));
    }

    return chunks;
  }

  /**
   * Returns true if the given file name represents a PDF or TXT file.
   *
   * @param fileName the file name to check
   * @return true if the file extension is .pdf or .txt
   */
  @Override
  public boolean supports(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    String lower = fileName.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".txt");
  }

  /**
   * Returns the domain name for this adapter.
   *
   * @return "PDF/TXT"
   */
  @Override
  public String getDomainName() {
    return "PDF/TXT";
  }
}