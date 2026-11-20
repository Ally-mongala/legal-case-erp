package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.Extraction;
import za.co.mongala.legal.model.LegalDocument;

import java.time.LocalDate;
import java.util.*;

/**
 * Cross-document AI intelligence for a matter:
 *  1. Detects factual contradictions across multiple processed documents in the same case
 *     (e.g. conflicting Claim Amounts, Case Numbers, Hearing Dates, or Parties).
 *  2. Builds a unified Master Case Chronology sorted by date across case inception,
 *     extracted document dates, and deadlines.
 */
public class CaseIntelligenceService {

    private static final List<String> SINGULAR_FIELDS = List.of(
            "Claim Amount", "Case Number", "Court", "Hearing Date", "Plaintiff", "Defendant"
    );

    public record DocumentValue(
            String documentId,
            String fileName,
            String value,
            double confidence,
            int pageNumber,
            String spanText
    ) {}

    public record ContradictionAlert(
            String fieldName,
            List<DocumentValue> conflictingValues,
            String explanation
    ) {}

    public record TimelineEvent(
            LocalDate date,
            String category,       // "CASE_OPENED" | "DOCUMENT_DATE" | "DEADLINE"
            String title,
            String source,
            Integer pageNumber,
            String quote
    ) {}

    public record CaseIntelligenceReport(
            String caseId,
            int processedDocumentsAnalysed,
            boolean consistent,
            List<ContradictionAlert> contradictions,
            List<TimelineEvent> timeline
    ) {}

    private final CaseService cases;
    private final DocumentService documents;
    private final ExtractionService extractions;
    private final DeadlineService deadlines;

    public CaseIntelligenceService(CaseService cases, DocumentService documents,
                                   ExtractionService extractions, DeadlineService deadlines) {
        this.cases = cases;
        this.documents = documents;
        this.extractions = extractions;
        this.deadlines = deadlines;
    }

    public CaseIntelligenceReport analyse(String caseId) {
        Case c = cases.findById(caseId);
        if (c == null) return null;

        List<LegalDocument> docs = documents.byCase(caseId).stream()
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED)
                .toList();

        // Map fieldName -> list of distinct normalised values across documents
        Map<String, List<DocumentValue>> byField = new LinkedHashMap<>();
        List<TimelineEvent> events = new ArrayList<>();

        // 1. Matter opened date
        if (c.getOpenedDate() != null) {
            events.add(new TimelineEvent(c.getOpenedDate(), "CASE_OPENED",
                    "Matter opened", "Case register (" + c.getReference() + ")", null, ""));
        }

        // 2. Document extractions
        for (LegalDocument d : docs) {
            List<Extraction> exList = extractions.byDocument(d.getId());
            for (Extraction e : exList) {
                String fname = e.getFieldName();
                if (SINGULAR_FIELDS.stream().anyMatch(sf -> sf.equalsIgnoreCase(fname))) {
                    String canonField = SINGULAR_FIELDS.stream()
                            .filter(sf -> sf.equalsIgnoreCase(fname)).findFirst().orElse(fname);
                    byField.computeIfAbsent(canonField, k -> new ArrayList<>())
                            .add(new DocumentValue(d.getId(), d.getFileName(), e.getFieldValue(),
                                    e.getConfidence(), e.getPageNumber(), e.getSpanText()));
                }

                // Parse ISO dates extracted by AI into timeline
                LocalDate parsed = tryParseDate(e.getFieldValue());
                if (parsed != null) {
                    events.add(new TimelineEvent(parsed, "DOCUMENT_DATE",
                            fname + " extracted from pleading",
                            d.getFileName(), e.getPageNumber(), e.getSpanText()));
                }
            }
        }

        // 3. Case deadlines
        for (Deadline dl : deadlines.byCase(caseId)) {
            if (dl.getDate() != null) {
                events.add(new TimelineEvent(dl.getDate(), "DEADLINE",
                        dl.getTitle() + " (" + dl.getType() + ")",
                        dl.isSynced() ? "Calendar (synced)" : "Case deadlines", null, ""));
            }
        }

        // 4. Identify genuine cross-document contradictions
        List<ContradictionAlert> contradictions = new ArrayList<>();
        for (Map.Entry<String, List<DocumentValue>> kv : byField.entrySet()) {
            String field = kv.getKey();
            List<DocumentValue> vals = kv.getValue();
            // Deduplicate per (documentId, normalisedValue) so multiple dates inside 1 doc aren't treated as cross-doc conflict unless from >= 2 docs or conflicting singular claims
            Map<String, DocumentValue> distinctByNorm = new LinkedHashMap<>();
            Set<String> docIds = new HashSet<>();
            for (DocumentValue dv : vals) {
                docIds.add(dv.documentId());
                distinctByNorm.putIfAbsent(normaliseField(field, dv.value()), dv);
            }
            if (docIds.size() >= 2 && distinctByNorm.size() >= 2) {
                List<DocumentValue> conflicting = new ArrayList<>(distinctByNorm.values());
                String summary = conflicting.get(0).fileName() + " states \"" + conflicting.get(0).value()
                        + "\" whereas " + conflicting.get(1).fileName() + " states \"" + conflicting.get(1).value() + "\".";
                contradictions.add(new ContradictionAlert(field, conflicting, summary));
            }
        }

        events.sort(Comparator.comparing(TimelineEvent::date));
        return new CaseIntelligenceReport(caseId, docs.size(), contradictions.isEmpty(), contradictions, events);
    }

    private static String normaliseField(String field, String raw) {
        if (raw == null) return "";
        if ("Claim Amount".equalsIgnoreCase(field)) {
            return raw.replaceAll("[^0-9.]", "");
        }
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static LocalDate tryParseDate(String val) {
        if (val == null) return null;
        try {
            if (val.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(val);
        } catch (Exception ignored) {}
        return null;
    }
}
