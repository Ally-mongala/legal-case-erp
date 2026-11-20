package za.co.mongala.legal.model;

import java.util.List;

/**
 * Standard legal matter types (practice areas). Used as the full drop-down
 * list when creating a new case (lawyer) - served by the backend at
 * GET /api/matter-types and used directly by the JavaFX client.
 */
public final class MatterTypes {

    public static final List<String> LIST = List.of(
            "Personal Injury",
            "Motor Vehicle Accident",
            "Medical Negligence",
            "Breach of Contract",
            "Debt Collection",
            "Consumer Credit",
            "Banking Law",
            "Insurance Law",
            "Family Law",
            "Divorce",
            "Labour Law",
            "Unfair Dismissal",
            "Criminal Defence",
            "Property / Conveyancing",
            "Landlord & Tenant",
            "Municipal Law",
            "Rates & Valuation",
            "Corporate / Commercial",
            "Intellectual Property",
            "Tax Law",
            "Insolvency",
            "Wills & Estates",
            "Immigration",
            "Constitutional Law",
            "Environmental Law",
            "Defamation",
            "Construction Law",
            "General Litigation");

    private MatterTypes() {}
}
