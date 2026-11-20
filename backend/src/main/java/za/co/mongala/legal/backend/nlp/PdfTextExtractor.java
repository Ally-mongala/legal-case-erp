package za.co.mongala.legal.backend.nlp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts raw text from an uploaded legal PDF, page by page, using Apache
 * PDFBox. Real implementation of FR1/FR2 ("upload a legal PDF and store it",
 * "automatically extract case dates, parties and key facts").
 *
 * Page-by-page extraction is what lets the extraction engine report the exact
 * page number of every field (explainability, ETR2).
 */
public final class PdfTextExtractor {

    private PdfTextExtractor() {}

    public record PdfContent(List<String> pages, String fullText) {}

    public static PdfContent extract(File file) {
        try (PDDocument pdf = PDDocument.load(file)) {
            if (pdf.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDFs are not supported in this build.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>();
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(stripper.getText(pdf));
            }
            return new PdfContent(pages, String.join("\n", pages));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read PDF text: " + e.getMessage(), e);
        }
    }
}
