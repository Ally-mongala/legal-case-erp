package za.co.mongala.legal.backend.service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Full-text precedent and pleading search across all processed documents and
 * AI-extracted fields in the firm.
 */
public class PrecedentSearchService {

    public record SearchHit(
            String documentId,
            String fileName,
            String docType,
            String caseId,
            String caseReference,
            String caseTitle,
            String matterType,
            String court,
            int matchCount,
            String snippet
    ) {}

    private final DataSource ds;

    public PrecedentSearchService(DataSource ds) {
        this.ds = ds;
    }

    public List<SearchHit> search(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().length() < 2) {
            return List.of();
        }
        String q = rawQuery.trim();
        String qLower = q.toLowerCase(Locale.ROOT);
        List<String> terms = Arrays.stream(qLower.split("\\s+"))
                .filter(t -> t.length() >= 2)
                .toList();
        if (terms.isEmpty()) return List.of();

        List<SearchHit> hits = new ArrayList<>();
        String sql = """
                SELECT d.id AS doc_id, d.filename, d.doc_type, d.full_text,
                       c.id AS case_id, c.reference, c.title, c.matter_type, c.court
                FROM documents d
                JOIN cases c ON c.id = d.case_id
                WHERE d.status = 'PROCESSED'
                ORDER BY d.uploaded_at DESC
                """;

        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String docId = rs.getString("doc_id");
                String fileName = rs.getString("filename");
                String docType = rs.getString("doc_type");
                String fullText = rs.getString("full_text");
                String caseId = rs.getString("case_id");
                String ref = rs.getString("reference");
                String title = rs.getString("title");
                String matterType = rs.getString("matter_type");
                String court = rs.getString("court");

                String extraSpans = loadExtractionText(conn, docId);
                String combined = ((fullText == null ? "" : fullText) + "\n" + extraSpans).trim();
                if (combined.isEmpty()) continue;

                String lower = combined.toLowerCase(Locale.ROOT);
                int count = countOccurrences(lower, qLower);
                if (count == 0 && terms.size() > 1) {
                    // Multi-word AND match across terms
                    boolean allPresent = terms.stream().allMatch(lower::contains);
                    if (allPresent) {
                        for (String t : terms) count += countOccurrences(lower, t);
                    }
                }
                if (count > 0) {
                    String focusTerm = lower.contains(qLower) ? qLower : terms.get(0);
                    String snippet = buildSnippet(combined, lower, focusTerm);
                    hits.add(new SearchHit(docId, fileName, docType == null ? "PDF" : docType,
                            caseId, ref, title, matterType, court, count, snippet));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Precedent search failed", e);
        }

        hits.sort(Comparator.comparingInt(SearchHit::matchCount).reversed());
        return hits;
    }

    private String loadExtractionText(Connection conn, String docId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT field_name, field_value, span_text FROM extractions WHERE document_id = ?")) {
            ps.setString(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString(1)).append(": ").append(rs.getString(2))
                            .append(" (").append(rs.getString(3)).append(") ");
                }
            }
        }
        return sb.toString();
    }

    private static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) return 0;
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String buildSnippet(String original, String lower, String termLower) {
        int idx = lower.indexOf(termLower);
        if (idx < 0) {
            return original.length() <= 180 ? original : original.substring(0, 180) + "...";
        }
        int start = Math.max(0, idx - 75);
        int end = Math.min(original.length(), idx + termLower.length() + 95);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < original.length() ? "..." : "";
        return prefix + original.substring(start, end).replaceAll("\\s+", " ").trim() + suffix;
    }
}
