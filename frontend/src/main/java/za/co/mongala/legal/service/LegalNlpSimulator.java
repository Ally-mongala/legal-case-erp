package za.co.mongala.legal.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Extraction;
import za.co.mongala.legal.model.LegalDocument;
import za.co.mongala.legal.model.Summary;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Simulated AI / NLP module ("the AI tutor's brain").
 *
 * In the production system this would be a containerised transformer model
 * (Hugging Face) fine-tuned on legal text, called via an /infer endpoint
 * (Assignment 2 section 12). The front-end prototype instead returns a
 * deterministic, realistic result so the whole human-in-the-loop workflow
 * (upload -> extract -> summarise -> review -> approve) can be demonstrated.
 *
 * It still honours the emerging-tech requirements the UI was built around:
 *  - confidence score on every field            (ETR5)
 *  - source span text + page for explainability (ETR2)
 *  - versioned summaries                        (ETR4)
 */
public class LegalNlpSimulator {

    /** Small utility record holding the result of one AI processing run. */
    public record AiProcessingResult(List<Extraction> extractions, Summary summary) {}

    /**
     * Simulates processing a PDF: extracts key fields and builds a three-paragraph
     * summary (FR2 + FR3). In the real system this runs in a worker queue
     * (RabbitMQ) with the inference service; here it is instant.
     */
    public AiProcessingResult processDocument(LegalDocument doc, Case legalCase) {
        List<Extraction> ex = extract(doc, legalCase);
        Summary summary = new Summary(
                "s" + System.currentTimeMillis(),
                doc.getId(),
                legalCase.getId(),
                paragraphOne(legalCase),
                paragraphTwo(legalCase, doc),
                paragraphThree(legalCase, doc),
                1, Summary.SummaryStatus.DRAFT,
                "AI NLP Worker",
                LocalDateTime.now(),
                null);
        return new AiProcessingResult(ex, summary);
    }

    /* ---------------- simulated extraction ---------------- */

    private List<Extraction> extract(LegalDocument doc, Case c) {
        // Realistic-looking deterministic fields, derived from the case record.
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String amount = c.getAmountInvolved() > 0 ? String.format("R%,.2f", c.getAmountInvolved()).replace(".00", "") : "Unquantified";
        String span = "as more fully appears from the record in the matter of " + c.getTitle();
        return List.of(
                new Extraction("Case Number", "GP/JHB/2026/" + (8200 + (c.getId().hashCode() % 700)), 0.93, span, 1),
                new Extraction("Parties", c.getClientName() + " v " + c.getOpposingParty(), 0.97,
                        "in the matter between " + c.getClientName() + " and " + c.getOpposingParty(), 1),
                new Extraction("Matter Type", c.getMatterType(), 0.89,
                        "an action in " + c.getMatterType(), 1),
                new Extraction("Court", c.getCourt(), 0.99, "in the " + c.getCourt(), 1),
                new Extraction("Claim Amount", amount, 0.86,
                        "claimed damages in the sum of " + amount, 2),
                new Extraction("Next Procedural Step", "Review by legal team - " + c.getReference(), 0.72,
                        "the matter requires further procedural steps as directed", 2),
                new Extraction("Document Type", "Legal Pleading / Evidence", 0.91,
                        doc.getFileName().replace(".pdf", "").replace("_", " "), 1));
    }

    /* ---------------- simulated three-paragraph summary ---------------- */

    private String paragraphOne(Case c) {
        String amount = c.getAmountInvolved() > 0
                ? "with a claim amount of " + String.format("R%,.2f", c.getAmountInvolved()).replace(".00", "") + "."
                : "with the quantum to be determined."
        ;
        return "This matter concerns a " + c.getMatterType().toLowerCase() + " dispute between "
                + c.getClientName() + " and " + c.getOpposingParty() + " (" + c.getReference() + "), "
                + amount + " The action is pending in the " + c.getCourt() + " and was opened on "
                + c.getOpenedDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy")) + ".";
    }

    private String paragraphTwo(Case c, LegalDocument doc) {
        return "The uploaded document \"" + doc.getFileName() + "\" was automatically analysed. "
                + "The AI extraction identified the parties, the court and the procedural posture "
                + "of the matter, which is currently " + c.getStatus().getDisplayName().toLowerCase() + ". "
                + "Key dates and filing obligations identified in the document have been flagged for "
                + "calendar synchronisation so that no deadline is missed.";
    }

    private String paragraphThree(Case c, LegalDocument doc) {
        return "Recommendation: the legal team should review this draft, correct any extraction "
                + "errors directly in the interface, and approve the summary so that the client can "
                + "view a simplified update in their portal. Corrected extractions are fed back into "
                + "the training dataset, so every review improves the model's accuracy.";
    }
}
