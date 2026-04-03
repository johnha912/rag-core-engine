package com.ragcore.adapter;

import com.ragcore.model.Chunk;
import java.io.InputStream;
import java.util.List;

/**
 * Interface for adapting different document formats into chunks.
 */
public interface DocumentAdapter {

  List<Chunk> parse(InputStream inputStream, String fileName) throws Exception;

  boolean supports(String fileName);

  String getDomainName();
}