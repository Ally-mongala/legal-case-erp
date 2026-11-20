package za.co.mongala.legal.backend.nlp;

import za.co.mongala.legal.model.Extraction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real field-extraction engine (FR2, ETR2, ETR5).
 *
 * Works on the raw text of each PDF page and returns structured fields:
 *   - dates (multiple formats, validated as real calendar dates)
 *   - parties (plaintiff/defendant/applicant/respondent lines, "X v Y")
 *   - case number, court, claim amount, document type
 * Every field carries:
 *   - a confidence score   (ETR5)
 *   - the exact text span  (ETR2 explainability)
 *   - the source page      (ETR2)
 *
 * In the production system this logic is a fine-tuned transformer model; the
 * deterministic rules here give the same contract (field, value, confidence,
 * span, page) so the UI and pipeline are model-agnostic.
 */
public final class ExtractionEngine {

    private static final List<String> MONTHS = List.of(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");
    private static final String MONTH_ALT = String.join("|", MONTHS);

    private static final Pattern DATE_NUMERIC = Pattern.compile("\\b(\\d{1,2})[/\\-.]\\s?(\\d{1,2})[/\\-.]\\s?(\\d{2,4})\\b");
    private static final Pattern DATE_WORD = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(" + MONTH_ALT + ")\\s*,?\\s+(\\d{4})\\b");
    private static final Pattern DATE_WORD_REV = Pattern.compile("\\b(" + MONTH_ALT + ")\\s+(\\d{1,2})\\s*,?\\s+(\\d{4})\\b");
    private static final Pattern RELATIVE_DEADLINE = Pattern.compile("\\bwithin\\s+(\\d{1,3})\\s+(?:calendar\\s+)?days\\b", Pattern.CASE_INSENSITIVE);
    // Matches a case number either parenthesised - "(GP/PTA/2026/4471)" - or introduced by
    // a label. The label form accepts "case number", "case no", "case no." and "case",
    // optionally followed by a colon or dash, because real pleadings head the page
    // "Case Number: GP/PTA/2026/4471". Requiring whitespace after the label previously
    // missed every colon-separated heading (regression covered by TC-U01 / TC-U05).
    private static final Pattern CASE_NUMBER = Pattern.compile(
            "\\(\\s*([A-Z]{2,4})\\s*/\\s*([A-Z]{2,4})\\s*/\\s*(\\d{4})\\s*/\\s*(\\d+)\\s*\\)|" +
            "\\bcase\\s*(?:number|no\\.?)?\\s*[:\\-]?\\s*([A-Z]{2,4}/[A-Z]{2,4}/\\d{4}/\\d+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_LINE = Pattern.compile(
            "^(?:Plaintiff|Defendant|Applicant|Respondent|First Defendant|Second Defendant|Third Party)\\s*[:.]?\\s*(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PARTY_NAME_FIRST = Pattern.compile(
            "^(?<name>[A-Z][A-Za-z&'.,\\- ]{2,90}?)\\s+(?<role>Plaintiff|Defendant|Applicant|Respondent)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern PARTY_V = Pattern.compile(
            "\\b(?:in\\s+the\\s+matter\\s+between|between)\\s+([A-Z][^,;\\n]{2,120})\\s+(?:and|v\\.?)\\s+([A-Z][^,;\\n]{2,120})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "R\\s?\\d[\\d,\\s]*\\.?\\d{0,2}|\\bthe\\s+sum\\s+of\\s+R\\s?\\d[\\d,\\s]*\\.?\\d{0,2}", Pattern.CASE_INSENSITIVE);
    private static final Pattern COURT = Pattern.compile(
            "(?:Gauteng|Western Cape|KwaZulu-Natal|Eastern Cape|Free State|North West|Limpopo|Mpumalanga|Northern Cape|Northern|Southern|Central)\\s+Division\\s+of\\s+the\\s+High\\s+Court|" +
            "(?:the\\s+)?High\\s+Court|Magistrate'?s?\\s+Court|Labour\\s+Court|Equality\\s+Court", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTORNEY = Pattern.compile(
            "(?:attorneys?|solicitors?)\\s*(?:for\\s+(?:the\\s+)?(?:plaintiff|defendant|applicant|respondent))?\\s*[:.]?\\s*([A-Z][A-Za-z&\\s'.,-]{2,90})" +
            "|(?:acting\\s+for\\s+(?:the\\s+)?(?:plaintiff|defendant|applicant|respondent)\\s*:\\s*)([A-Z][A-Za-z&\\s'.,-]{2,90})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JUDGE = Pattern.compile(
            "\\b(?:before|presided\\s+over\\s+by)\\s+(?:the\\s+)?(?:Honourable\\s+)?(?:Justice|Judge)\\s+([A-Z][A-Za-z'.-]{2,60})" +
            "|\\bjudge\\s*[:.]?\\s*([A-Z][A-Za-z'.-]{2,60})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOC_TYPE = Pattern.compile(
            "\\b(SUMMONS|STATEMENT\\s+OF\\s+CLAIM|MEDICAL\\s+REPORT|AFFIDAVIT|NOTICE\\s+OF\\s+MOTION|PARTICULARS\\s+OF\\s+CLAIM|PLEA|PRETRIAL\\s+MINUTES|PRE-TRIAL\\s+MINUTES|HEADS\\s+OF\\s+ARGUMENT|SETTLEMENT\\s+AGREEMENT|RULE\\s+41\\s+AGREEMENT|WITNESS\\s+STATEMENT)\\b",
            Pattern.CASE_INSENSITIVE);

    private ExtractionEngine() {}

    /**
     * Runs every rule over each page's text.
     * @param pages page texts (page i -> index i-1)
     */
    public static List<Extraction> extract(List<String> pages) {
        LinkedHashMap<String, Extraction> found = new LinkedHashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            String text = pages.get(i);
            int page = i + 1;
            extractDates(text, page, found);
            extractParties(text, page, found);
            extractAttorneys(text, page, found);
            extractJudge(text, page, found);
            extractCaseNumber(text, page, found);
            extractCourt(text, page, found);
            extractAmount(text, page, found);
        }
        // document type: derive from the whole text (page 1 preferred)
        String firstPage = pages.isEmpty() ? "" : pages.get(0);
        Matcher m = DOC_TYPE.matcher(firstPage.toUpperCase());
        if (m.find()) {
            found.putIfAbsent("Document Type", new Extraction("Document Type", titleCase(m.group(1)),
                    0.97, m.group(1), 1));
        }
        // key facts (FR2): a couple of the most informative sentences
        for (Extraction kf : extractKeyFacts(pages)) {
            found.putIfAbsent(kf.getFieldName(), kf);
        }
        return new ArrayList<>(found.values());
    }

    /** FR2 "key facts": 2 high-signal sentences (keywords, dates, amounts). */
    private static List<Extraction> extractKeyFacts(List<String> pages) {
        List<String> candidates = new ArrayList<>();
        for (String page : pages) {
            for (String part : page.split("(?<=[.!?])\\s+")) {
                String s = part.replaceAll("\\s+", " ").trim();
                if (s.length() > 40 && s.length() < 350) candidates.add(s);
            }
        }
        List<String> best = new ArrayList<>();
        for (String s : candidates) {
            if (!s.matches(".*[a-z]{3,}.*") || mostlyUppercase(s)) continue; // skip headers
            int score = 0;
            String t = s.toLowerCase();
            for (String kw : List.of("plaintiff", "defendant", "claim", "damages", "court",
                    "hearing", "filed", "negligence", "breach", "amount", "contract",
                    "collision", "sum", "injury", "repair", "loan", "rates", "fees")) {
                if (t.contains(kw)) score++;
            }
            if (t.matches(".*\\b\\d{4}\\b.*")) score++;
            if (score >= 3) best.add(s);
            if (best.size() >= 2) break;
        }
        List<Extraction> out = new ArrayList<>();
        int n = 1;
        for (String s : best) {
            out.add(new Extraction("Key Fact " + n,
                    s.length() > 200 ? s.substring(0, 200) + "\u2026" : s,
                    0.7, s.substring(0, Math.min(90, s.length())), 1));
            n++;
        }
        return out;
    }

    private static void extractAttorneys(String text, int page, Map<String, Extraction> out) {
        Matcher m = ATTORNEY.matcher(text);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            if (name != null) {
                name = clean(name).replaceAll("[:.]+$", "").trim();
                if (name.length() > 2) {
                    out.putIfAbsent("Attorney", new Extraction("Attorney", name, 0.82, m.group().trim(), page));
                    break;
                }
            }
        }
    }

    private static void extractJudge(String text, int page, Map<String, Extraction> out) {
        Matcher m = JUDGE.matcher(text);
        while (m.find()) {
            String name = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            if (name != null) {
                name = clean(name);
                if (name.length() > 1) {
                    out.putIfAbsent("Judge", new Extraction("Judge", name, 0.78, m.group().trim(), page));
                    break;
                }
            }
        }
    }

    private static void extractDates(String text, int page, Map<String, Extraction> out) {
        Matcher m = DATE_NUMERIC.matcher(text);
        while (m.find()) {
            String value = parseDate(m.group(1), m.group(2), m.group(3));
            if (value != null) {
                out.putIfAbsent("Date", new Extraction("Date", value, 0.97, m.group(), page));
                break;
            }
        }
        // hearing date: prefer the date whose sentence mentions a hearing/court
        // (e.g. "set down for hearing on 12 November 2026"); otherwise the latest
        // word-date in the document (hearing dates are almost always future dates).
        String hearing = null;
        String hearingSpan = null;
        int hearingPage = page;
        LocalDate latest = null;
        m = DATE_WORD.matcher(text);
        while (m.find()) {
            String value = parseDate(m.group(1), monthNum(m.group(2)), m.group(3));
            if (value == null) continue;
            LocalDate d = LocalDate.parse(value);
            if (latest == null || d.isAfter(latest)) { latest = d; }
            String sentence = sentenceAround(text, m.start(), m.end());
            // "court" is deliberately NOT a trigger: a court heading in the same (full-stop-less,
            // OCR-style) block would otherwise pull the collision date in as the hearing date.
            if (sentence != null && (sentence.toLowerCase().contains("hearing")
                    || sentence.toLowerCase().contains("set down"))) {
                hearing = value;
                hearingSpan = m.group();
                hearingPage = page;
                break;
            }
        }
        m = DATE_WORD_REV.matcher(text);
        while (m.find()) {
            String value = parseDate(m.group(2), monthNum(m.group(1)), m.group(3));
            if (value == null) continue;
            LocalDate d = LocalDate.parse(value);
            if (latest == null || d.isAfter(latest)) { latest = d; }
            String sentence = sentenceAround(text, m.start(), m.end());
            // "court" is deliberately NOT a trigger: a court heading in the same (full-stop-less,
            // OCR-style) block would otherwise pull the collision date in as the hearing date.
            if (sentence != null && (sentence.toLowerCase().contains("hearing")
                    || sentence.toLowerCase().contains("set down"))) {
                hearing = value;
                hearingSpan = m.group();
                hearingPage = page;
                break;
            }
        }
        if (hearing == null && latest != null) {
            // fallback: latest word-date as the procedural date
            hearing = latest.toString();
        }
        if (hearing != null) {
            out.putIfAbsent("Hearing Date", new Extraction("Hearing Date", hearing,
                    hearingSpan != null ? 0.95 : 0.8, hearingSpan == null ? "latest procedural date" : hearingSpan,
                    hearingPage));
        }
        m = RELATIVE_DEADLINE.matcher(text);
        if (m.find()) {
            out.putIfAbsent("Relative Deadline", new Extraction("Relative Deadline",
                    "within " + m.group(1) + " days", 0.72, m.group(), page));
        }
    }

    /** Returns the sentence containing the given span, or null. */
    private static String sentenceAround(String text, int start, int end) {
        int s = Math.max(0, text.lastIndexOf('.', start) + 1);
        int e = text.indexOf('.', end);
        if (e < 0) e = Math.min(text.length(), start + 200);
        String sent = text.substring(s, Math.min(text.length(), e + 1));
        return sent.length() < 400 ? sent : null;
    }

    private static void extractParties(String text, int page, Map<String, Extraction> out) {
        Matcher m = PARTY_LINE.matcher(text);
        while (m.find()) {
            String name = clean(m.group(1));
            if (name.length() > 2) {
                String field = m.group(0).trim().split("\\s+")[0];
                String fieldName = switch (field.toLowerCase()) {
                    case "plaintiff" -> "Plaintiff";
                    case "defendant" -> "Defendant";
                    case "applicant" -> "Applicant";
                    case "respondent" -> "Respondent";
                    default -> "Party";
                };
                out.putIfAbsent(fieldName, new Extraction(fieldName, name, 0.9, m.group().trim(), page));
            }
        }
        // "NAME Plaintiff" / "NAME Defendant" style (party word after the name)
        m = PARTY_NAME_FIRST.matcher(text);
        while (m.find()) {
            String role = m.group("role");
            String name = clean(m.group("name"));
            String field = role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
            if (name.length() > 2) {
                out.putIfAbsent(field, new Extraction(field, name, 0.9, m.group().trim(), page));
            }
        }
        m = PARTY_V.matcher(text);
        if (m.find() && !out.containsKey("Plaintiff") && !out.containsKey("Applicant")) {
            String a = clean(m.group(1));
            String b = clean(m.group(2));
            out.put("Plaintiff", new Extraction("Plaintiff", a, 0.85, m.group(), page));
            out.put("Defendant", new Extraction("Defendant", b, 0.85, m.group(), page));
        }
    }

    private static void extractCaseNumber(String text, int page, Map<String, Extraction> out) {
        Matcher m = CASE_NUMBER.matcher(text);
        if (m.find()) {
            // Group 5 is the labelled alternative ("Case Number: GP/PTA/2026/4471"); when it
            // matched, use it directly so the label and any colon are excluded from the value.
            String value = m.group(5) != null
                    ? m.group(5).trim()
                    : m.group(0).trim().replaceAll("^\\(|\\)$", "").trim();
            out.putIfAbsent("Case Number", new Extraction("Case Number", value, 0.94, m.group().trim(), page));
        }
    }

    private static void extractCourt(String text, int page, Map<String, Extraction> out) {
        // keep the most specific (longest) court expression in the document
        String best = null;
        String bestSpan = null;
        Matcher m = COURT.matcher(text);
        while (m.find()) {
            String value = m.group().replaceAll("(?i)^the\\s+", "");
            value = value.substring(0, 1).toUpperCase() + value.substring(1);
            if (best == null || value.length() > best.length()) {
                best = value;
                bestSpan = m.group();
            }
        }
        if (best != null) {
            out.putIfAbsent("Court", new Extraction("Court", best, 0.95, bestSpan, page));
        }
    }

    private static void extractAmount(String text, int page, Map<String, Extraction> out) {
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String value = m.group().trim();
            if (!value.toLowerCase().startsWith("the sum of")) {
                value = "R" + value.replaceFirst("^R\\s?", "");
            }
            out.putIfAbsent("Claim Amount", new Extraction("Claim Amount", value, 0.85, m.group(), page));
            break;
        }
    }

