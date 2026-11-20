package za.co.mongala.legal.backend.nlp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Scanned, image-only PDFs must be read by OCR instead of failing. The test builds a
 * real image-only PDF (text drawn as pixels, no text layer) and checks that the words
 * come back. It is skipped automatically when the Tesseract engine is not installed.
 */
class OcrServiceTest {

    private static byte[] imageOnlyPdf(String line) throws Exception {
        BufferedImage img = new BufferedImage(2100, 500, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 72));
        g.drawString(line, 60, 260);
        g.dispose();

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(595, 842));
            doc.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 20, 300, 555, 132);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @DisplayName("OCR-1: a scanned, image-only PDF page is read by Tesseract")
    void scannedPageIsRead() throws Exception {
        assumeTrue(OcrService.isAvailable(), "Tesseract not installed - OCR test skipped");

        byte[] pdf = imageOnlyPdf("CASE NUMBER: GP/JHB/2026/000841");
        DocumentTextExtractor.PdfContent content = DocumentTextExtractor.extract(pdf, "scan.pdf");

        assertEquals(1, content.ocrPages(), "the page had no text layer, so OCR must have run");
        String text = content.fullText().toUpperCase().replaceAll("\\s+", "");
        assertTrue(text.contains("000841"), "OCR text was: " + content.fullText());
        assertTrue(text.contains("2026"), "OCR text was: " + content.fullText());
    }

    @Test
    @DisplayName("OCR-2: a PDF with a real text layer is not sent to OCR")
    void textLayerIsNotOcred() throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Case number GP/JHB/2026/000841");
                cs.endText();
            }
            doc.save(out);
            DocumentTextExtractor.PdfContent content = DocumentTextExtractor.extract(out.toByteArray(), "text.pdf");
            assertEquals(0, content.ocrPages());
            assertTrue(content.fullText().contains("GP/JHB/2026/000841"));
        }
    }
}
