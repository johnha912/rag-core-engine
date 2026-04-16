package com.ragcore.adapter.domain;

import com.ragcore.adapter.DocumentAdapter;
import com.ragcore.model.Chunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.InputStream;
import java.util.List;

public abstract class BaseDocumentAdapter implements DocumentAdapter {

  // 子类只需要实现这一个方法
  protected abstract List<Chunk> buildChunks(String rawText, String fileName);

  @Override
  public List<Chunk> parse(InputStream inputStream, String fileName) throws Exception {
    if (inputStream == null) throw new IllegalArgumentException("InputStream cannot be null.");
    if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName cannot be blank.");

    String lower = fileName.toLowerCase();

    if (lower.endsWith(".pdf"))  return parsePdf(inputStream, fileName);
    if (lower.endsWith(".docx")) return parseDocx(inputStream, fileName);
    if (lower.endsWith(".md"))   return parseMd(inputStream, fileName);
    if (lower.endsWith(".txt"))  return parseTxt(inputStream, fileName);

    throw new IllegalArgumentException("Unsupported file type: " + fileName);
  }

  private List<Chunk> parsePdf(InputStream inputStream, String fileName) throws Exception {
    byte[] bytes = inputStream.readAllBytes();
    try (PDDocument doc = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      return buildChunks(stripper.getText(doc), fileName);
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
    return buildChunks(new String(inputStream.readAllBytes()), fileName);
  }

  private List<Chunk> parseTxt(InputStream inputStream, String fileName) throws Exception {
    return buildChunks(new String(inputStream.readAllBytes()), fileName);
  }
}