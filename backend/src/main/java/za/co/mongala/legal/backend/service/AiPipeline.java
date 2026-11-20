package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.backend.nlp.ExtractionEngine;
import za.co.mongala.legal.backend.nlp.DocumentTextExtractor;
import za.co.mongala.legal.backend.nlp.OcrService;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.backend.nlp.Summarizer;
import za.co.mongala.legal.model.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Worker-based document processing pipeline (Assignment 2 section 12).
 *
 * Uploading a document enqueues a job; a worker thread then:
 *   1. extracts the raw text page-by-page (PDFBox)
 *   2. runs the extraction engine  -> structured fields with confidence + span + page
 *   3. derives deadlines from extracted dates (FR4)
 *   4. runs the extractive summariser -> 3-paragraph draft (FR3)
 *   5. persists everything and records audit entries (FR10)
 * Failures leave the document in FAILED state so the UI can offer "Retry AI" (FR7).
 */
public class AiPipeline {

    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final ExtractionService extractionService;
    private final SummaryService summaryService;
    private final DeadlineExtractor deadlineExtractor;
    private final DocumentService documentService;
    private final CaseService caseService;
    private final AuditService audit;
    private final Path uploadDir;
    private final EncryptionUtil encryption;

    public AiPipeline(ExtractionService extractionService, SummaryService summaryService,
                      DeadlineExtractor deadlineExtractor, DocumentService documentService,
                      CaseService caseService, AuditService audit, Path uploadDir,
                      EncryptionUtil encryption) {
        this.extractionService = extractionService;
        this.summaryService = summaryService;
        this.deadlineExtractor = deadlineExtractor;
        this.documentService = documentService;
        this.caseService = caseService;
        this.audit = audit;
        this.uploadDir = uploadDir;
        this.encryption = encryption;
    }

    /** Queues a freshly-uploaded document for AI processing (FR1 -> FR2/FR3/FR4). */
    public void enqueue(LegalDocument doc, Case legalCase) {
        workers.submit(() -> {
            try {
                processDocument(doc, legalCase);
            } catch (Exception e) {
                fail(doc, e);
            }
        });
    }

    /** Re-runs AI processing on a FAILED document (FR7) - stale results removed first. */
    public void retry(LegalDocument doc, Case legalCase) {
        documentService.setStatus(doc.getId(), LegalDocument.ProcessingStatus.PROCESSING);
        extractionService.deleteForDocument(doc.getId());
        summaryService.deleteForDocument(doc.getId());
        workers.submit(() -> {
            try {
                processDocument(doc, legalCase);
            } catch (Exception e) {
                fail(doc, e);
            }
        });
    }

    private void processDocument(LegalDocument doc, Case legalCase) throws Exception {
        documentService.setStatus(doc.getId(), LegalDocument.ProcessingStatus.PROCESSING);
        Path file = uploadDir.resolve(doc.getStoragePath()).normalize();
        if (!Files.exists(file)) {
            throw new IllegalStateException("Stored file not found: " + file);
        }
        // FR11: decrypt the stored file (AES-256-GCM) in memory before processing
        String b64 = Files.readString(file);
        byte[] bytes = encryption.decrypt(b64);
        DocumentTextExtractor.PdfContent content = DocumentTextExtractor.extract(bytes, doc.getFileName());
        if (content.fullText().isBlank()) {
            throw new IllegalStateException(OcrService.isAvailable()
                    ? "No extractable text found, even after OCR. Check the scan quality, then Retry."
                    : "No extractable text found (scanned image). OCR engine is not installed on this server.");
        }
        if (content.ocrPages() > 0) {
            audit.info("AI Worker", "OCR_APPLIED",
                    "Document " + doc.getFileName() + ": " + content.ocrPages()
                            + " scanned page(s) read with OCR (Tesseract)");
        }

        // 0. cache the full extracted text so it can be read back in full (lawyer requirement)
        documentService.setFullText(doc.getId(), content.fullText());

        // 1. field extraction with confidence + span + page (FR2 / ETR2 / ETR5)
        List<Extraction> extractions = ExtractionEngine.extract(content.pages());
        extractionService.insertAll(doc.getId(), extractions);

        // 1b. AI validation: is this document actually about this case?
        //     If it is irrelevant or has conflicting information, highlight it
        //     (extractions stay visible) and BLOCK the document.
        String rejection = rejectionReason(extractions, content.fullText(), legalCase);
        if (rejection != null) {
            documentService.setRejected(doc.getId(), rejection);
            audit.warning("AI Worker", "DOCUMENT_REJECTED",
                    doc.getFileName() + " BLOCKED: " + rejection
                            + " (extracted fields kept for review)");
            return;
        }

        // 2. deadlines from extracted dates (FR4 / US5)
        deadlineExtractor.apply(doc.getId(), doc.getCaseId(), extractions);

        // 3. three-paragraph extractive summary - Facts / Legal Issues / Current Status (FR3)
        Summarizer.Result summary = Summarizer.summarize(content.fullText(), legalCase);
        Summary s = new Summary("s" + UUID.randomUUID().toString().substring(0, 10),
                doc.getId(), doc.getCaseId(),
                summary.paragraph1(), summary.paragraph2(), summary.paragraph3(),
                1, Summary.SummaryStatus.DRAFT, "AI NLP Worker", LocalDateTime.now(), null);
        summaryService.insert(s);

        // 4. mark processed + audit
        documentService.setStatus(doc.getId(), LegalDocument.ProcessingStatus.PROCESSED);
        double avgConf = extractions.stream().mapToDouble(Extraction::getConfidence).average().orElse(0);
        audit.success("AI Worker", "AI_PROCESSED",
                doc.getFileName() + ": extracted " + extractions.size() + " fields (avg confidence "
                        + Math.round(avgConf * 100) + "%) and generated a 3-paragraph draft summary");
    }

