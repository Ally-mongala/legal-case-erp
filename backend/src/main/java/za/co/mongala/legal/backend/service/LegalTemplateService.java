package za.co.mongala.legal.backend.service;

import org.apache.poi.xwpf.usermodel.*;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Extraction;
import za.co.mongala.legal.model.LegalDocument;
import za.co.mongala.legal.model.Summary;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Generates editable Microsoft Word (.docx) legal pleadings and notices pre-filled
 * with case metadata, AI-extracted fields, and approved/draft summaries via Apache POI.
 */
public class LegalTemplateService {

    public byte[] generateDocx(String templateType,
                               Case c,
                               List<Extraction> extractions,
                               Summary summary,
                               List<LegalDocument> processedDocs) {
        String type = templateType == null ? "letter_of_demand" : templateType.toLowerCase(Locale.ROOT);
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String caseNo = bestExtraction(extractions, "Case Number", c.getReference());
            String court = bestExtraction(extractions, "Court", c.getCourt()).toUpperCase(Locale.ROOT);
            String plaintiff = bestExtraction(extractions, "Plaintiff", c.getClientName());
            String defendant = bestExtraction(extractions, "Defendant", c.getOpposingParty());
            String amount = bestExtraction(extractions, "Claim Amount",
                    c.getAmountInvolved() > 0 ? String.format(Locale.US, "R %,.2f", c.getAmountInvolved()) : "to be quantified");

            if (type.contains("defend")) {
                buildNoticeToDefend(doc, court, caseNo, plaintiff, defendant, c);
            } else if (type.contains("discovery")) {
                buildDiscoverySchedule(doc, court, caseNo, plaintiff, defendant, c, processedDocs);
            } else {
                buildLetterOfDemand(doc, c, plaintiff, defendant, amount, summary);
            }

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate .docx template", e);
        }
    }

    private void buildNoticeToDefend(XWPFDocument doc, String court, String caseNo,
                                     String plaintiff, String defendant, Case c) {
        heading(doc, "IN THE " + court, 13, ParagraphAlignment.CENTER, true);
        heading(doc, "CASE NO: " + caseNo, 11, ParagraphAlignment.RIGHT, true);
        para(doc, "In the matter between:", false);
        heading(doc, plaintiff.toUpperCase(Locale.ROOT) + "    (Plaintiff / Applicant)", 11, ParagraphAlignment.LEFT, true);
        para(doc, "and", false);
        heading(doc, defendant.toUpperCase(Locale.ROOT) + "    (Defendant / Respondent)", 11, ParagraphAlignment.LEFT, true);

        heading(doc, "NOTICE OF INTENTION TO DEFEND / OPPOSE", 13, ParagraphAlignment.CENTER, true);
        para(doc, "BE PLEASED TO TAKE NOTICE that the above-named party hereby gives notice of intention to defend / oppose this action.", false);
        para(doc, "AND TAKE FURTHER NOTICE that the undersigned attorneys have been appointed of record and will accept service of all notices, pleadings and documents at the address set out below:", false);
        para(doc, "MONGALA ATTORNEYS INC.\n14 Republic Road, Randburg, Gauteng\nRef: " + c.getReference() + "  |  Email: ally@mongalalaw.co.za", true);
        para(doc, "DATED at RANDBURG on this " + LocalDate.now() + ".", false);
        para(doc, "\n____________________________________\nMONGALA ATTORNEYS INC.\nAttorneys of Record", false);
    }

    private void buildLetterOfDemand(XWPFDocument doc, Case c, String client,
                                     String opposing, String amount, Summary summary) {
        heading(doc, "MONGALA ATTORNEYS INC.", 15, ParagraphAlignment.LEFT, true);
        para(doc, "Legal Practitioners  |  14 Republic Road, Randburg, Gauteng  |  Tel: (011) 555-0194", false);
        para(doc, "Date: " + LocalDate.now() + "      |      Our Ref: " + c.getReference() + "      |      WITHOUT PREJUDICE", true);
        para(doc, "TO: " + opposing, true);
        heading(doc, "RE: FORMAL LETTER OF DEMAND — " + client.toUpperCase(Locale.ROOT) + " / " + opposing.toUpperCase(Locale.ROOT),
                11, ParagraphAlignment.LEFT, true);

        para(doc, "1. We act on behalf of our client, " + client + ", on whose instructions we address this formal demand to you.", false);
        String background = (summary != null && summary.getParagraph1() != null && !summary.getParagraph1().isBlank())
                ? summary.getParagraph1()
                : (c.getDescription() == null || c.getDescription().isBlank() ? c.getTitle() : c.getDescription());
        para(doc, "2. Background to claim: " + background, false);
        para(doc, "3. Our instructions are that you are indebted / liable to our client in the sum of " + amount + " arising from the above matter (" + c.getMatterType() + ").", false);
        para(doc, "4. DEMAND IS HEREBY MADE for payment of " + amount + " into our firm's Trust Account (Mongala Attorneys Trust, FNB Acc 62891044512, Branch 250655, Ref: " + c.getReference() + ") within FOURTEEN (14) calendar days of receipt hereof, failing which summons will be issued out of the " + c.getCourt() + " without further notice, including a prayer for interest and costs on an attorney-and-client scale.", false);
        para(doc, "\nYours faithfully,\n\nMONGALA ATTORNEYS INC.\nPer: A. Mongala", true);
    }

    private void buildDiscoverySchedule(XWPFDocument doc, String court, String caseNo,
                                        String plaintiff, String defendant, Case c,
                                        List<LegalDocument> docs) {
        heading(doc, "IN THE " + court, 13, ParagraphAlignment.CENTER, true);
        heading(doc, "CASE NO: " + caseNo + "   |   MATTER REF: " + c.getReference(), 10, ParagraphAlignment.RIGHT, true);
        heading(doc, "RULE 35 DISCOVERY AFFIDAVIT — FIRST SCHEDULE", 13, ParagraphAlignment.CENTER, true);
        para(doc, "Matter: " + plaintiff + " v " + defendant, true);
        para(doc, "Documents in the possession of our client relating to the matters in question in this action:", false);

        XWPFTable tbl = doc.createTable(Math.max(2, docs.size() + 1), 4);
        tbl.getRow(0).getCell(0).setText("Item #");
        tbl.getRow(0).getCell(1).setText("Document Description / Filename");
        tbl.getRow(0).getCell(2).setText("Format");
        tbl.getRow(0).getCell(3).setText("Date Uploaded");

        if (docs.isEmpty()) {
            tbl.getRow(1).getCell(0).setText("1");
            tbl.getRow(1).getCell(1).setText("No processed discovery documents filed yet");
            tbl.getRow(1).getCell(2).setText("—");
            tbl.getRow(1).getCell(3).setText(String.valueOf(LocalDate.now()));
        } else {
            for (int i = 0; i < docs.size(); i++) {
                LegalDocument d = docs.get(i);
                XWPFTableRow r = tbl.getRow(i + 1);
                r.getCell(0).setText(String.valueOf(i + 1));
                r.getCell(1).setText(d.getFileName());
                r.getCell(2).setText(d.getDocType());
                r.getCell(3).setText(d.getUploadedAt() == null ? "" : d.getUploadedAt().toLocalDate().toString());
            }
        }
        para(doc, "\nCompiled via LegalCase ERP on " + LocalDate.now() + " by Mongala Attorneys Inc.", false);
    }

    private static void heading(XWPFDocument doc, String text, int pt, ParagraphAlignment align, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        p.setSpacingAfter(140);
        XWPFRun r = p.createRun();
        r.setFontFamily("Calibri");
        r.setFontSize(pt);
        r.setBold(bold);
        r.setText(text);
    }

    private static void para(XWPFDocument doc, String text, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(120);
        String[] lines = (text == null ? "" : text).split("\n");
        for (int i = 0; i < lines.length; i++) {
            XWPFRun r = p.createRun();
            r.setFontFamily("Calibri");
            r.setFontSize(11);
            r.setBold(bold);
            r.setText(lines[i]);
            if (i < lines.length - 1) r.addBreak();
        }
    }

    private static String bestExtraction(List<Extraction> list, String field, String fallback) {
        if (list != null) {
            for (Extraction e : list) {
                if (field.equalsIgnoreCase(e.getFieldName()) && e.getFieldValue() != null && !e.getFieldValue().isBlank()) {
                    return e.getFieldValue();
                }
            }
        }
        return fallback == null || fallback.isBlank() ? "—" : fallback;
    }
}
