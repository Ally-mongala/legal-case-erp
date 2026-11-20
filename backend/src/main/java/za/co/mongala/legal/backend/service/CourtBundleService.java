package za.co.mongala.legal.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.LegalDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles an indexed, court-paginated Hearing Bundle PDF for a matter:
 *  - Page 1: High Court / Magistrate's Court Cover Sheet & Paginated Table of Contents
 *  - Following pages: decrypts uploaded PDFs in memory (or renders extracted text for Word/seeded files)
 *  - Stamps "Bundle Page X of Y" in the top-right corner of every page
 */
public class CourtBundleService {

    private record IndexEntry(int tabNo, String fileName, String uploadedDate, int startBundlePage, int pageCount) {}

    private final DocumentService documents;
    private final EncryptionUtil encryption;
    private final Path uploadDir;

    public CourtBundleService(DocumentService documents, EncryptionUtil encryption, Path uploadDir) {
        this.documents = documents;
        this.encryption = encryption;
        this.uploadDir = uploadDir;
    }

    public byte[] compileBundlePdf(Case legalCase) {
        List<LegalDocument> approvedDocs = documents.byCase(legalCase.getId()).stream()
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED)
                .toList();

        List<PDDocument> openSources = new ArrayList<>();
        try (PDDocument bundle = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Reserve Page 1 for Cover Sheet & Index
            PDPage coverPage = new PDPage(PDRectangle.A4);
            bundle.addPage(coverPage);

            List<IndexEntry> index = new ArrayList<>();
            int tab = 1;

            for (LegalDocument d : approvedDocs) {
                int startPage = bundle.getNumberOfPages() + 1; // 1-based bundle page
                int added = appendDocumentPages(bundle, openSources, d);
                String upDate = d.getUploadedAt() == null ? "" : d.getUploadedAt().toLocalDate().toString();
                index.add(new IndexEntry(tab++, d.getFileName(), upDate, startPage, added));
            }

            // Draw Cover Sheet & Bundle Index on Page 1 now that exact page numbers are known
            drawCoverAndIndex(bundle, coverPage, legalCase, index);

            // Stamp "Bundle Page X of Y" across every page in the bundle
            int total = bundle.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage p = bundle.getPage(i);
                try (PDPageContentStream cs = new PDPageContentStream(
                        bundle, p, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    cs.setNonStrokingColor(180, 35, 35);
                    write(cs, PDType1Font.HELVETICA_BOLD, 9.5f, 445, 820,
                            "Bundle Page " + (i + 1) + " of " + total);
                }
            }

            bundle.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not compile court bundle PDF", e);
        } finally {
            for (PDDocument src : openSources) {
                try { src.close(); } catch (Exception ignored) {}
            }
        }
    }

    private int appendDocumentPages(PDDocument bundle, List<PDDocument> openSources, LegalDocument d) throws IOException {
        Path encFile = uploadDir.resolve(d.getStoragePath() == null ? "" : d.getStoragePath());
        if ("PDF".equalsIgnoreCase(d.getDocType()) && Files.exists(encFile)) {
            try {
                String b64 = Files.readString(encFile, StandardCharsets.UTF_8);
                byte[] pdfBytes = encryption.decrypt(b64);
                PDDocument src = PDDocument.load(pdfBytes);
                openSources.add(src);
                int count = src.getNumberOfPages();
                for (int i = 0; i < count; i++) {
                    bundle.importPage(src.getPage(i));
                }
                if (count > 0) return count;
            } catch (Exception ignored) {
                // Fall back to rendering stored text if raw PDF import fails
            }
        }

        // Render extracted/seeded text cleanly onto A4 pages
        String text = documents.getFullText(d.getId());
        if (text == null || text.isBlank()) {
            text = "Document: " + d.getFileName() + "\nUploaded by: " + d.getUploadedBy()
                    + "\n(Processed pleading record included in court bundle.)";
        }
        List<String> lines = wrapLines(text, 84);
        int linesPerPage = 42;
        int pagesAdded = 0;
        for (int offset = 0; offset < lines.size(); offset += linesPerPage) {
            PDPage page = new PDPage(PDRectangle.A4);
            bundle.addPage(page);
            pagesAdded++;
            try (PDPageContentStream cs = new PDPageContentStream(bundle, page)) {
                cs.setNonStrokingColor(22, 35, 60);
                write(cs, PDType1Font.HELVETICA_BOLD, 10.5f, 48, 790,
                        "BUNDLE EXHIBIT: " + safe(d.getFileName()));
                cs.setStrokingColor(200, 210, 225);
                cs.moveTo(48, 782);
                cs.lineTo(547, 782);
                cs.stroke();

                cs.setNonStrokingColor(25, 30, 40);
                float y = 762f;
                int end = Math.min(lines.size(), offset + linesPerPage);
                for (int i = offset; i < end; i++) {
                    write(cs, PDType1Font.HELVETICA, 9.5f, 48, y, lines.get(i));
                    y -= 15.5f;
                }
            }
        }
        return Math.max(1, pagesAdded);
    }

    private void drawCoverAndIndex(PDDocument bundle, PDPage page, Case c, List<IndexEntry> index) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(bundle, page)) {
            cs.setNonStrokingColor(22, 35, 60);
            cs.addRect(0, 750, PDRectangle.A4.getWidth(), 92);
            cs.fill();

            cs.setNonStrokingColor(255, 255, 255);
            write(cs, PDType1Font.HELVETICA_BOLD, 15f, 48, 800,
                    "IN THE " + safe(c.getCourt()).toUpperCase());
            write(cs, PDType1Font.HELVETICA_BOLD, 12f, 48, 780,
                    "INDEXED COURT HEARING BUNDLE  |  REF: " + safe(c.getReference()));
            write(cs, PDType1Font.HELVETICA, 9.5f, 48, 762,
                    "Compiled on " + LocalDate.now() + " by Mongala Attorneys Inc.");

            cs.setNonStrokingColor(20, 25, 35);
            write(cs, PDType1Font.HELVETICA_BOLD, 11.5f, 48, 722, "MATTER: " + safe(c.getTitle()));
            write(cs, PDType1Font.HELVETICA, 10f, 48, 705,
                    "Plaintiff / Client: " + safe(c.getClientName()) + "    |    Opposing Party: " + safe(c.getOpposingParty()));
            write(cs, PDType1Font.HELVETICA, 10f, 48, 689,
                    "Practice Area: " + safe(c.getMatterType()) + "    |    Total Documents Indexed: " + index.size());

            float y = 655f;
            cs.setNonStrokingColor(232, 240, 251);
            cs.addRect(48, y - 5, 499, 19);
            cs.fill();
            cs.setNonStrokingColor(22, 35, 60);
            write(cs, PDType1Font.HELVETICA_BOLD, 9f, 54, y, "TAB");
            write(cs, PDType1Font.HELVETICA_BOLD, 9f, 92, y, "DOCUMENT DESCRIPTION");
            write(cs, PDType1Font.HELVETICA_BOLD, 9f, 365, y, "FILED DATE");
            write(cs, PDType1Font.HELVETICA_BOLD, 9f, 450, y, "PAGES");

            cs.setNonStrokingColor(20, 25, 35);
            y -= 22f;
            if (index.isEmpty()) {
                write(cs, PDType1Font.HELVETICA_OBLIQUE, 9.5f, 54, y,
                        "No processed pleadings on file yet. Upload and validate documents to populate bundle tabs.");
            } else {
                for (IndexEntry e : index) {
                    if (y < 90f) break;
                    int endPage = e.startBundlePage() + e.pageCount() - 1;
                    String range = e.startBundlePage() == endPage
                            ? "p. " + e.startBundlePage()
                            : "pp. " + e.startBundlePage() + "-" + endPage;
                    write(cs, PDType1Font.HELVETICA_BOLD, 9.5f, 54, y, String.valueOf(e.tabNo()));
                    write(cs, PDType1Font.HELVETICA, 9.5f, 92, y, truncate(safe(e.fileName()), 48));
                    write(cs, PDType1Font.HELVETICA, 9f, 365, y, e.uploadedDate());
                    write(cs, PDType1Font.HELVETICA_BOLD, 9.5f, 450, y, range);
                    y -= 18f;
                }
            }
        }
    }

    private static List<String> wrapLines(String text, int width) {
        List<String> out = new ArrayList<>();
        for (String rawLine : text.replace("\r", "").split("\n")) {
            if (rawLine.isBlank()) {
                out.add("");
                continue;
            }
            String line = rawLine.trim();
            while (line.length() > width) {
                int cut = line.lastIndexOf(' ', width);
                if (cut <= 0) cut = width;
                out.add(line.substring(0, cut));
                line = line.substring(cut).trim();
            }
            out.add(line);
        }
        return out;
    }

    private static void write(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        String ascii = (text == null ? "" : text).replaceAll("[^\\x20-\\x7E]", " ");
        cs.showText(ascii);
        cs.endText();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