    /**
     * Decides whether an uploaded document is relevant to the case it was
     * uploaded against. Returns a blocking reason, or null when the document
     * passes validation.
     *
     * The check is deliberately LENIENT so correct documents are never blocked:
     *  - The document is accepted when the case reference is mentioned, OR the
     *    document text mentions the client's/opposing party's names anywhere
     *    (not just as extracted fields), OR the document is clearly legal
     *    content (court / summons / claim / affidavit / hearing ...).
     *  - It is only BLOCKED when there is strong evidence of a problem:
     *      a) no legal content at all AND no case identifiers (grocery lists etc.)
     *      b) extracted parties belong to a clearly different matter AND the
     *         text never mentions the client/opposing party nor the case ref.
     */
    private static String rejectionReason(List<Extraction> extractions, String fullText, Case legalCase) {
        String text = (fullText == null ? "" : fullText.toLowerCase());
        if (text.isBlank()) return null; // blank text is handled by the FAIL path

        boolean mentionsCaseRef = legalCase.getReference() != null
                && text.contains(legalCase.getReference().toLowerCase());

        // collect extracted parties (structured fields)
        List<String> parties = new java.util.ArrayList<>();
        for (Extraction e : extractions) {
            String f = e.getFieldName().toLowerCase();
            if (f.equals("plaintiff") || f.equals("defendant")
                    || f.equals("applicant") || f.equals("respondent")
                    || f.equals("party")) {
                parties.add(e.getFieldValue().toLowerCase());
            }
        }

        String client = legalCase.getClientName() == null ? "" : legalCase.getClientName();
        String opposing = legalCase.getOpposingParty() == null ? "" : legalCase.getOpposingParty();

        // does the WHOLE document text mention the client or opposing party?
        boolean textMentionsClient = textMentions(text, client);
        boolean textMentionsOpposing = textMentions(text, opposing);
        // distinguishing tokens only (surnames / company words - NOT shared first names)
        boolean textMentionsClientDistinct = textMentionsDistinct(text, client);
        boolean textMentionsOpposingDistinct = textMentionsDistinct(text, opposing);

        // does any extracted party field genuinely match the case parties?
        boolean partyMatch = false;
        for (String p : parties) {
            if (overlaps(p, client) || overlaps(p, opposing)) { partyMatch = true; break; }
        }

        // a) completely non-legal content with no case identifiers -> block
        if (!hasLegalKeywords(text) && !mentionsCaseRef
                && !textMentionsClient && !textMentionsOpposing) {
            return "The document contains no case information (no parties, dates, case number or legal content could be identified).";
        }

        // b) clear different matter: extracted parties exist, none match, and the
        //    text never mentions the client's/opposing party's distinguishing names
        //    (surname / company word) nor the case reference.
        //    The distinguishing check avoids false blocks AND false accepts caused by
        //    a shared first name (e.g. "Thabo Nkosi" vs "Thabo Mokoena").
        if (!parties.isEmpty() && !partyMatch && !mentionsCaseRef
                && !textMentionsClientDistinct && !textMentionsOpposingDistinct) {
            return "The document relates to a different matter (extracted parties: "
                    + String.join(" v ", parties) + ") - it does not match case "
                    + legalCase.getReference() + " (" + legalCase.getClientName()
                    + " v " + legalCase.getOpposingParty() + ").";
        }

        // otherwise accept - the document is relevant to this case
        return null;
    }

