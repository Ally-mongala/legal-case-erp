package za.co.mongala.legal.backend.nlp;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts raw text from an uploaded legal document (FR1: PDF *or* Word) using
 * Apache PDFBox + Apache POI. Works on in-memory decrypted bytes (FR11 -
 * documents are encrypted at rest, decrypted only for processing).
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {}

    /** ocrPages = number of pages whose text came from OCR rather than a text layer. */
    public record PdfContent(List<String> pages, String fullText, int ocrPages) {
        public PdfContent(List<String> pages, String fullText) { this(pages, fullText, 0); }
    }

    public static PdfContent extract(byte[] bytes, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".docx")) {
            return extractDocx(bytes);
        } else if (lower.endsWith(".doc")) {
            return extractDoc(bytes);
        } else if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        } else {
            // plain text fallback
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return new PdfContent(List.of(text), text);
        }
    }

    private static PdfContent extractPdf(byte[] bytes) {
        try (PDDocument pdf = PDDocument.load(bytes)) {
            if (pdf.isEncrypted()) {
                throw new IllegalArgumentException("Encrypted PDFs are not supported in this build.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = null;
            List<String> pages = new ArrayList<>();
            int ocrPages = 0;
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                // scanned page: no text layer, so read the rendered image with OCR
                if (text.isBlank() && OcrService.isAvailable()) {
                    if (renderer == null) renderer = new PDFRenderer(pdf);
                    String ocr = OcrService.recognisePage(renderer, i - 1);
                    if (!ocr.isBlank()) {
                        text = ocr;
                        ocrPages++;
                    }
                }
                pages.add(text);
            }
            return new PdfContent(pages, String.join("\n", pages), ocrPages);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read PDF text: " + e.getMessage(), e);
        }
    }

    private static PdfContent extractDocx(byte[] bytes) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    sb.append(t).append("\n");
                }
            }
            String text = sb.toString();
            return new PdfContent(List.of(text), text);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read Word (.docx) text: " + e.getMessage(), e);
        }
    }

    private static PdfContent extractDoc(byte[] bytes) {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor we = new WordExtractor(doc)) {
            String text = we.getText();
            return new PdfContent(List.of(text), text);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read Word (.doc) text: " + e.getMessage(), e);
        }
    }
}
