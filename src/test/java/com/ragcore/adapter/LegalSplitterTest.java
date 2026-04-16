package com.ragcore.adapter;

import com.ragcore.adapter.domain.legal.LegalSplitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegalSplitterTest {

  private LegalSplitter splitter;

  @BeforeEach
  void setUp() {
    splitter = new LegalSplitter();
  }

  @Test
  void split_nullInput_returnsEmptyList() {
    assertTrue(splitter.split(null).isEmpty());
  }

  @Test
  void split_blankInput_returnsEmptyList() {
    assertTrue(splitter.split("   ").isEmpty());
  }

  @Test
  void split_sectionHeadings_splitsCorrectly() {
    String text = """
        Section 1. Definitions
        The term "Tenant" means the person renting.

        Section 2. Rent
        Rent is due on the first of each month.
        """;

    List<String> chunks = splitter.split(text);
    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).contains("Section 1. Definitions"));
    assertTrue(chunks.get(1).contains("Section 2. Rent"));
  }

  @Test
  void split_articleHeadings_splitsCorrectly() {
    String text = """
        Article I. General Terms
        This agreement governs the rental.

        Article II. Payment
        All payments shall be made on time.
        """;

    List<String> chunks = splitter.split(text);
    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).contains("Article I"));
    assertTrue(chunks.get(1).contains("Article II"));
  }

  @Test
  void split_californiaCodeStyle_splitsCorrectly() {
    String text = """
        § 1940. Application of chapter
        This chapter applies to all residential tenancies.

        § 1941. Landlord duty to repair
        The landlord shall maintain the premises.
        """;

    List<String> chunks = splitter.split(text);
    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).contains("§ 1940"));
    assertTrue(chunks.get(1).contains("§ 1941"));
  }

  @Test
  void split_numberedAllCapsTitle_splitsCorrectly() {
    String text = """
        1. DEFINITIONS
        The following terms apply throughout this agreement.

        2. TERM OF TENANCY
        The tenancy shall begin on the first day of the month.
        """;

    List<String> chunks = splitter.split(text);
    assertEquals(2, chunks.size());
    assertTrue(chunks.get(0).contains("1. DEFINITIONS"));
    assertTrue(chunks.get(1).contains("2. TERM OF TENANCY"));
  }

  @Test
  void split_noHeadings_fallsBackToParagraphs() {
    String text = "This is a plain paragraph.\n\nThis is another paragraph.";
    List<String> chunks = splitter.split(text);
    assertEquals(2, chunks.size());
  }

  @Test
  void split_preambleBeforeFirstSection_includedAsOwnChunk() {
    String text = "CALIFORNIA RESIDENTIAL LEASE AGREEMENT\nThis agreement is entered into.\n\nSection 1. Term\nOne year.";
    List<String> chunks = splitter.split(text);
    assertTrue(chunks.size() >= 2);
    assertTrue(chunks.get(0).contains("CALIFORNIA RESIDENTIAL LEASE AGREEMENT"));
    assertTrue(chunks.stream().anyMatch(c -> c.contains("Section 1. Term")));
  }

  @Test
  void extractSectionNumber_sectionKeyword_returnsNumber() {
    assertEquals("1", splitter.extractSectionNumber("Section 1. Definitions"));
    assertEquals("1.1", splitter.extractSectionNumber("Section 1.1 Sub-clause"));
  }

  @Test
  void extractSectionNumber_californiaCode_returnsNumber() {
    assertEquals("1940", splitter.extractSectionNumber("§ 1940. Application"));
    assertEquals("1940.5", splitter.extractSectionNumber("§ 1940.5 Habitability"));
  }

  @Test
  void extractSectionNumber_articleRoman_returnsRoman() {
    assertEquals("I", splitter.extractSectionNumber("Article I. General Terms"));
    assertEquals("IV", splitter.extractSectionNumber("Article IV. Termination"));
  }

  @Test
  void extractSectionNumber_numberedTitle_returnsNumber() {
    assertEquals("3", splitter.extractSectionNumber("3. RENT PAYMENT"));
  }

  @Test
  void extractSectionNumber_noMatch_returnsEmpty() {
    assertEquals("", splitter.extractSectionNumber("Random text without a section marker."));
    assertEquals("", splitter.extractSectionNumber(null));
  }
}
