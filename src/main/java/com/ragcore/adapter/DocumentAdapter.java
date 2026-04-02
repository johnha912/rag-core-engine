package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import java.io.InputStream;
import java.util.List;

/**
 * Interface for adapting different document formats into chunks.
 *
 * <p>Each implementation handles a specific document type (e.g., PDF, TXT, HTML).
 * The RAG orchestrator uses {@link #supports(String)} to automatically select
 * the correct adapter for a given file, enabling the Open/Closed Principle —
 * new document types can be added without modifying existing code.</p>
 *
 * <p><strong>Contract:</strong> All interface files are frozen after Day 1.
 * No modifications without full team agreement.</p>
 */
public interface DocumentAdapter {

  /**
   * Parses the given input stream into a list of text chunks.
   *
   * <p>The implementation is responsible for:
   * <ul>
   *   <li>Reading raw content from the stream</li>
   *   <li>Cleaning the text (removing noise, artifacts)</li>
   *   <li>Splitting text into appropriately sized chunks</li>
   *   <li>Attaching metadata (e.g., page number, filename) to each chunk</li>
   * </ul>
   *
   * @param inputStream the input stream of the document to parse
   * @param fileName    the original file name (used for metadata and source tracking)
   * @return a list of {@link Chunk} objects extracted from the document
   * @throws Exception if the document cannot be read or parsed
   */
  List<Chunk> parse(InputStream inputStream, String fileName) throws Exception;

  /**
   * Checks whether this adapter can handle the given file name.
   *
   * <p>Typically checks the file extension (e.g., ".pdf", ".txt").
   * The orchestrator iterates all registered adapters and selects the first
   * one that returns {@code true}.</p>
   *
   * @param fileName the file name to check
   * @return {@code true} if this adapter can parse the given file type
   */
  boolean supports(String fileName);

  /**
   * Returns the domain name (document type) that this adapter handles.
   *
   * <p>Used for logging, status reporting, and identifying which adapter
   * processed a given document. Examples: "PDF", "TXT", "HTML".</p>
   *
   * @return a human-readable domain name string
   */
  String getDomainName();
}