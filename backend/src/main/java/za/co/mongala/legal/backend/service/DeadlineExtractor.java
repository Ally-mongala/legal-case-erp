package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.Extraction;
import za.co.mongala.legal.model.Case;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Turns AI-extracted dates into real deadline records on the case (FR4 / US5).
 * Dedupes by (case, title, date) so re-processing a document never creates
 * duplicate deadlines.
 */
public final class DeadlineExtractor {

    private final DeadlineService deadlineService;
    private final CaseService caseService;

    public DeadlineExtractor(DeadlineService deadlineService, CaseService caseService) {
        this.deadlineService = deadlineService;
        this.caseService = caseService;
    }

    public void apply(String documentId, String caseId, List<Extraction> extractions) {
        for (Extraction ex : extractions) {
            String field = ex.getFieldName();
            LocalDate date = parseDate(ex.getFieldValue());
            if (date == null) continue;

            Deadline.DeadlineType type;
            String title;
            int reminder;
            switch (field) {
                case "Hearing Date" -> { type = Deadline.DeadlineType.HEARING; title = "Hearing - extracted from document"; reminder = 5; }
                case "Date" -> { type = Deadline.DeadlineType.FILING; title = "Deadline - extracted from document"; reminder = 7; }
                default -> { continue; }
            }
            // de-duplicate
            boolean exists = deadlineService.byCase(caseId).stream()
                    .anyMatch(d -> d.getDate().equals(date) && d.getTitle().equals(title));
            if (exists) continue;

            Deadline d = new Deadline("dl" + UUID.randomUUID().toString().substring(0, 10),
                    caseId, "", title, date, type, reminder, false);
            deadlineService.insert(d);
        }
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
