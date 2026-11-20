package za.co.mongala.legal.backend.service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Conflict-of-interest checker across firm matters and AI-extracted parties.
 *
 * Under Legal Practice Council (LPC) rules, an attorney must screen prospective
 * clients and opposing parties before accepting a mandate so the firm does not
 * act against a current or former client.
 */
public class ConflictCheckService {

    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "pty", "ltd", "limited", "inc", "incorporated", "cc", "trust",
            "family", "bank", "south", "africa", "sa", "of", "the", "and",
            "partners", "client", "unknown", "company", "group", "holdings"
    );

    public record ConflictMatch(
            String severity,      // "CRITICAL" | "WARNING" | "INFO"
            String conflictType,  // "ADVERSE_TO_EXISTING_CLIENT" | "CLIENT_IS_EXISTING_OPPONENT" | "RELATED_MATTER" | "EXTRACTED_PARTY_MATCH"
            String caseId,
            String caseReference,
            String caseTitle,
            String matchedParty,
            String explanation
    ) {}

    public record ConflictReport(
            boolean hasCriticalConflict,
            boolean hasAnyMatch,
            int criticalCount,
            int warningCount,
            List<ConflictMatch> matches
    ) {}

    private final DataSource ds;

    public ConflictCheckService(DataSource ds) {
        this.ds = ds;
    }

    public ConflictReport check(String candidateClient, String candidateOpposing, String excludeCaseId) {
        List<ConflictMatch> matches = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        String normClient = normalise(candidateClient);
        String normOpposing = normalise(candidateOpposing);

        // Self-conflict guard
        if (!normClient.isEmpty() && !normOpposing.isEmpty() && partiesOverlap(normClient, normOpposing)) {
            matches.add(new ConflictMatch(
                    "CRITICAL",
                    "SAME_PARTY_BOTH_SIDES",
                    "",
                    "—",
                    "Prospective mandate",
                    candidateClient,
                    "Client and opposing party refer to the same person or entity."
            ));
        }

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, reference, title, client_name, opposing_party, status FROM cases ORDER BY opened_date DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cid = rs.getString("id");
                    if (excludeCaseId != null && excludeCaseId.equals(cid)) continue;

                    String ref = rs.getString("reference");
                    String title = rs.getString("title");
                    String exClient = rs.getString("client_name");
                    String exOpposing = rs.getString("opposing_party");
                    String status = rs.getString("status");

                    String nExClient = normalise(exClient);
                    String nExOpposing = normalise(exOpposing);

                    // 1. Prospective opposing party is an existing/former client -> CRITICAL
                    if (!normOpposing.isEmpty() && !nExClient.isEmpty() && partiesOverlap(normOpposing, nExClient)) {
                        String key = "ADV_CLIENT|" + cid;
                        if (seenKeys.add(key)) {
                            matches.add(new ConflictMatch(
                                    "CRITICAL",
                                    "ADVERSE_TO_EXISTING_CLIENT",
                                    cid, ref, title, exClient,
                                    "Prospective opposing party matches existing firm client \"" + exClient
                                            + "\" in matter " + ref + " (" + status + "). Acting against an own client breaches LPC conflict rules."
                            ));
                        }
                    }

                    // 2. Prospective client is an opposing party in another firm matter -> CRITICAL if open, WARNING if closed
                    if (!normClient.isEmpty() && !nExOpposing.isEmpty() && partiesOverlap(normClient, nExOpposing)) {
                        String key = "CLI_OPP|" + cid;
                        if (seenKeys.add(key)) {
                            boolean active = !"CLOSED".equalsIgnoreCase(status);
                            matches.add(new ConflictMatch(
                                    active ? "CRITICAL" : "WARNING",
                                    "CLIENT_IS_EXISTING_OPPONENT",
                                    cid, ref, title, exOpposing,
                                    "Prospective client matches opposing party \"" + exOpposing
                                            + "\" in matter " + ref + " (" + status + ")."
                            ));
                        }
                    }

                    // 3. Same client + same opposing party -> duplicate / related matter warning
                    if (!normClient.isEmpty() && !normOpposing.isEmpty()
                            && partiesOverlap(normClient, nExClient) && partiesOverlap(normOpposing, nExOpposing)) {
                        String key = "DUP|" + cid;
                        if (seenKeys.add(key)) {
                            matches.add(new ConflictMatch(
                                    "INFO",
                                    "RELATED_MATTER",
                                    cid, ref, title, exClient + " v " + exOpposing,
                                    "Both parties already appear together in matter " + ref + " (" + title + ")."
                            ));
                        }
                    }
                }
            }

            // 4. Also scan AI-extracted parties from processed documents across other cases
            try (PreparedStatement psEx = c.prepareStatement("""
                    SELECT e.field_name, e.field_value, d.case_id, cs.reference, cs.title
                    FROM extractions e
                    JOIN documents d ON d.id = e.document_id
                    JOIN cases cs ON cs.id = d.case_id
                    WHERE d.status = 'PROCESSED'
                      AND lower(e.field_name) IN ('plaintiff','defendant','applicant','respondent')
                    """);
                 ResultSet rsEx = psEx.executeQuery()) {
                while (rsEx.next()) {
                    String cid = rsEx.getString("case_id");
                    if (excludeCaseId != null && excludeCaseId.equals(cid)) continue;
                    String role = rsEx.getString("field_name");
                    String val = rsEx.getString("field_value");
                    String ref = rsEx.getString("reference");
                    String title = rsEx.getString("title");
                    String nVal = normalise(val);
                    if (nVal.isEmpty()) continue;

                    boolean isPlaintiffSide = role.equalsIgnoreCase("plaintiff") || role.equalsIgnoreCase("applicant");
                    if (isPlaintiffSide && !normOpposing.isEmpty() && partiesOverlap(normOpposing, nVal)) {
                        String key = "ADV_CLIENT|" + cid;
                        if (seenKeys.add(key)) {
                            matches.add(new ConflictMatch(
                                    "WARNING",
                                    "EXTRACTED_PARTY_MATCH",
                                    cid, ref, title, val,
                                    "Opposing party matches AI-extracted " + role + " \"" + val + "\" in matter " + ref + "."
                            ));
                        }
                    } else if (!isPlaintiffSide && !normClient.isEmpty() && partiesOverlap(normClient, nVal)) {
                        String key = "CLI_OPP|" + cid;
                        if (seenKeys.add(key)) {
                            matches.add(new ConflictMatch(
                                    "WARNING",
                                    "EXTRACTED_PARTY_MATCH",
                                    cid, ref, title, val,
                                    "Prospective client matches AI-extracted " + role + " \"" + val + "\" in matter " + ref + "."
                            ));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run conflict check", e);
        }

        int critical = (int) matches.stream().filter(m -> "CRITICAL".equals(m.severity())).count();
        int warnings = (int) matches.stream().filter(m -> "WARNING".equals(m.severity())).count();
        return new ConflictReport(critical > 0, !matches.isEmpty(), critical, warnings, matches);
    }

    static String normalise(String raw) {
        if (raw == null) return "";
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) return "";
        List<String> kept = new ArrayList<>();
        for (String tok : cleaned.split(" ")) {
            if (tok.length() >= 2 && !LEGAL_SUFFIXES.contains(tok)) {
                kept.add(tok);
            }
        }
        return String.join(" ", kept);
    }

    static boolean partiesOverlap(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b) || a.contains(b) || b.contains(a)) return true;
        Set<String> ta = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> tb = new HashSet<>(Arrays.asList(b.split(" ")));
        // Require either exact full-token subset or >= 2 distinctive shared tokens (e.g. first + surname),
        // or a single distinctive surname/entity token >= 5 chars when one side has 1 token.
        List<String> shared = ta.stream().filter(t -> t.length() >= 4 && tb.contains(t)).toList();
        if (shared.size() >= 2) return true;
        if (shared.size() == 1 && (ta.size() == 1 || tb.size() == 1) && shared.get(0).length() >= 5) return true;
        return false;
    }
}
