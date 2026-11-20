package za.co.mongala.legal.backend.nlp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.model.Extraction;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the defect found while testing OCR on a scanned pleading: the hearing
 * date was taken from the collision date (2 November 2026), because a court heading in
 * the same block counted as a hearing cue. The hearing date is the date that is set down
 * for hearing, and that must win.
 */
class HearingDateRegressionTest {

    /** OCR-style text: lines run together with no full stop after the court heading. */
    private static final String OCR_STYLE = """
            IN THE HIGH COURT OF SOUTH AFRICA GAUTENG DIVISION JOHANNESBURG
            CASE NUMBER: GP/JHB/2026/000841
            Plaintiff: Thabo Mokoena
            Defendant: Siyaya Transport (Pty) Ltd
            The plaintiff claims damages arising from the collision on 2 November 2026.
            The defendant has filed a plea dated 16 November 2026.
            Hearing set down for 14 December 2026.
            """;

    private static String hearingDate(List<Extraction> fields) {
        return fields.stream().filter(e -> e.getFieldName().equals("Hearing Date"))
                .findFirst().map(Extraction::getFieldValue).orElse(null);
    }

    @Test
    @DisplayName("HD-1: hearing date is the date set down for hearing, not the collision date")
    void hearingDateIsNotTheCollisionDate() {
        List<Extraction> fields = ExtractionEngine.extract(List.of(OCR_STYLE));
        assertEquals("2026-12-14", hearingDate(fields));
    }

    @Test
    @DisplayName("HD-2: without a hearing cue, the latest procedural date is still used")
    void latestDateFallbackStillWorks() {
        String text = "The plaintiff claims damages from the collision on 2 November 2026. "
                + "The plea is dated 16 November 2026.";
        List<Extraction> fields = ExtractionEngine.extract(List.of(text));
        assertEquals("2026-11-16", hearingDate(fields));
    }
}
