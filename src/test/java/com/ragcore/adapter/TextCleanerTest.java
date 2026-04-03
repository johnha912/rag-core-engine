package com.ragcore.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TextCleaner}.
 */
class TextCleanerTest {

  private TextCleaner cleaner;

  @BeforeEach
  void setUp() {
    cleaner = new TextCleaner();
  }

  @Test
  void testCleanNullReturnsEmpty() {
    assertEquals("", cleaner.clean(null));
  }

  @Test
  void testCleanEmptyStringReturnsEmpty() {
    assertEquals("", cleaner.clean(""));
  }

  @Test
  void testCleanBlankStringReturnsEmpty() {
    assertEquals("", cleaner.clean("     "));
  }

  @Test
  void testRemovesPageOfPattern() {
    String input = "Introduction\nPage 3 of 20\nSome content here";
    String result = cleaner.clean(input);
    assertFalse(result.contains("Page 3 of 20"));
    assertTrue(result.contains("Introduction"));
    assertTrue(result.contains("Some content here"));
  }

  @Test
  void testRemovesPageNumberOnly() {
    String input = "First paragraph\npage 7\nSecond paragraph";
    String result = cleaner.clean(input);
    assertFalse(result.contains("page 7"));
    assertTrue(result.contains("First paragraph"));
    assertTrue(result.contains("Second paragraph"));
  }

  @Test
  void testRemovesDashedPageNumbers() {
    String input = "Hello world\n- 5 -\nGoodbye world";
    String result = cleaner.clean(input);
    assertFalse(result.contains("- 5 -"));
    assertTrue(result.contains("Hello world"));
    assertTrue(result.contains("Goodbye world"));
  }

  @Test
  void testRemovesStandaloneNumbers() {
    String input = "Line one\n42\nLine two";
    String result = cleaner.clean(input);
    assertFalse(result.matches("(?s).*\\n\\s*42\\s*\\n.*"));
    assertTrue(result.contains("Line one"));
    assertTrue(result.contains("Line two"));
  }

  @Test
  void testCollapsesMultipleBlankLines() {
    String input = "Paragraph one\n\n\n\n\nParagraph two";
    String result = cleaner.clean(input);
    assertFalse(result.contains("\n\n\n"), "Should not have 3+ consecutive newlines");
    assertTrue(result.contains("Paragraph one"));
    assertTrue(result.contains("Paragraph two"));
  }

  @Test
  void testCollapsesMultipleSpaces() {
    String input = "Hello     world     test";
    String result = cleaner.clean(input);
    assertEquals("Hello world test", result);
  }

  @Test
  void testTrimsLeadingAndTrailingWhitespace() {
    String input = "   leading and trailing   ";
    String result = cleaner.clean(input);
    assertEquals("leading and trailing", result);
  }

  @Test
  void testTrimsEachLine() {
    String input = "  line one  \n  line two  ";
    String result = cleaner.clean(input);
    assertTrue(result.startsWith("line one"));
    assertTrue(result.endsWith("line two"));
  }

  @Test
  void testNormalTextUnchanged() {
    String input = "This is a clean paragraph with no noise.";
    assertEquals(input, cleaner.clean(input));
  }

  @Test
  void testMixedNoiseRemoval() {
    String input = "Header text\nPage 1 of 5\n\n\n\nActual content   with   spaces\n- 2 -\nMore content";
    String result = cleaner.clean(input);
    assertFalse(result.contains("Page 1 of 5"));
    assertFalse(result.contains("- 2 -"));
    assertTrue(result.contains("Actual content with spaces"));
    assertTrue(result.contains("More content"));
  }
}