    private static final java.util.List<String> LEGAL_KEYWORDS = java.util.List.of(
            "court", "summons", "claim", "affidavit", "hearing", "plaintiff", "defendant",
            "applicant", "respondent", "matter", "notice", "agreement", "damages", "lease",
            "contract", "debt", "loan", "injury", "divorce", "maintenance", "the sum of",
            "case number", "plea", "action", "litigation", "attorney", "advocate", "witness",
            "statement", "particulars", "set down", "strike out", "costs");

    private static boolean hasLegalKeywords(String text) {
        for (String kw : LEGAL_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** True when any significant token of a party name appears as a word in the text. */
    private static boolean textMentions(String text, String name) {
        if (name == null || name.isBlank()) return false;
        for (String token : significantTokens(name)) {
            if (wordInText(text, token)) return true;
        }
        return false;
    }

    /** Like textMentions but SKIPS the first token (usually the first name), so a shared
     *  first name ("Thabo Nkosi" vs "Thabo Mokoena") can never cause a false match. */
    private static boolean textMentionsDistinct(String text, String name) {
        if (name == null || name.isBlank()) return false;
        java.util.List<String> tokens = new java.util.ArrayList<>(significantTokens(name));
        if (tokens.size() <= 1) return textMentions(text, name);
        tokens.remove(0); // drop the first token (first name / most generic word)
        for (String token : tokens) {
            if (wordInText(text, token)) return true;
        }
        return false;
    }

    private static boolean wordInText(String text, String token) {
        return java.util.regex.Pattern.compile("(^|[^a-z0-9])" + java.util.regex.Pattern.quote(token) + "($|[^a-z0-9])")
                .matcher(text).find();
    }

    private static final java.util.Set<String> NAME_STOPWORDS = java.util.Set.of(
            "pty", "ltd", "cc", "inc", "sa", "and", "the", "of", "for", "van", "de",
            "der", "in", "re", "ex", "co", "limited", "holdings");

    /**
     * True when two party names genuinely overlap. Compares "significant"
     * tokens (length >= 4, not a legal-suffix/first-name stopword) and
     * ignores a token when it is the FIRST token of BOTH names - that catches
     * the "Thabo Nkosi" vs "Thabo Mokoena" case (shared first name only)
     * while still matching "Thabo Mokoena" v "Thabo Mokoena" via the surname.
     */
    private static boolean overlaps(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        java.util.Set<String> ta = significantTokens(a);
        java.util.Set<String> tb = significantTokens(b);
        if (ta.isEmpty() || tb.isEmpty()) return false;
        String firstA = firstToken(a);
        String firstB = firstToken(b);
        for (String t : ta) {
            if (tb.contains(t) && !(t.equals(firstA) && t.equals(firstB))) return true;
        }
        return false;
    }

    private static java.util.Set<String> significantTokens(String name) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String token : name.toLowerCase().split("[^a-z0-9]+")) {
            if (token.length() >= 4 && !NAME_STOPWORDS.contains(token)) out.add(token);
        }
        return out;
    }

    private static String firstToken(String name) {
        for (String token : name.toLowerCase().split("[^a-z0-9]+")) {
            if (!token.isBlank()) return token;
        }
        return "";
    }

    private void fail(LegalDocument doc, Exception e) {
        documentService.setStatus(doc.getId(), LegalDocument.ProcessingStatus.FAILED);
        audit.warning("AI Worker", "EXTRACTION_WARNING",
                doc.getFileName() + " failed processing: " + e.getMessage() + " - queued for retry (FR7)");
    }
}
