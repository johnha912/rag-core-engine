package com.ragcore.adapter;

import com.ragcore.adapter.domain.legal.LegalTextCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegalTextCleanerTest {

  private LegalTextCleaner cleaner;

  @BeforeEach
  void setUp() {
    cleaner = new LegalTextCleaner();
  }

  @Test
  void clean_nullInput_returnsEmptyString() {
    assertEquals("", cleaner.clean(null));
  }

  @Test
  void clean_blankInput_returnsEmptyString() {
    assertEquals("", cleaner.clean("   \n  \n  "));
  }

  @Test
  void clean_standalonePageNumber_removed() {
    String input = "Section 1. Definitions\n\n42\n\nTenant shall pay rent.";
    String result = cleaner.clean(input);
    assertFalse(result.contains("\n42\n"), "Standalone page number should be removed");
    assertTrue(result.contains("Section 1. Definitions"));
    assertTrue(result.contains("Tenant shall pay rent."));
  }

  @Test
  void clean_pageXofY_removed() {
    String input = "Section 2. Rent\n\nPage 3 of 10\n\nRent is due on the first.";
    String result = cleaner.clean(input);
    assertFalse(result.toLowerCase().contains("page 3 of 10"));
    assertTrue(result.contains("Rent is due on the first."));
  }

  @Test
  void clean_dashWrappedPageNumber_removed() {
    String input = "Article I. Term\n\n- 5 -\n\nThe lease term is one year.";
    String result = cleaner.clean(input);
    assertFalse(result.contains("- 5 -"));
    assertTrue(result.contains("The lease term is one year."));
  }

  @Test
  void clean_sectionExtraWhitespace_normalized() {
    String input = "Section  1.  Definitions\nArticle  II  Rent";
    String result = cleaner.clean(input);
    assertFalse(result.contains("Section  "), "Double space after Section should be normalized");
    assertFalse(result.contains("Article  "), "Double space after Article should be normalized");
  }

  @Test
  void clean_excessiveBlankLines_collapsed() {
    String input = "Line one.\n\n\n\n\nLine two.";
    String result = cleaner.clean(input);
    assertFalse(result.contains("\n\n\n"), "3+ blank lines should be collapsed");
    assertTrue(result.contains("Line one."));
    assertTrue(result.contains("Line two."));
  }

  @Test
  void clean_normalContent_preserved() {
    String input = "Section 1. Definitions\nThe term \"Landlord\" means the property owner.";
    String result = cleaner.clean(input);
    assertTrue(result.contains("Section 1. Definitions"));
    assertTrue(result.contains("The term"));
  }
}
