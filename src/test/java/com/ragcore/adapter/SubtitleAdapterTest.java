package com.ragcore.adapter;

import com.ragcore.adapter.domain.film.SubtitleAdapter;
import com.ragcore.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubtitleAdapterTest {

  private SubtitleAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new SubtitleAdapter();
  }

  @Test
  void supports_srtFile_returnsTrue() {
    assertTrue(adapter.supports("movie.srt"));
  }

  @Test
  void supports_txtFile_returnsFalse() {
    assertFalse(adapter.supports("movie.txt"));
  }

  @Test
  void supports_nullFileName_returnsFalse() {
    assertFalse(adapter.supports(null));
  }

  @Test
  void getDomainName_returnsSubtitle() {
    assertEquals("Subtitle", adapter.getDomainName());
  }

  @Test
  void parse_validSrt_returnsChunksWithTimecodes() throws Exception {
    String srt = """
                1
                00:01:23,456 --> 00:01:26,789
                Hello, my name is John.

                2
                00:01:27,000 --> 00:01:29,500
                Nice to meet you, John.

                3
                00:05:00,000 --> 00:05:03,000
                Let's talk about the script.
                """;

    InputStream is = new ByteArrayInputStream(
        srt.getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "movie.srt");

    assertEquals(3, chunks.size());
    assertEquals("Hello, my name is John.", chunks.get(0).getContent());
    assertEquals("00:01:23,456", chunks.get(0).getMetadata().get("startTime"));
    assertEquals("00:01:26,789", chunks.get(0).getMetadata().get("endTime"));
    assertEquals("1", chunks.get(0).getMetadata().get("minute"));
    assertEquals("5", chunks.get(2).getMetadata().get("minute"));
  }

  @Test
  void parse_emptyContent_returnsEmptyList() throws Exception {
    InputStream is = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "movie.srt");
    assertTrue(chunks.isEmpty());
  }

  @Test
  void parse_nullInputStream_throwsException() {
    assertThrows(IllegalArgumentException.class,
        () -> adapter.parse(null, "movie.srt"));
  }

  @Test
  void parse_multilineDialogue_combinesIntoOneChunk() throws Exception {
    String srt = """
                1
                00:02:00,000 --> 00:02:04,000
                This is line one.
                This is line two.
                """;

    InputStream is = new ByteArrayInputStream(
        srt.getBytes(StandardCharsets.UTF_8));
    List<Chunk> chunks = adapter.parse(is, "movie.srt");

    assertEquals(1, chunks.size());
    assertTrue(chunks.get(0).getContent().contains("line one"));
    assertTrue(chunks.get(0).getContent().contains("line two"));
  }
}