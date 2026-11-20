package za.co.mongala.legal.backend.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.model.Extraction;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the AI/NLP extraction engine (FR-02, ETR-01, ETR-03).
 *
 * These tests verify the emerging-technology component in isolation: that it
 * recognises the legally significant entities, and that every field it returns
 * carries the confidence score, source span and page number required for
 * explainability.
 *
 * Test case IDs map to the A3 Q5.1 test summary table.
 */
class ExtractionEngineTest {

    /** A realistic single-page South African high-court pleading. */
    private static final String PLEADING = """
            IN THE HIGH COURT OF SOUTH AFRICA
            GAUTENG DIVISION, PRETORIA

            Case Number: GP/PTA/2026/4471

            In the matter between:

            Thabo Mokoena                                          Plaintiff

            and

            Sizwe Dlamini                                          Defendant

            PARTICULARS OF CLAIM

            1. The Plaintiff is Thabo Mokoena, an adult male residing in Pretoria.
            2. On 14 March 2026 the Defendant breached the written agreement concluded
               between the parties on 3 January 2026.
            3. As a result of the said breach the Plaintiff has suffered damages in the
               sum of R 250 000.00 which the Defendant refuses to pay.
            4. The matter is set down for hearing on 12 October 2026 before Judge Mahlangu.

            Attorneys for the Plaintiff: Mongala Attorneys Incorporated
            """;

    private static Optional<Extraction> field(List<Extraction> all, String name) {
        return all.stream().filter(e -> e.getFieldName().equalsIgnoreCase(name)).findFirst();
    }

    @Test
    @DisplayName("TC-U01: extracts the case number from a pleading")
    void extractsCaseNumber() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING));
        Optional<Extraction> caseNo = field(result, "Case Number");

        assertTrue(caseNo.isPresent(), "case number should be extracted");
        assertTrue(caseNo.get().getFieldValue().contains("GP/PTA/2026/4471"),
                "expected GP/PTA/2026/4471 but was " + caseNo.get().getFieldValue());
    }

    @Test
    @DisplayName("TC-U02: extracts plaintiff and defendant as distinct parties")
    void extractsParties() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING));

        boolean plaintiff = result.stream()
                .anyMatch(e -> e.getFieldValue() != null && e.getFieldValue().contains("Thabo Mokoena"));
        boolean defendant = result.stream()
                .anyMatch(e -> e.getFieldValue() != null && e.getFieldValue().contains("Sizwe Dlamini"));

        assertTrue(plaintiff, "plaintiff Thabo Mokoena should be extracted");
        assertTrue(defendant, "defendant Sizwe Dlamini should be extracted");
    }

    @Test
    @DisplayName("TC-U03: extracts calendar dates written in long form")
    void extractsDates() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING));

        long dateFields = result.stream()
                .filter(e -> e.getFieldName().toLowerCase().contains("date"))
                .count();

        assertTrue(dateFields > 0, "at least one date field should be extracted");
    }

    @Test
    @DisplayName("TC-U04: every extraction carries confidence, span and page (ETR-03 explainability)")
    void everyExtractionIsExplainable() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING));
        assertFalse(result.isEmpty(), "engine should return extractions for a valid pleading");

        for (Extraction e : result) {
            assertAll("explainability contract for " + e.getFieldName(),
                    () -> assertTrue(e.getConfidence() >= 0.0 && e.getConfidence() <= 1.0,
                            "confidence must be within [0,1] but was " + e.getConfidence()),
                    () -> assertNotNull(e.getSpanText(), "span text must not be null"),
                    () -> assertFalse(e.getSpanText().isBlank(), "span text must not be blank"),
                    () -> assertTrue(e.getPageNumber() >= 1,
                            "page number must be 1-based but was " + e.getPageNumber()));
        }
    }

    @Test
    @DisplayName("TC-U05: assigns the correct page number in a multi-page document")
    void assignsCorrectPageNumber() {
        String page1 = "IN THE HIGH COURT OF SOUTH AFRICA\nGAUTENG DIVISION, PRETORIA\n";
        String page2 = "Case Number: GP/PTA/2026/9988\nThe hearing is set for 5 November 2026.\n";

        List<Extraction> result = ExtractionEngine.extract(List.of(page1, page2));
        Optional<Extraction> caseNo = field(result, "Case Number");

        assertTrue(caseNo.isPresent(), "case number on page 2 should be extracted");
        assertEquals(2, caseNo.get().getPageNumber(),
                "case number appears on page 2 and must be reported as page 2");
    }

    @Test
    @DisplayName("TC-U06: returns no case fields for a document with no legal content")
    void nonLegalDocumentYieldsNoCaseFields() {
        String groceryList = """
                Shopping list for Saturday
                milk, bread, eggs, washing powder
                remember to collect the dry cleaning
                """;

        List<Extraction> result = ExtractionEngine.extract(List.of(groceryList));

        assertTrue(field(result, "Case Number").isEmpty(),
                "a grocery list must not yield a case number");
        assertTrue(field(result, "Court").isEmpty(),
                "a grocery list must not yield a court");
    }

    @Test
    @DisplayName("TC-U07: handles empty input without throwing")
    void handlesEmptyInput() {
        assertDoesNotThrow(() -> {
            List<Extraction> empty = ExtractionEngine.extract(List.of());
            assertNotNull(empty, "must return a list, not null");
            assertTrue(empty.isEmpty(), "no pages means no extractions");
        });

        assertDoesNotThrow(() -> ExtractionEngine.extract(List.of("")),
                "a blank page must not throw");
    }

    @Test
    @DisplayName("TC-U08: extracts the monetary claim amount")
    void extractsClaimAmount() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING));

        boolean hasAmount = result.stream()
                .anyMatch(e -> e.getFieldValue() != null && e.getFieldValue().contains("250"));

        assertTrue(hasAmount, "claim amount R 250 000.00 should be extracted");
    }

    @Test
    @DisplayName("TC-U09: does not return duplicate entries for the same field")
    void noDuplicateFields() {
        List<Extraction> result = ExtractionEngine.extract(List.of(PLEADING, PLEADING));

        long distinct = result.stream().map(Extraction::getFieldName).distinct().count();
        assertEquals(distinct, result.size(),
                "the same field must not be reported twice even when text repeats");
    }
}