    /* ---------------- helpers ---------------- */

    private static String parseDate(String d, String mo, String y) {
        try {
            return toIso(Integer.parseInt(d.trim()), Integer.parseInt(mo.trim()), Integer.parseInt(y.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseDate(String d, int month, String y) {
        try {
            return toIso(Integer.parseInt(d.trim()), month, Integer.parseInt(y.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String toIso(int day, int month, int year) {
        try {
            if (year < 100) year += 2000;
            LocalDate date = LocalDate.of(year, month, day);
            if (date.isBefore(LocalDate.of(2000, 1, 1)) || date.isAfter(LocalDate.of(2100, 1, 1))) return null;
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static int monthNum(String name) {
        for (int i = 0; i < MONTHS.size(); i++) {
            if (MONTHS.get(i).equalsIgnoreCase(name)) return i + 1;
        }
        return -1;
    }

    private static String clean(String s) {
        s = s.trim().replaceAll("\\s+", " ");
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    /** True when most words are uppercase (typical heading/caption lines). */
    private static boolean mostlyUppercase(String s) {
        String[] words = s.trim().split("\\s+");
        if (words.length < 3) return false;
        int upper = 0;
        for (String w : words) {
            if (w.length() > 1 && w.equals(w.toUpperCase())) upper++;
        }
        return upper * 100 / words.length > 40;
    }

    private static String titleCase(String s) {
        s = s.toLowerCase();
        StringBuilder b = new StringBuilder();
        boolean cap = true;
        for (char ch : s.toCharArray()) {
            if (cap && Character.isLetter(ch)) { b.append(Character.toUpperCase(ch)); cap = false; }
            else b.append(ch);
            if (ch == ' ') cap = true;
        }
        return b.toString();
    }
}
