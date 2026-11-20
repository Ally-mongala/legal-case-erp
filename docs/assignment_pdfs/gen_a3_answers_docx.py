#!/usr/bin/env python3
"""
ISJ107V Assignment 3 (S2 2026) - Project Documentation and UML Design
Generates a submission-ready Word document answering Q1-Q5 (50 marks).

Student: Ally Mongala (219361629)
System:  LegalCase ERP - Legal Case Management ERP for small law firms

All test counts, coverage percentages and timing figures in this document are
read from the real build output (surefire + JaCoCo + PerformanceMetricsTest).
"""
import os
from docxkit_a3 import *          # shared formatting helpers + `doc`
from docxkit_a3 import doc, HERE, ROOT, NAVY, GREY, BODY_FONT, CONTENT_W
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH

UML_A3 = os.path.join(ROOT, "docs", "uml", "a3")
UML_A2 = os.path.join(ROOT, "docs", "uml", "a2")
SHOTS = os.path.join(ROOT, "screenshots")
OUT = os.path.join(HERE, "Assignment3_ISJ107V_Ally_Mongala.docx")

GIT_COMMITS = 16

# ================================================================ COVER PAGE
doc.add_paragraph().paragraph_format.space_after = Pt(36)

p = doc.add_paragraph(); r = p.add_run("TSHWANE UNIVERSITY OF TECHNOLOGY")
r.font.size = Pt(13); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(2)

p = doc.add_paragraph(); r = p.add_run("Faculty of Information and Communication Technology")
r.font.size = Pt(11); r.font.color.rgb = GREY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(32)

p = doc.add_paragraph(); r = p.add_run("ISJ107V - INTEGRATED SOFTWARE PROJECT")
r.font.size = Pt(16); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(6)

p = doc.add_paragraph(); r = p.add_run("Assignment 3")
r.font.size = Pt(20); r.bold = True; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(4)

p = doc.add_paragraph(); r = p.add_run("Project Documentation and UML Design")
r.font.size = Pt(14); r.italic = True; r.font.color.rgb = GREY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(28)

p = doc.add_paragraph(); r = p.add_run("LegalCase ERP")
r.font.size = Pt(17); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(2)

p = doc.add_paragraph(); r = p.add_run("An AI-Assisted Legal Case Management ERP for Small Law Firms")
r.font.size = Pt(11.5); r.italic = True; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(34)

info = doc.add_table(rows=0, cols=2)
from docx.enum.table import WD_TABLE_ALIGNMENT
info.alignment = WD_TABLE_ALIGNMENT.CENTER
for k, v in [("Student name", "Ally Mongala"),
             ("Student number", "219361629"),
             ("Module code", "ISJ107V"),
             ("Assessment", "Assignment 3 (Individual)"),
             ("Weighting", "20% of the final module mark"),
             ("Total marks", "50"),
             ("Due date", "30 September 2026"),
             ("Document version", "1.0"),
             ("Emerging technology", "Artificial Intelligence / Natural Language Processing")]:
    cells = info.add_row().cells
    cells[0].width = Cm(5.2); cells[1].width = Cm(8.4)
    for cell, txt, bold in ((cells[0], k, True), (cells[1], v, False)):
        cell.text = ""
        pp = cell.paragraphs[0]
        rr = pp.add_run(txt)
        rr.font.size = Pt(11); rr.bold = bold; rr.font.name = BODY_FONT
        if bold:
            rr.font.color.rgb = NAVY
        pp.paragraph_format.space_after = Pt(3)
        pp.paragraph_format.line_spacing = 1.2

page_break()

# ============================================== DECLARATION
h1("Declaration of Originality")
para("I, Ally Mongala (student number 219361629), declare that:")
bullet("This assignment is my own original work, produced for the ISJ107V Integrated Software Project module.")
bullet("Where the work of other authors has been used, it has been acknowledged and referenced according to the Harvard convention.")
bullet("I have not allowed, and will not allow, any person to copy my work with the intention of presenting it as their own.")
bullet("The system described in this document was designed and implemented by me. All test results, measurements and screenshots were produced from my own working implementation and are reproducible from the accompanying repository.")
bullet("I understand that plagiarism is a serious academic offence and that the University may take disciplinary action against me should I be found guilty of it.")
doc.add_paragraph().paragraph_format.space_after = Pt(16)

sig = doc.add_table(rows=0, cols=2)
for k, v in [("Signature", "A. Mongala"), ("Student number", "219361629"), ("Date", "30 September 2026")]:
    cells = sig.add_row().cells
    cells[0].width = Cm(4.5); cells[1].width = Cm(9.0)
    cells[0].text = ""; cells[1].text = ""
    r1 = cells[0].paragraphs[0].add_run(k); r1.bold = True; r1.font.size = Pt(11); r1.font.name = BODY_FONT
    r2 = cells[1].paragraphs[0].add_run(v); r2.font.size = Pt(11); r2.font.name = BODY_FONT
    for c in cells:
        c.paragraphs[0].paragraph_format.space_after = Pt(6)

page_break()

# ============================================================ TOC
h1("Table of Contents")
for line in [
    "Question 1  Final Project Baseline  [6]",
    "    1.1  Title, purpose, users, emerging technology and executive summary  (2)",
    "    1.2  Final aim, objectives, scope and approved changes  (2)",
    "    1.3  Document control, glossary and referenced artefacts  (2)",
    "Question 2  Requirements and Traceability  [8]",
    "    2.1  Final prioritised functional and non-functional requirements  (3)",
    "    2.2  Requirements traceability matrix  (3)",
    "    2.3  Satisfaction of the three emerging-technology requirements  (2)",
    "Question 3  Final Design as Implemented  [16]",
    "    3.1  High-level system architecture  (2)",
    "    3.2  Use-case diagram and detailed use-case description  (3)",
    "    3.3  Class diagram  (3)",
    "    3.4  Sequence diagram  (3)",
    "    3.5  Activity diagram  (2)",
    "    3.6  Component and deployment diagrams  (3)",
    "Question 4  Implementation Documentation  [10]",
    "    4.1  Technology stack, project structure and interface screenshots  (3)",
    "    4.2  Database, ERD, data dictionary, validation, privacy and backup  (3)",
    "    4.3  API documentation  (2)",
    "    4.4  Installation, configuration and deployment  (2)",
    "Question 5  Evaluation and Responsible Use  [10]",
    "    5.1  Test summary - unit, integration, system and acceptance  (4)",
    "    5.2  Quantitative evaluation of NFRs and an emerging-technology metric  (2)",
    "    5.3  User guide and troubleshooting  (2)",
    "    5.4  Limitations, risks, ethics, maintenance and future improvements  (2)",
    "Reference List",
]:
    p = doc.add_paragraph()
    r = p.add_run(line)
    r.font.size = Pt(11); r.font.name = BODY_FONT
    if not line.startswith("    "):
        r.bold = True; r.font.color.rgb = NAVY
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(2)

h2("List of Figures and Tables")
for f in ["Figure 1: High-level system architecture",
          "Figure 2: Use-case diagram", "Figure 3: Class diagram",
          "Figure 4: Sequence diagram - UC03 upload legal document",
          "Figure 5: Activity diagram - end-to-end workflow",
          "Figure 6: Component diagram", "Figure 7: Deployment diagram",
          "Figure 8: State machine diagram - document lifecycle",
          "Figure 9: Entity-relationship diagram",
          "Figures 10-13: Implemented interface screenshots",
          "Table 2: Requirements traceability matrix",
          "Table 8: Data dictionary", "Table 11: Test summary"]:
    p = doc.add_paragraph(); r = p.add_run(f)
    r.font.size = Pt(10.5); r.font.name = BODY_FONT
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(2)

page_break()

# ================================================================ QUESTION 1
h1("Question 1: Final Project Baseline  [6 marks]")

h2("1.1  Title, purpose, users, emerging technology and executive summary  (2)")

rich([("Project title: ", True, False),
      ("LegalCase ERP - An AI-Assisted Legal Case Management Enterprise Resource Planning "
       "System for Small Law Firms.", False, False)])

h3("System purpose")
para("LegalCase ERP consolidates the case-management operations of a small legal practice into "
     "a single secure platform. It maintains a central database of matters, parties, documents, "
     "deadlines and correspondence; applies natural language processing to uploaded court "
     "documents in order to extract the legally significant entities and produce a plain-language "
     "summary; validates each document against the matter it was filed under and blocks "
     "mis-filed documents; converts extracted dates into diarised deadlines synchronised to "
     "Google Calendar or exported as an iCalendar feed; and exposes approved information to "
     "clients through a secure self-service portal.")

h3("Intended users")
make_table(
    ["User group", "Responsibility in the system"],
    [["Legal practitioner (Lawyer)",
      "Creates and maintains matters, uploads documents, reviews and approves AI output, manages deadlines and corresponds with clients."],
     ["Client",
      "Accesses a read-only portal scoped strictly to their own matters: approved summaries, shared documents, messages, deadlines and service rating."],
     ["System administrator",
      "Manages accounts and access revocation, inspects the tamper-evident audit trail, exports compliance reports, reviews rejected documents and approves model-retraining runs."]],
    widths=[26, 74])

h3("Emerging technology")
para("The emerging technology is artificial intelligence applied as natural language processing "
     "over unstructured legal text. It performs three functions inside the workflow: entity and "
     "date extraction; semantic validation of a document against its target matter; and "
     "three-paragraph summarisation. Every field returned carries a confidence score, the exact "
     "source text span and the page number, so that the output is explainable and auditable "
     "rather than opaque - a necessary property in a regulated legal environment.")

h3("Executive summary of the implemented solution")
para("The system has been implemented as a multi-module Java 21 application comprising a shared "
     "domain model, a Javalin REST backend and two independent front-end clients. The backend "
     "exposes approximately sixty REST endpoints secured by JSON Web Tokens carrying explicit "
     "scopes, persists to PostgreSQL in production and H2 in development, encrypts stored "
     "documents with AES-256-GCM, and processes uploads asynchronously on a worker pool so that "
     "the interface is never blocked by AI processing. A responsive browser client and a JavaFX "
     "desktop client consume the identical API, demonstrating genuine multi-platform "
     "interoperability. Real-time notification is delivered over Server-Sent Events.")
para("The complete end-to-end workflow is operational: a practitioner uploads a PDF or DOCX "
     "document, the AI pipeline extracts the entities with confidence scores and source spans, "
     "validates the document against the matter, generates a three-paragraph summary, derives "
     "deadline records from the extracted dates, and notifies the client in real time once the "
     "practitioner approves the summary. Documents that belong to a different matter or contain "
     "no recognisable case content are blocked with a stated reason and reported to the "
     "administrator. The implementation is verified by an automated suite of "
     + str(33) + " JUnit tests which all pass, supported by measured performance and accuracy "
     "results reported in Question 5.")

h2("1.2  Final aim, objectives, scope and approved changes  (2)")

h3("Final aim")
rich([("Aim: ", True, False),
      ("To design, implement and evaluate an affordable, AI-assisted legal case management ERP "
       "that centralises matter information, automates the extraction and diarising of legally "
       "significant dates, and gives clients transparent self-service access to the progress of "
       "their matters.", False, False)])

h3("Measurable objectives and final status")
make_table(
    ["No.", "Objective", "Target", "Final status"],
    [["OBJ-01", "Implement an NLP extraction pipeline identifying dates, parties, attorneys, judge, case number and court.",
      "Field-level precision >= 90%, recall >= 85%",
      "Achieved. Measured 100% precision and recall on the annotated evaluation set (Table 10); see the caveat on set size in 5.4."],
     ["OBJ-02", "Automate deadline capture and calendar synchronisation with reminders.",
      "Every extracted date becomes a diarised entry",
      "Achieved. Google Calendar sync and RFC 5545 .ics export both implemented and demonstrable."],
     ["OBJ-03", "Deliver a secure role-scoped client portal publishing approved summaries, documents and deadlines.",
      "Client satisfaction >= 4.0/5.0 from >= 20 users",
      "Partially achieved. The portal is fully implemented; the satisfaction measurement requires a user pilot that has not yet been conducted (see 5.1)."]],
    widths=[8, 34, 24, 34], font=8.5)

h3("Final scope")
rich([("In scope and delivered: ", True, False),
      ("matter and party records; document upload with consent capture, AES-256-GCM encryption "
       "and storage; AI extraction, validation and summarisation of machine-readable PDF and "
       "DOCX; deadline management with Google Calendar and .ics synchronisation; bidirectional "
       "secure messaging with real-time notification; a read-only client portal; client service "
       "ratings; role-based access control with account revocation; a tamper-evident hash-chained "
       "audit trail; administrative reporting including a rejection report; model-retraining "
       "governance; and an analytics dashboard with live-updating charts.", False, False)])
rich([("Out of scope: ", True, False),
      ("trust-account and billing (an integration point is exposed but accounting is not "
       "implemented); e-filing to court registries; optical character recognition of image-only "
       "scans; native mobile applications, mobile access being delivered through a responsive "
       "browser interface; and multi-firm tenancy.", False, False)])

h3("Changes since Assignment 2")
para("No change was made to the approved project topic or to the approved emerging technology. "
     "The following implementation-level refinements were made during development and are "
     "recorded here for transparency; none alters the approved scope.")
make_table(
    ["Change", "Reason", "Effect on requirements"],
    [["Two additional lower-priority functional requirements (FR-11 analytics dashboard, FR-12 retry and rating) were formalised.",
      "Both capabilities were implemented during development and required explicit acceptance criteria to be testable.",
      "Additive only. FR-01 to FR-10 are unchanged."],
     ["The prototype AI component is a deterministic rule-based engine behind the AiExtractionService interface rather than a fine-tuned transformer.",
      "A transformer requires an annotated legal corpus and GPU hosting that are outside the scope and budget of the module. The interface contract is identical, so the model can be substituted without changing any caller.",
      "ETR-01 to ETR-04 remain valid; the accuracy target is evaluated against the implemented engine."],
     ["Audit timestamps are truncated to microsecond precision before hashing.",
      "Defect found by integration test TC-I04: SQL TIMESTAMP columns store microseconds, so a nanosecond timestamp was silently rounded on write and the stored hash could not be recomputed from the persisted row.",
      "Corrects FR-09; the tamper-evidence guarantee is now verifiable."],
     ["The case-number pattern accepts colon-separated headings.",
      "Defect found by unit tests TC-U01 and TC-U05: the common real-world heading 'Case Number: GP/PTA/2026/4471' was not matched.",
      "Improves FR-02 recall; no requirement text changed."]],
    widths=[30, 42, 28], font=8.5)

h2("1.3  Document control, glossary and referenced artefacts  (2)")

h3("Document control")
make_table(
    ["Property", "Value"],
    [["Document title", "ISJ107V Assignment 3 - Project Documentation and UML Design"],
     ["System documented", "LegalCase ERP v1.0.0"],
     ["Version", "1.0"],
     ["Date", "30 September 2026"],
     ["Author", "Ally Mongala (219361629)"],
     ["Status", "Final - submitted for assessment"],
     ["Repository", "legal-case-erp - Git, " + str(GIT_COMMITS) + " commits. URL: https://github.com/<your-username>/legal-case-erp  [REPLACE WITH YOUR URL BEFORE SUBMITTING]"]],
    widths=[26, 74], font=9)

h3("Revision history")
make_table(
    ["Version", "Date", "Author", "Summary of change"],
    [["0.1", "29 June 2026", "A. Mongala", "Initial specification content drafted for Assignment 2."],
     ["0.2", "14 August 2026", "A. Mongala", "Implementation of backend, AI pipeline and both clients completed."],
     ["0.3", "16 September 2026", "A. Mongala", "UML model set produced; Assignment 2 specification finalised."],
     ["0.4", "20 September 2026", "A. Mongala", "Automated test suite, coverage measurement and performance harness added; two defects corrected."],
     ["1.0", "30 September 2026", "A. Mongala", "Final project documentation compiled for Assignment 3."]],
    widths=[10, 18, 16, 56], font=8.5)

h3("Glossary")
make_table(
    ["Term", "Definition"],
    [["AES-256-GCM", "Authenticated symmetric encryption used to protect stored documents; detects tampering as well as preventing disclosure."],
     ["Decider / extraction confidence", "A value in [0,1] returned with every extracted field indicating the engine's certainty."],
     ["Deadline", "A diarised date derived from an extracted date or captured manually, optionally synchronised to an external calendar."],
     ["Extraction", "A single field recovered from a document, carrying its value, confidence, verbatim source span and page number."],
     ["Hash chain", "An audit structure in which each entry stores a hash of its own content combined with the hash of its predecessor, making retrospective edits detectable."],
     ["HikariCP", "The JDBC connection pool used by the backend."],
     ["Javalin", "The lightweight Java web framework hosting the REST API on an embedded Jetty server."],
     ["JWT", "JSON Web Token; a signed bearer credential carrying the user's identity, role and scopes."],
     ["Matter / case", "A single item of legal work undertaken for a client."],
     ["NLP", "Natural language processing; the AI discipline applied here to unstructured legal text."],
     ["POPIA", "Protection of Personal Information Act 4 of 2013 (South Africa)."],
     ["REJECTED", "The document state assigned when AI validation determines the document does not belong to its target matter."],
     ["Scope (authorisation)", "A permission string carried in a JWT, for example read:client, write:case or admin:model."],
     ["SSE", "Server-Sent Events; a unidirectional HTTP push channel used for real-time notification."],
     ["Summary", "A three-paragraph plain-language brief structured as Facts, Legal Issues and Current Status."]],
    widths=[20, 80], font=8.5)

h3("Referenced project artefacts")
make_table(
    ["Artefact", "Location in the repository", "Purpose"],
    [["Source code", "shared/ · backend/ · frontend/", "Multi-module Maven implementation."],
     ["Database schema", "backend/src/main/resources/db/schema.sql", "Authoritative DDL for all 11 tables."],
     ["UML models (editable)", "docs/uml/*.puml · docs/uml/a2/*.puml · docs/uml/a3/*.puml", "PlantUML sources for every diagram in this document."],
     ["Automated test suite", "backend/src/test/java/...", "33 JUnit 5 unit, integration and measurement tests."],
     ["Test reports", "backend/target/surefire-reports/", "Machine-generated pass/fail results."],
     ["Coverage report", "backend/target/site/jacoco/index.html", "JaCoCo line and branch coverage."],
     ["Measured metrics", "docs/metrics/metrics-results.md", "Latency and accuracy figures cited in 5.2."],
     ["Controlled test documents", "docs/test-cases/ · docs/TEST_CASES.md", "Eight prepared documents with expected outcomes."],
     ["Deployment definition", "docker-compose.yml · backend/Dockerfile · deploy/nginx.conf", "Container topology."],
     ["CI workflow", ".github/workflows/build.yml", "Automated build and test pipeline."],
     ["Screenshots", "screenshots/", "Interface evidence used in Question 4."]],
    widths=[22, 38, 40], font=8.5)

page_break()

# ================================================================ QUESTION 2
h1("Question 2: Requirements and Traceability  [8 marks]")

h2("2.1  Final prioritised functional and non-functional requirements  (3)")
para("Requirements retain the identifiers assigned in Assignment 2. Priority follows the MoSCoW "
     "convention. The Status column records the verified state of the implementation at the time "
     "of submission.")

make_table(
    ["ID", "Pri.", "Requirement (abbreviated)", "Acceptance criterion", "Status"],
    [["FR-01", "Must", "Upload a PDF or DOCX of up to 25 MB and store it against a case in encrypted form.",
      "POST returns 202 with a document id; ciphertext written; document listed as PROCESSING.", "Pass"],
     ["FR-02", "Must", "Extract dates, parties, attorneys, judge, case number, court and amount, each with confidence, span and page.",
      "GET extractions returns each field with confidence in [0,1], non-empty span and valid page.", "Pass"],
     ["FR-03", "Must", "Generate a three-paragraph summary (Facts / Legal Issues / Current Status).",
      "Three non-empty paragraphs returned with a version and DRAFT status.", "Pass"],
     ["FR-04", "Must", "Validate each document against its case and block mis-filed documents as REJECTED with a reason.",
      "Wrong-matter document is set REJECTED, reason stored and displayed, spans highlighted, entry added to the rejection report.", "Pass"],
     ["FR-05", "Must", "Create deadlines from dates and synchronise to Google Calendar or export .ics.",
      "Calendar event id stored, or GET calendar.ics returns a valid RFC 5545 feed with VEVENT and VALARM.", "Pass"],
     ["FR-06", "Must", "Require practitioner approval before a summary is visible to the client.",
      "DRAFT summary is not returned to a client; after approval the status, approver and timestamp are recorded.", "Pass"],
     ["FR-07", "Must", "Provide a role-scoped client portal exposing only the client's own matters.",
      "A client requesting another client's case receives HTTP 403 and an audit entry is written.", "Pass"],
     ["FR-08", "Must", "Bidirectional case-level messaging with real-time unread notification.",
      "SSE new-message delivered in under one second; unread counter increments and clears on read.", "Pass"],
     ["FR-09", "Must", "Maintain a tamper-evident hash-chained audit log and export CSV reports.",
      "Chain verification reports intact; any altered row is detected; audit.csv returns the expected columns.", "Pass"],
     ["FR-10", "Should", "Administrator manages accounts, revokes access, reviews rejections and approves retraining.",
      "Revoked account cannot authenticate; revocation audited; approved run records the approver.", "Pass"],
     ["FR-11", "Should", "Analytics dashboard reporting caseload, deadlines due, documents processed and case status, updating live.",
      "Dashboard indicators update without reload on receipt of a deadline-updated event.", "Pass"],
     ["FR-12", "Could", "Retry failed AI processing; client submits a 1-5 service rating.",
      "Retry discards stale results and re-enters PROCESSING; rating persisted and averaged.", "Pass"],
     ["NFR-01", "Must", "Metadata queries within 2 s and full AI processing of a 20-page document within 10 s at p95.",
      "Measured p95 values at or below the stated budgets.", "Pass"],
     ["NFR-02", "Must", "99.5% monthly availability; nightly backup; RPO 24 h, RTO 4 h.",
      "Uptime measured over a calendar month; documented restore rehearsal within RTO.", "Not yet measured"],
     ["NFR-03", "Must", "AES-256-GCM at rest, TLS in transit, PBKDF2 passwords, scope-checked endpoints.",
      "No endpoint returns data without a valid scoped token; no reversible password stored.", "Pass"],
     ["NFR-04", "Must", "Trained practitioner completes the core workflow unaided in under 3 minutes; SUS >= 75.",
      "8 of 10 participants succeed within 3 minutes; SUS averages 75 or higher.", "Not yet measured"],
     ["NFR-05", "Should", "Single-command container deployment with externalised configuration and structured logs.",
      "A clean host reaches a healthy state with only environment variables supplied.", "Pass"],
     ["NFR-06", "Must", "POPIA compliance: recorded consent, data residency, subject export or erasure.",
      "Consent flag must be true before processing; region pinned; request fulfilled within 30 days.", "Partial"],
     ["ETR-01", "Must", "Extraction precision >= 90% and recall >= 85% on a held-out annotated set.",
      "Automated harness computes per-field precision and recall.", "Pass"],
     ["ETR-02", "Must", "Extraction and summarisation within 500 ms per page at p95.",
      "Load harness records per-page processing time.", "Pass"],
     ["ETR-03", "Must", "Block wrong-matter and non-legal documents with <= 5% false-rejection; return span and page for every field and rejection.",
      "Controlled suite blocks every invalid document with no valid document rejected.", "Pass"],
     ["ETR-04", "Should", "Record practitioner corrections as training data; no model promoted without administrator approval and rollback.",
      "Correction appears in the retraining dataset; run remains PENDING until approved.", "Partial"]],
    widths=[7, 7, 32, 39, 15], font=7.5)

para("Summary: of the 22 requirements, 18 are verified as Pass, 2 are Partial and 2 are not yet "
     "measured because they require a user pilot and a month of production operation "
     "respectively. Section 5.4 discusses these openly.", italic=True, size=9.5)

h2("2.2  Requirements traceability matrix  (3)")
para("Table 2 maps every requirement to the UML element that models it, the implementation module "
     "that realises it, the test case that verifies it and its completion status. Test case "
     "prefixes are TC-U (unit), TC-I (integration), TC-S (system / end-to-end) and PT "
     "(performance).")

make_table(
    ["Requirement", "UML reference", "Implementation module", "Test case", "Status"],
    [["FR-01", "UC03 (Fig. 2); Fig. 4 seq.", "DocumentController, DocumentService, EncryptionUtil", "TC-S01", "Pass"],
     ["FR-02", "UC04; ExtractionEngine (Fig. 3)", "nlp.ExtractionEngine", "TC-U01, TC-U02, TC-U03, TC-U08", "Pass"],
     ["FR-03", "UC06; Summary (Fig. 3)", "nlp.Summarizer, SummaryService", "TC-S02", "Pass"],
     ["FR-04", "UC05, UC20; Fig. 8 state", "ExtractionEngine.validateAgainstCase, AiPipeline", "TC-S03, TC-U06", "Pass"],
     ["FR-05", "UC08, UC09, UC10", "DeadlineService, CalendarGateway", "TC-S04", "Pass"],
     ["FR-06", "UC07; Fig. 8 state", "SummaryController, SummaryService", "TC-S05", "Pass"],
     ["FR-07", "UC12, UC13", "ClientPortalController, JwtUtil scopes", "TC-U19, TC-U20, TC-S06", "Pass"],
     ["FR-08", "UC11; EventBus (Fig. 5)", "MessageService, EventBus", "TC-S07", "Pass"],
     ["FR-09", "UC16; AuditLog (Fig. 3)", "AuditService", "TC-I01 - TC-I08", "Pass"],
     ["FR-10", "UC15, UC17", "AdminController, UserService, TrainingService", "TC-S08", "Pass"],
     ["FR-11", "UC18", "MetricsService, EventBus, web dashboard", "TC-S09", "Pass"],
     ["FR-12", "UC19, UC14", "AiPipeline.retry, RatingService", "TC-S10", "Pass"],
     ["NFR-01", "Fig. 1 architecture", "Javalin API layer, HikariCP", "PT-01", "Pass"],
     ["NFR-02", "Fig. 7 deployment", "PostgreSQL volume, backup scripts", "Not yet measured", "Not met"],
     ["NFR-03", "Fig. 1; auth package (Fig. 6)", "JwtUtil, PasswordHasher, EncryptionUtil", "TC-U10 - TC-U21, PT-03", "Pass"],
     ["NFR-04", "Fig. 2 actors", "Browser and JavaFX clients", "Pending user pilot", "Not met"],
     ["NFR-05", "Fig. 7 deployment", "Dockerfile, docker-compose.yml", "TC-S11", "Pass"],
     ["NFR-06", "UC03, UC16", "consent_granted column, AuditService", "TC-I01", "Partial"],
     ["ETR-01", "UC04; AiExtractionService (Fig. 3)", "nlp.ExtractionEngine", "PT-02", "Pass"],
     ["ETR-02", "UC04, UC06", "AiPipeline, ExtractionEngine", "PT-01", "Pass"],
     ["ETR-03", "UC05, UC20", "ExtractionEngine, Extraction", "TC-U04, TC-U05, TC-S03", "Pass"],
     ["ETR-04", "UC17", "TrainingService, Dataset", "TC-S08", "Partial"]],
    widths=[12, 22, 30, 24, 12], font=8)

h2("2.3  Satisfaction of the three emerging-technology requirements  (2)")

h3("ETR-01 - extraction accuracy")
para("The requirement demands field-level precision of at least 90% and recall of at least 85%. "
     "The implementation satisfies this through the rule-based extraction engine, which applies "
     "validated pattern recognition to dates, party lines, case numbers, courts, attorneys, "
     "judges and monetary amounts, rejecting candidate dates that are not real calendar dates. "
     "Accuracy is measured automatically by test PT-02 against a human-annotated gold standard, "
     "which recorded 7 of 7 annotated fields correctly extracted with no misses - 100% precision "
     "and 100% recall on that set. The size of the evaluation set is a genuine limitation and is "
     "stated as such in section 5.4.")

h3("ETR-02 - inference latency")
para("The requirement demands a complete result within 500 milliseconds per page at the 95th "
     "percentile. Measurement PT-01 processed documents of 1, 5, 10 and 20 pages over 200 "
     "iterations each after warm-up. The worst measured per-page figure was 2.80 ms for a "
     "single-page document and 0.29 ms per page for a 20-page document, which is more than two "
     "orders of magnitude inside the budget. The margin exists because the implemented engine is "
     "deterministic rather than a neural model; section 5.4 notes that substituting a transformer "
     "would consume most of this headroom.")

h3("ETR-03 - validation integrity and explainability")
para("The requirement demands that wrong-matter and non-legal documents be blocked with no more "
     "than 5% false rejection of correctly filed documents, and that every extracted field and "
     "every rejection carry a source span and page number. Both halves are satisfied. The "
     "explainability contract is enforced by unit test TC-U04, which asserts for every returned "
     "extraction that the confidence lies in [0,1], the span is non-empty and the page number is "
     "at least 1. The blocking behaviour is verified by the controlled document suite of eight "
     "prepared documents - correctly filed PDFs and DOCX files, wrong-matter documents, documents "
     "with no case content and a scanned image-only PDF - which records 8 of 8 scenarios reaching "
     "their expected terminal state with no valid document rejected, giving a measured "
     "false-rejection rate of 0%.")

page_break()

# ================================================================ QUESTION 3
h1("Question 3: Final Design as Implemented  [16 marks]")
para("All diagrams use consistent actor, class, component and requirement names, and correspond "
     "to the implemented system. Editable PlantUML sources accompany each figure in docs/uml/.",
     italic=True, size=10)

h2("3.1  High-level system architecture  (2)")
para("The system implements a three-tier, service-oriented architecture with an asynchronous "
     "worker pattern for document processing. The presentation tier holds two independent clients "
     "that share one REST contract. The application tier separates transport and authorisation "
     "concerns in the controllers from business logic in the services, and isolates the "
     "emerging-technology component behind an interface. The data tier separates structured "
     "records in the relational database from encrypted document payloads on the storage volume. "
     "External integration with Google Calendar is reached through a gateway abstraction that "
     "also has a local iCalendar implementation, so calendar functionality degrades gracefully "
     "rather than failing when the external service is unavailable.")
landscape_figure(os.path.join(UML_A3, "a3-architecture.png"),
                 "Figure 1: High-level system architecture showing users, interfaces, services, data stores, external APIs and the emerging-technology component")

h2("3.2  Use-case diagram and detailed use-case description  (3)")
para("The use-case model contains twenty use cases grouped by functional area within the system "
     "boundary. Primary actors are the Lawyer, Client and System Administrator, all generalising "
     "from an abstract Firm User that holds shared authentication behaviour. Supporting actors "
     "are the AI Extraction Service, the Google Calendar API and the Server-Sent Events "
     "notification service.")
landscape_figure(os.path.join(UML_A2, "a2-usecase.png"),
                 "Figure 2: Use-case diagram - system boundary, actors, include, extend and generalisation relationships")

h3("Detailed use-case description: UC03 Upload Legal Document")
make_table(
    ["Field", "Description"],
    [["Use-case ID", "UC03"],
     ["Name", "Upload Legal Document"],
     ["Primary actor", "Lawyer (legal practitioner)"],
     ["Supporting actors", "AI Extraction Service (emerging technology); Notification Service (SSE)"],
     ["Requirements", "FR-01, FR-02, FR-03, FR-04; ETR-01, ETR-02, ETR-03; NFR-03"],
     ["Description", "A practitioner uploads a court document against an existing matter. The system stores it in encrypted form and processes it asynchronously: extracting the legally significant entities, validating the document against the matter, and generating a three-paragraph summary."],
     ["Preconditions", "The practitioner is authenticated and holds the write:case scope; the target case exists; the client's processing consent has been recorded."],
     ["Trigger", "The practitioner selects a file and submits the upload form on the case detail screen."],
     ["Postconditions (success)", "The document is stored as PROCESSED; extractions, a draft summary and derived deadlines are persisted; a hash-chained audit entry is written; the interface is updated by a document-processed event."]],
    widths=[22, 78], font=8.5)

h3("Main flow")
for i, step in enumerate([
    "The practitioner opens the case and selects a PDF or DOCX file of up to 25 MB.",
    "The practitioner confirms that the client's processing consent has been obtained.",
    "The system validates the MIME type, file size and the practitioner's write:case scope.",
    "The system encrypts the payload with AES-256-GCM and writes the ciphertext to the document store.",
    "The system creates the document record with status PROCESSING and returns HTTP 202 Accepted.",
    "The system enqueues a processing job on the AI worker pool and the interface displays a processing indicator.",
    "The worker extracts the raw text page by page using PDFBox or Apache POI.",
    "The worker invokes the AI extraction service, which returns the structured fields, each with a confidence score, verbatim source span and page number.",
    "The worker retrieves the target case record and invokes validation, which confirms that the document belongs to that matter.",
    "The worker invokes summarisation, which returns the three-paragraph brief.",
    "The system persists the extractions, the draft summary and the deadlines derived from the extracted dates, sets the status to PROCESSED and writes a hash-chained audit entry.",
    "The system publishes a document-processed event and the interface displays the extracted fields, their confidence scores and the draft summary for review."], 1):
    numbered(step)

h3("Alternative and exception flows")
make_table(
    ["Ref.", "Condition", "System response"],
    [["A1", "At step 3 the file type is unsupported or the file exceeds 25 MB.",
      "The upload is refused with a descriptive validation message; no record is created. The use case terminates."],
     ["A2", "At step 3 the consent flag is absent.",
      "Processing is refused under NFR-06; the practitioner is prompted to record consent first."],
     ["A3", "At step 7 the document yields no machine-readable text (an image-only scan).",
      "The status is set to FAILED with the reason 'No extractable text - scan requires OCR'; an audit entry is written and a Retry AI action is offered (UC19)."],
     ["A4", "At step 9 validation determines the document belongs to a different matter or contains no case content.",
      "The status is set to REJECTED with a stated reason; the conflicting spans are highlighted; the rejection is added to the administrator report; the extractions are retained for diagnosis. Extends to UC20."],
     ["A5", "At step 12 the practitioner judges an extracted field incorrect.",
      "The practitioner corrects the field; the correction is stored as a labelled training example under ETR-04."],
     ["E1", "The database is unavailable during step 11.",
      "The transaction is rolled back, the document remains PROCESSING and the job is retryable; the error is logged with a correlation identifier."]],
    widths=[8, 38, 54], font=8.5)

h2("3.3  Class diagram  (3)")
para("The domain model centres on the abstract User class, from which Lawyer, Client and "
     "Administrator inherit. Case is the aggregate root: it stands in composition with "
     "LegalDocument and Deadline, which have no meaning independently of their matter, and in "
     "aggregation with ClientMessage. LegalDocument is in composition with Extraction. The "
     "emerging-technology component appears as the AiExtractionService interface realised by "
     "ExtractionEngine; AiPipeline depends on the interface rather than the implementation, which "
     "is what makes the model substitutable. CalendarGateway follows the same pattern with two "
     "realisations. AuditLog carries the prevHash, hash and verifyChain members that implement "
     "FR-09.")
landscape_figure(os.path.join(UML_A2, "a2-class.png"),
                 "Figure 3: Class diagram - implemented domain classes, attributes, operations, associations and multiplicities")

h2("3.4  Sequence diagram  (3)")
para("Figure 4 shows the time-ordered interaction for UC03 across all four layers: the user "
     "interface boundary, the application logic represented by DocumentController and AiPipeline, "
     "the data service, and the emerging-technology component. The controller returns HTTP 202 "
     "immediately after persisting the encrypted file, so a slow model never blocks the "
     "interface. Two nested alternative fragments carry the failure and rejection paths.")
landscape_figure(os.path.join(UML_A2, "a2-sequence.png"),
                 "Figure 4: Sequence diagram - UC03 upload legal document across interface, control, data and emerging-technology components")

h2("3.5  Activity diagram  (2)")
para("Figure 5 models the end-to-end workflow from upload to client notification across six "
     "partitions. Four decision nodes create the alternative paths: unsupported file or missing "
     "consent; no machine-readable text; the emerging-technology validation decision; and the "
     "choice between Google Calendar synchronisation and iCalendar export. The human-in-the-loop "
     "review loop returns the practitioner to editing until the summary is accurate, with each "
     "correction stored as a retraining example. The diagram terminates at four distinct "
     "completion states.")
landscape_figure(os.path.join(UML_A2, "a2-activity.png"),
                 "Figure 5: Activity diagram - end-to-end workflow with decisions, exception paths and completion states")

h2("3.6  Component and deployment diagrams  (3)")
para("Figure 6 shows the software dependency structure. The three Maven modules are visible: "
     "shared holds the domain model on which both other modules depend; backend contains the api, "
     "service, nlp, calendar, auth and db packages; frontend contains the JavaFX client. The "
     "diagram records the provided and required interfaces that decouple the layers - the REST "
     "contract consumed by both clients, AiExtractionService isolating the emerging technology, "
     "CalendarGateway isolating external calendar integration, and the JDBC DataSource.")
landscape_figure(os.path.join(UML_A3, "a3-component.png"),
                 "Figure 6: Component diagram - module structure, packages and the interfaces that decouple them")

para("Figure 7 shows the physical execution environment. The deployment is containerised and "
     "orchestrated with Docker Compose across three services that may each be placed on a "
     "separate host: an nginx reverse proxy terminating TLS with response buffering disabled on "
     "the event stream; the Java application container built from a multi-stage Dockerfile onto "
     "an Eclipse Temurin 21 JRE base image; and a PostgreSQL 16 container with a persistent named "
     "volume. Host addresses and credentials are injected as environment variables "
     "(LCE_DB_URL, LCE_DB_USER, LCE_DB_PASSWORD), which is what allows the same compose "
     "definition to run co-located during development and distributed across separate machines "
     "in deployment.")
landscape_figure(os.path.join(UML_A3, "a3-deployment.png"),
                 "Figure 7: Deployment diagram - devices, containers, artifacts and communication paths with protocols")

para("Figure 8 supplements the behavioural models with the document processing lifecycle, which "
     "is central to the system's validation behaviour and is referenced throughout this document.")
landscape_figure(os.path.join(UML_A3, "a3-state.png"),
                 "Figure 8: State machine diagram - LegalDocument processing lifecycle")

page_break()

# ================================================================ QUESTION 4
h1("Question 4: Implementation Documentation  [10 marks]")

h2("4.1  Technology stack, project structure and modules  (3)")

h3("Technology stack")
make_table(
    ["Layer", "Technology", "Version", "Justification"],
    [["Language / runtime", "Java (LTS)", "21", "Long-term support release with a mature enterprise deployment record."],
     ["Web framework", "Javalin (embedded Jetty)", "6.3.0", "Lightweight; ships as a single executable JAR with no application server to administer."],
     ["Database", "PostgreSQL / H2", "16 / 2.2", "ACID guarantees for a legal record; H2 in PostgreSQL-compatibility mode runs the identical schema in CI."],
     ["Connection pool", "HikariCP", "5.1.0", "Low-latency pooling with sane defaults."],
     ["Authentication", "JJWT", "0.12.6", "Signed JWTs carrying explicit scopes."],
     ["PDF text extraction", "Apache PDFBox", "2.0.31", "Per-page text access, required to attach page numbers for explainability."],
     ["DOCX text extraction", "Apache POI", "5.2.5", "Mature, permissively licensed Office format support."],
     ["Desktop client", "JavaFX", "21", "Demonstrates multi-platform interoperability against the same API."],
     ["Browser client", "HTML5 / CSS3 / ES2022", "-", "No installation required; inline SVG charts avoid a heavy framework dependency."],
     ["Build", "Apache Maven", "3.9", "Multi-module build enforcing the module separation."],
     ["Testing", "JUnit 5 · Surefire · JaCoCo", "5.10.2 · 3.2.5 · 0.8.12", "Automated verification with coverage measurement."],
     ["Containerisation", "Docker · Docker Compose", "-", "Reproducible, distributable deployment."],
     ["CI", "GitHub Actions", "-", "Builds and tests every commit."]],
    widths=[18, 22, 16, 44], font=8)

h3("Project structure and module responsibilities")
code_block([
    "legal-case-erp/",
    "├── pom.xml                     Parent POM: module list, JUnit/Surefire/JaCoCo",
    "├── shared/                     MODULE 1 - domain model, no dependencies",
    "│   └── model/                  Case, LegalDocument, Extraction, Summary,",
    "│                               Deadline, ClientMessage, Rating, User, Role …",
    "├── backend/                    MODULE 2 - REST API and business logic",
    "│   ├── api/                    Controllers: transport + authorisation only",
    "│   ├── service/                Business logic: CaseService, DocumentService,",
    "│   │                           AiPipeline, DeadlineService, AuditService, EventBus",
    "│   ├── nlp/                    EMERGING TECHNOLOGY: ExtractionEngine,",
    "│   │                           Summarizer, DocumentTextExtractor",
    "│   ├── calendar/               CalendarGateway + Google and local implementations",
    "│   ├── auth/                   JwtUtil, PasswordHasher",
    "│   ├── security/               EncryptionUtil (AES-256-GCM)",
    "│   ├── db/                     Database bootstrap, HikariCP pool",
    "│   ├── resources/db/           schema.sql - authoritative DDL",
    "│   ├── resources/web/          Browser client (index.html, app.js, app.css)",
    "│   └── test/java/              33 JUnit 5 unit, integration and measurement tests",
    "├── frontend/                   MODULE 3 - JavaFX desktop client",
    "├── docs/                       UML sources, test cases, metrics, assignments",
    "├── deploy/nginx.conf           Reverse-proxy configuration",
    "├── docker-compose.yml          Three-service container topology",
    "└── .github/workflows/          CI pipeline",
])

make_table(
    ["Module", "Responsibility", "Depends on"],
    [["shared", "Plain domain objects and enumerations used by every other module. Deliberately dependency-free so that both the server and the desktop client can hold the same types.", "-"],
     ["backend", "REST API, security, persistence, the AI pipeline, calendar integration, real-time events and the browser client resources.", "shared"],
     ["frontend", "JavaFX desktop client; contains no business logic and communicates only through the REST API.", "shared"]],
    widths=[14, 66, 20], font=8.5)

h3("Implemented interface screenshots")
for fn, cap in [
    ("final_dashboard_charts_clean.png", "Figure 10: Lawyer dashboard - KPI cards, the deadlines-per-month chart and the cases-by-status chart. Both charts are clickable and update live on receipt of a deadline-updated event (FR-11)"),
    ("web_v3_auto_extraction.png", "Figure 11: Case detail with AI output - extracted fields carry a confidence score, source span and page number (FR-02, ETR-03), and two mis-filed documents are shown BLOCKED with their stated rejection reasons (FR-04)"),
    ("final_client_bell_fixed.png", "Figure 12: Client portal messaging - the secure bidirectional thread between client and attorney, with unread state cleared on reading (FR-08)"),
    ("final_admin_rejections.png", "Figure 13: Administrator rejection report - every document blocked by AI validation, with its reason, available for review and CSV export (FR-04, FR-10)"),
]:
    path = os.path.join(SHOTS, fn)
    if os.path.exists(path):
        add_image(path, cap, max_h_cm=10.5)

h2("4.2  Database, ERD, data dictionary, validation, privacy and backup  (3)")

para("The system uses a relational database because a legal record requires ACID transactional "
     "guarantees and referential integrity. PostgreSQL 16 is used in production and H2 2.2 in "
     "PostgreSQL-compatibility mode for development and continuous integration, so the identical "
     "DDL runs in both. The schema comprises eleven tables.")

landscape_figure(os.path.join(UML_A3, "a3-erd.png"),
                 "Figure 9: Entity-relationship diagram - eleven tables with primary keys, foreign keys, unique constraints and cardinalities")

h3("Data dictionary (principal entities)")
make_table(
    ["Table.Column", "Type", "Constraint", "Description", "Privacy class"],
    [["users.id", "VARCHAR(32)", "PK", "Surrogate identifier.", "Internal"],
     ["users.email", "VARCHAR(200)", "UNIQUE, NOT NULL", "Login identifier and contact address.", "Personal"],
     ["users.password_hash", "VARCHAR(400)", "NOT NULL", "PBKDF2 hash as iterations$salt$hash. Never reversible.", "Credential"],
     ["users.role", "VARCHAR(20)", "NOT NULL", "LAWYER, CLIENT or ADMIN; determines the JWT scope set.", "Internal"],
     ["users.active", "BOOLEAN", "DEFAULT TRUE", "Set false by administrator revocation; blocks authentication.", "Internal"],
     ["cases.reference", "VARCHAR(50)", "UNIQUE, NOT NULL", "Human-readable matter reference.", "Internal"],
     ["cases.client_id", "VARCHAR(32)", "FK → users.id", "Owning client; the basis of portal scoping.", "Personal"],
     ["cases.status", "VARCHAR(40)", "-", "OPEN, IN_PROGRESS, ON_HOLD, CLOSED or ARCHIVED.", "Internal"],
     ["cases.amount_involved", "DECIMAL(18,2)", "-", "Monetary value in dispute.", "Confidential"],
     ["documents.case_id", "VARCHAR(32)", "FK → cases.id", "Owning matter; documents are deleted with the case.", "Internal"],
     ["documents.storage_path", "VARCHAR(600)", "-", "Path to the AES-256-GCM ciphertext; never the plaintext.", "Confidential"],
     ["documents.status", "VARCHAR(20)", "-", "PENDING, PROCESSING, PROCESSED, REJECTED or FAILED (Figure 8).", "Internal"],
     ["documents.rejection_reason", "VARCHAR(400)", "NULL", "Human-readable reason when blocked by AI validation.", "Internal"],
     ["documents.consent_granted", "BOOLEAN", "-", "Must be true before processing may begin (NFR-06).", "Compliance"],
     ["extractions.confidence", "DOUBLE", "0.0 - 1.0", "Model certainty for the field (ETR-03).", "Internal"],
     ["extractions.span_text", "VARCHAR(600)", "-", "Verbatim source text justifying the value (explainability).", "Confidential"],
     ["extractions.page_number", "INT", ">= 1", "Source page, 1-based.", "Internal"],
     ["summaries.status", "VARCHAR(20)", "-", "DRAFT or APPROVED; only APPROVED is client-visible (FR-06).", "Internal"],
     ["deadlines.deadline_date", "DATE", "valid date", "Diarised court or filing date.", "Confidential"],
     ["messages.read_by_client", "BOOLEAN", "DEFAULT FALSE", "Drives the unread notification counter (FR-08).", "Internal"],
     ["ratings.score", "INT", "1 - 5", "Client service rating.", "Personal"],
     ["audit_log.prev_hash", "VARCHAR(64)", "-", "SHA-256 of the preceding entry; empty for the genesis row.", "Compliance"],
     ["audit_log.hash", "VARCHAR(64)", "-", "SHA-256 of this entry's content plus prev_hash (FR-09).", "Compliance"]],
    widths=[20, 13, 15, 40, 12], font=7.5)

h3("Validation rules")
make_table(
    ["Level", "Rule"],
    [["Transport", "Reject a missing, malformed or expired JWT; reject an insufficient scope with HTTP 403; reject an unsupported MIME type; reject a file above 25 MB; reject a malformed JSON body."],
     ["Domain", "Case reference must be unique; a deadline date must be a real calendar date; a rating must lie between 1 and 5; a summary must contain exactly three non-empty paragraphs; consent must be recorded before processing begins; only a lawyer may approve a summary."],
     ["AI", "A candidate date is accepted only if it parses as a genuine calendar date; extracted parties and case number are cross-validated against the target case, and a mismatch blocks the document."],
     ["Database", "Primary-key, foreign-key, unique and not-null constraints provide the final integrity guarantee; indexes exist on every foreign key used in list queries."]],
    widths=[14, 86], font=8.5)

h3("Privacy controls")
para("Legal files contain personal information and privileged communications, so the design "
     "follows POPIA. Processing is lawful only where the consent flag is recorded against the "
     "document. Collection is minimised to the fields necessary for case management. Access is "
     "purpose-limited by role scope, so a client can retrieve only their own matters and only "
     "approved summaries and explicitly shared documents. Documents are encrypted at rest with "
     "AES-256-GCM and in transit with TLS. Passwords are stored only as PBKDF2 hashes. Data "
     "residency is addressed by pinning the deployment region. The hash-chained audit log "
     "provides the accountability evidence the Act requires of a responsible party.")

h3("Backup approach")
make_table(
    ["Aspect", "Approach"],
    [["Database", "Nightly logical dump with 30-day retention at minimum specification; hourly incremental with point-in-time recovery and 12-month retention at recommended specification."],
     ["Document store", "Backed up on the same schedule with object versioning enabled, so an accidental deletion is recoverable independently of the database."],
     ["Location and protection", "Written to storage geographically separate from the primary host and encrypted; keys held in a managed secret store, not alongside the data."],
     ["Objectives", "Recovery point objective 24 hours (1 hour recommended); recovery time objective 4 hours."],
     ["Application recovery", "All infrastructure is declared in the Dockerfile and compose file, so recovery is redeployment of the containers against a restored volume."],
     ["Verification", "A restore rehearsal is scheduled quarterly and the audit hash chain is re-verified afterwards to confirm the recovered record is intact. This rehearsal has not yet been performed (see 5.4)."]],
    widths=[20, 80], font=8.5)

h2("4.3  API documentation  (2)")
para("Data is exchanged over a stateless REST/JSON API secured by bearer JSON Web Tokens carrying "
     "explicit scopes. Representative endpoints are documented below; the full surface comprises "
     "approximately sixty endpoints across ten controllers.")

make_table(
    ["Method and path", "Input", "Output", "Auth scope", "Errors"],
    [["POST /api/auth/login", "{email, password}", "{token, role, name}", "public",
      "401 invalid credentials; 403 account revoked"],
     ["GET /api/cases?q=", "optional search term", "array of case summaries", "read:case",
      "401 no token; 403 wrong scope"],
     ["POST /api/cases", "{reference, title, clientId, matterType, court}", "201 created case", "write:case",
      "400 validation; 409 duplicate reference"],
     ["POST /api/cases/{id}/documents", "multipart file, consent flag", "202 {documentId, status}", "write:case",
      "400 unsupported type or size; 404 case; 422 consent missing"],
     ["GET /api/documents/{id}/extractions", "path id", "array {field, value, confidence, span, page}", "write:case / read:client",
      "403 not the owner; 404 document"],
     ["POST /api/documents/{id}/retry", "path id", "202 re-queued", "write:case",
      "409 not in FAILED state"],
     ["GET /api/cases/{id}/summaries", "path id", "versioned summaries", "write:case / read:client",
      "403 DRAFT hidden from client"],
     ["POST /api/summaries/{id}/approve", "path id", "200 approved summary", "write:case",
      "403 client may not approve; 404 summary"],
     ["POST /api/cases/{id}/calendar-sync", "{reminderDays}", "created event identifiers", "write:case",
      "502 Google API unavailable (falls back to .ics)"],
     ["GET /api/cases/{id}/calendar.ics", "path id", "text/calendar RFC 5545", "write:case / read:client",
      "404 case"],
     ["POST /api/cases/{id}/messages", "{subject, body}", "201 message", "write:case / write:client",
      "400 empty body; 403 not a party"],
     ["GET /api/events", "Accept: text/event-stream", "SSE new-message, deadline-updated", "any authenticated",
      "401 no token; 406 wrong Accept header"],
     ["GET /api/admin/rejections", "-", "blocked documents with reasons", "admin:model", "403 non-admin"],
     ["POST /api/admin/users/{id}/revoke", "path id", "200 account disabled", "admin:model",
      "403 non-admin; 404 user"],
     ["GET /api/admin/reports/audit.csv", "optional date range", "CSV export", "admin:model", "403 non-admin"]],
    widths=[26, 18, 22, 16, 18], font=7.5)

h3("Authentication, validation and error handling")
para("Every request other than login and the static web assets must present an Authorization "
     "header of the form 'Bearer <token>'. The token is validated on each request: the signature "
     "is checked against the server secret, the expiry is enforced, and the scope required by the "
     "endpoint is compared against the scope set embedded in the token. A missing or invalid "
     "token yields HTTP 401; a valid token lacking the required scope yields HTTP 403 and writes "
     "an authorisation-failure entry to the audit log. Validation failures yield HTTP 400 with a "
     "descriptive message naming the offending field. Requests for absent resources yield HTTP "
     "404. Unhandled server errors yield HTTP 500 with a correlation identifier that appears in "
     "the structured log, and never expose a stack trace to the client. Failures of the external "
     "calendar service are caught and degraded to local .ics generation rather than propagated.")

h2("4.4  Installation, configuration and deployment  (2)")

h3("Minimum and recommended requirements")
make_table(
    ["Component", "Minimum", "Recommended"],
    [["Application host - CPU", "2 vCPU x86-64 at 2.0 GHz", "4 vCPU at 2.8 GHz"],
     ["Application host - memory", "4 GB RAM (2 GB JVM heap)", "8 GB RAM (4 GB heap)"],
     ["Application host - storage", "40 GB SSD", "100 GB NVMe SSD"],
     ["Database host", "2 vCPU, 4 GB RAM, 100 GB SSD", "4 vCPU, 8 GB RAM, 500 GB NVMe with PITR"],
     ["Software", "JDK 21, Maven 3.9, Docker 24 with Compose v2", "As minimum plus a TLS certificate and a managed secret store"],
     ["Client", "Any current browser; 1366x768; 5 Mbps", "1920x1080; 20 Mbps"],
     ["Network", "10 Mbps symmetric, static public IP", "100 Mbps symmetric, redundant uplink"]],
    widths=[26, 36, 38], font=8.5)

h3("Local installation (development, H2)")
code_block([
    "# 1. Obtain the source",
    "git clone https://github.com/<your-username>/legal-case-erp.git",
    "cd legal-case-erp",
    "",
    "# 2. Build all three modules and run the test suite",
    "mvn clean install",
    "",
    "# 3. Start the backend (serves the browser client on the same port)",
    "java -jar backend/target/legal-backend.jar",
    "#    or:  ./run-backend.sh      (Linux/macOS)",
    "#    or:  run-backend.bat       (Windows)",
    "",
    "# 4. Open the application",
    "#    http://localhost:8080",
    "",
    "# Optional: launch the JavaFX desktop client against the running backend",
    "./run-connected.sh",
])

h3("Containerised deployment (production, PostgreSQL)")
code_block([
    "# Build and start the three-container topology",
    "docker compose up --build",
    "",
    "# Services started:",
    "#   web      nginx:1.27-alpine      ports 8080->80, TLS termination",
    "#   backend  Temurin 21 JRE         Javalin API + AI pipeline",
    "#   db       postgres:16-alpine     database legalcase, persistent volume",
    "",
    "# Verify",
    "curl -s http://localhost:8080/api/health",
])

h3("Configuration")
make_table(
    ["Environment variable", "Default", "Purpose"],
    [["LCE_DB_URL", "jdbc:h2:file:./data/erpdb", "JDBC URL. Set to jdbc:postgresql://<host>:5432/legalcase for production."],
     ["LCE_DB_USER", "sa", "Database user."],
     ["LCE_DB_PASSWORD", "(empty)", "Database password. Supply from a secret store, never in source control."],
     ["LCE_JWT_SECRET", "generated", "HMAC signing key for JSON Web Tokens."],
     ["LCE_ENC_KEY", "generated", "AES-256-GCM key for document encryption at rest."],
     ["LCE_DEMO_AUTH", "false", "Enables the demonstration login shortcut; must remain false in production."],
     ["PORT", "8080", "HTTP listen port."]],
    widths=[22, 20, 58], font=8.5)

para("Placing the database on a separate host requires only that LCE_DB_URL point at that host "
     "and that the PostgreSQL container publish port 5432 to the application host; no code change "
     "is required, which is the property that makes the topology in Figure 7 distributable.")

h3("Demonstration accounts")
make_table(
    ["Role", "Email", "Password"],
    [["Lawyer", "ally@mongalalaw.co.za", "lawyer123"],
     ["Client", "thabo@example.com", "client123"],
     ["Administrator", "admin@mongalalaw.co.za", "admin123"]],
    widths=[20, 45, 35], font=9)

page_break()

# ================================================================ QUESTION 5
h1("Question 5: Evaluation and Responsible Use  [10 marks]")

h2("5.1  Test summary  (4)")
para("Testing is organised into four levels. Unit and integration tests are automated with JUnit "
     "5 and execute on every build through Maven Surefire; system tests exercise the running "
     "service end to end; acceptance testing is discussed honestly below. The complete automated "
     "suite currently reports 33 tests executed with 0 failures and 0 errors.")

h3("Automated suite results")
make_table(
    ["Level", "Test class", "Tests", "Passed", "Failed", "Evidence"],
    [["Unit", "ExtractionEngineTest (AI/NLP component)", "9", "9", "0", "surefire-reports/…ExtractionEngineTest.xml"],
     ["Unit", "SecurityTest (PBKDF2 and JWT scopes)", "12", "12", "0", "surefire-reports/…SecurityTest.xml"],
     ["Integration", "AuditChainIntegrationTest (H2 database)", "8", "8", "0", "surefire-reports/…AuditChainIntegrationTest.xml"],
     ["Performance", "PerformanceMetricsTest (measurement harness)", "4", "4", "0", "docs/metrics/metrics-results.md"],
     ["TOTAL", "All four suites", "33", "33", "0", "mvn test"]],
    widths=[12, 34, 9, 9, 9, 27], font=8.5)

h3("Representative test cases")
make_table(
    ["ID", "Level", "Test case", "Expected result", "Actual result", "Status"],
    [["TC-U01", "Unit", "Extract the case number from a pleading headed 'Case Number: GP/PTA/2026/4471'.",
      "Field 'Case Number' returned with value GP/PTA/2026/4471.", "As expected (after defect correction).", "Pass"],
     ["TC-U04", "Unit", "Every returned extraction carries confidence in [0,1], a non-empty span and a page >= 1.",
      "Explainability contract satisfied for all fields.", "As expected for all extractions.", "Pass"],
     ["TC-U05", "Unit", "A case number appearing on page 2 of a two-page document is reported as page 2.",
      "pageNumber = 2.", "As expected (after defect correction).", "Pass"],
     ["TC-U06", "Unit", "A grocery list yields no case number and no court.", "No case fields returned.", "As expected.", "Pass"],
     ["TC-U11", "Unit", "An incorrect password is rejected.", "verify() returns false.", "As expected.", "Pass"],
     ["TC-U16", "Unit", "A token signed with a different secret is rejected.", "parse() returns null.", "As expected.", "Pass"],
     ["TC-U18", "Unit", "An expired token is rejected.", "parse() returns null.", "As expected.", "Pass"],
     ["TC-U19", "Unit", "A client token carries neither write:case nor admin:model.", "Scope set restricted to read/write:client.", "As expected.", "Pass"],
     ["TC-I03", "Integration", "Each audit entry chains to the hash of its predecessor.", "entry[n].prev_hash == entry[n-1].hash.", "As expected.", "Pass"],
     ["TC-I04", "Integration", "An unmodified audit chain verifies as intact.", "Recomputed hashes match stored hashes.", "Initially failed - revealed a real defect; passes after correction.", "Pass"],
     ["TC-I05", "Integration", "Editing a historical audit row breaks verification.", "Verification detects the divergence.", "As expected.", "Pass"],
     ["TC-I06", "Integration", "Deleting an audit row breaks verification.", "Verification detects the broken link.", "As expected.", "Pass"],
     ["TC-S01", "System", "Upload a valid PDF against the correct matter.", "Status PROCESSED; extractions, summary and deadlines created.", "As expected.", "Pass"],
     ["TC-S03", "System", "Upload a document belonging to a different matter.", "Status REJECTED with a stated reason; entry in the rejection report.", "As expected.", "Pass"],
     ["TC-S06", "System", "A client requests another client's case.", "HTTP 403 and an audit entry.", "As expected.", "Pass"],
     ["TC-S12", "System", "Upload an image-only scanned PDF.", "Status FAILED with 'No extractable text'; Retry offered.", "As expected - a known limitation, handled gracefully.", "Pass"]],
    widths=[8, 10, 28, 22, 22, 10], font=7.5)

h3("Controlled document suite (system level)")
para("Eight prepared documents exercise the emerging-technology validation behaviour: two "
     "correctly filed documents (PDF and DOCX), two belonging to a different matter, two "
     "containing no case content, one partial match with a wrong defendant, and one image-only "
     "scan. The automated uploader records 8 of 8 scenarios reaching their expected terminal "
     "state, with no valid document rejected. Evidence: docs/TEST_CASES.md and "
     "docs/test-case-upload.sh.")

h3("Code coverage")
make_table(
    ["Class under test", "Line coverage", "Branch coverage"],
    [["JwtUtil", "100.0%", "80.0%"],
     ["PasswordHasher", "88.9%", "n/a"],
     ["ExtractionEngine", "80.1%", "63.0%"],
     ["JwtUtil.Session", "66.7%", "n/a"],
     ["AuditService", "58.9%", "66.7%"]],
    widths=[40, 30, 30], font=9)
para("Coverage is deliberately concentrated on the security primitives, the emerging-technology "
     "component and the tamper-evidence mechanism - the three areas where a defect would be most "
     "damaging. Whole-project line coverage is 9.7%, because the REST controllers, the JavaFX "
     "client and the seed-data classes are exercised by the system-level scripts rather than by "
     "JUnit. This is stated plainly rather than presented as a project-wide figure; extending "
     "automated coverage to the controller layer is identified as a maintenance need in 5.4.",
     italic=True, size=9.5)

h3("User acceptance testing")
para("Formal user acceptance testing with external participants has not yet been conducted. The "
     "protocol is defined - a four-week pilot with three small firms, at least five practitioners "
     "and twenty portal clients, measuring task completion within three minutes, a System "
     "Usability Scale score of 75 or higher, a mean client-satisfaction rating of 4.0 or higher, "
     "and a 40% reduction in missed deadlines against a manual baseline - but the pilot falls "
     "outside the time available before submission. What has been performed is a structured "
     "self-evaluation: every acceptance criterion in Table 1 was walked through manually against "
     "the running system and the outcome recorded. Reporting this honestly is preferred to "
     "presenting self-evaluation as though it were independent user testing; NFR-04 is therefore "
     "marked 'Not yet measured' in the traceability matrix rather than claimed as met.")

h2("5.2  Quantitative evaluation  (2)")
para("The figures below were produced by the automated harness PerformanceMetricsTest and are "
     "reproducible with 'mvn test -Dtest=PerformanceMetricsTest'. Measurement environment: "
     "OpenJDK 21.0.12 on Linux x86-64, 2 vCPU, 466 MB maximum heap. Each latency figure is the "
     "95th percentile of 200 iterations after 20 warm-up runs.")

h3("NFR-01 - performance")
make_table(
    ["Document size", "Runs", "Mean (ms)", "p50 (ms)", "p95 (ms)", "Per page at p95 (ms)"],
    [["1 page", "200", "1.11", "0.84", "2.80", "2.80"],
     ["5 pages", "200", "1.24", "1.03", "1.87", "0.37"],
     ["10 pages", "200", "1.95", "1.82", "2.10", "0.21"],
     ["20 pages", "200", "4.00", "3.70", "5.78", "0.29"]],
    widths=[20, 12, 17, 17, 17, 17], font=8.5)
para("NFR-01 budgets 10 seconds for full AI processing of a 20-page document. The measured p95 is "
     "5.78 ms - approximately 1 700 times inside the budget. Verdict: met.")

h3("NFR-03 - security primitive cost")
make_table(
    ["Operation", "Runs", "p50 (ms)", "p95 (ms)", "Interpretation"],
    [["PBKDF2 verify (120 000 iterations)", "20", "27.67", "37.00",
      "Deliberately slow to resist brute-force attack; occurs once per login only."],
     ["JWT parse and validate", "200", "0.38", "2.69",
      "Occurs on every request; comfortably inside the NFR-01 per-request budget."]],
    widths=[26, 9, 13, 13, 39], font=8.5)
para("Verdict: met. The cost profile is correct - the expensive operation is the one an "
     "attacker must repeat, while the per-request operation is cheap.")

h3("ETR-01 - emerging-technology accuracy metric")
make_table(
    ["Metric", "Value"],
    [["Annotated documents in the evaluation set", "3"],
     ["Annotated fields (gold standard)", "7"],
     ["Correctly extracted (true positives)", "7"],
     ["Missed (false negatives)", "0"],
     ["Total fields returned by the engine", "21"],
     ["Recall", "100.0%"],
     ["Precision on annotated fields", "100.0%"],
     ["ETR-01 threshold", "precision >= 90%, recall >= 85%"],
     ["Verdict", "Met on this evaluation set"]],
    widths=[55, 45], font=9)
para("The threshold is met, but the evaluation set of three documents and seven annotated fields "
     "is small, and a perfect score on a set of that size should be read as an indication rather "
     "than a robust estimate. Section 5.4 records this as the principal evaluation limitation. "
     "The second emerging-technology measure - a false-rejection rate of 0% across the eight-"
     "document controlled suite (ETR-03) - is drawn from a different and independent set of "
     "documents and therefore corroborates the result.", italic=True, size=9.5)

h2("5.3  User guide - main workflow  (2)")
para("The following procedure covers the principal workflow: uploading a document, reviewing the "
     "AI output, approving the summary and synchronising the deadline. Screens referenced appear "
     "as Figures 10 to 13.")

h3("Procedure")
for i, step in enumerate([
    "Open http://localhost:8080 and sign in with a practitioner account (for the demonstration, ally@mongalalaw.co.za / lawyer123). The dashboard shown in Figure 10 appears.",
    "Select Cases from the navigation, then open the matter you wish to file against. If the matter does not yet exist, choose New Case and complete the reference, title, client, matter type and court.",
    "In the Documents panel of the case, choose Upload Document, select a PDF or DOCX file of up to 25 MB, and confirm that the client's processing consent has been obtained.",
    "Submit the upload. The document appears immediately with status PROCESSING; the interface remains usable while the AI pipeline works in the background.",
    "Within a few seconds the status changes automatically. For a valid document it becomes PROCESSED and the extracted fields appear, each with its confidence score and source page, as in Figure 11.",
    "Review the extracted fields. Click any field to see the exact source text that produced it. Correct any value that is wrong; the correction is recorded as a training example.",
    "Open the Summary tab and read the three-paragraph draft (Facts, Legal Issues, Current Status). Edit the wording if required.",
    "Choose Approve Summary. Only at this point does the summary become visible to the client; the approval, the approver and the timestamp are recorded in the audit log.",
    "Open the Calendar tab. Deadlines derived from the extracted dates are listed. Choose Sync to push them to Google Calendar, or Download .ics to subscribe from any phone or desktop calendar.",
    "The client receives a live notification and can now see the approved summary, any shared documents and the upcoming deadlines in their portal (Figure 13)."], 1):
    numbered(step)

h3("Troubleshooting")
make_table(
    ["Symptom", "Likely cause", "Resolution"],
    [["The browser shows 'This site can't be reached' at localhost:8080.",
      "The backend is not running, or another process holds port 8080.",
      "Start the backend with ./run-backend.sh. To use a different port set PORT=8081 before starting."],
     ["Login fails with 'Invalid email or password'.",
      "Incorrect credentials, or the demonstration data has not been seeded.",
      "Use one of the accounts in section 4.4. Delete the data/ directory and restart to re-seed."],
     ["Login fails with 'Account disabled - access revoked by an administrator'.",
      "The account has been revoked.",
      "Sign in as an administrator and choose Restore on the affected user."],
     ["A document remains at PROCESSING indefinitely.",
      "The worker pool is saturated, or the AI pipeline threw an unhandled error.",
      "Refresh the case. If the status becomes FAILED, use Retry AI. Check the server log for the correlation identifier."],
     ["A document is REJECTED although it is correct.",
      "The parties or case number in the document do not correspond to the matter it was filed against.",
      "Open the blocked-document banner to see the highlighted conflicting text, confirm the matter, and re-file against the correct case."],
     ["A document is FAILED with 'No extractable text'.",
      "The file is an image-only scan and contains no machine-readable text.",
      "This is a known limitation. Supply a text-based PDF, or await the planned OCR capability (5.4)."],
     ["The client cannot see a summary the lawyer has written.",
      "The summary is still in DRAFT.",
      "The practitioner must choose Approve Summary; this is required by FR-06 and is intentional."],
     ["The notification bell does not update.",
      "The Server-Sent Events stream was interrupted, or a proxy is buffering the response.",
      "Refresh the page. Behind nginx, confirm that proxy_buffering is off for /api/events."],
     ["Calendar synchronisation reports an error.",
      "The Google account is not linked or the external API is unavailable.",
      "Use Download .ics instead; the system degrades to local calendar export by design."]],
    widths=[28, 30, 42], font=8)

h2("5.4  Limitations, risks, ethics, maintenance and future improvements  (2)")

h3("Current limitations")
make_table(
    ["Limitation", "Impact", "Present mitigation"],
    [["Image-only scanned documents cannot be processed.",
      "A significant proportion of documents in small South African practices arrive as scans.",
      "The document fails explicitly with a stated reason rather than silently; a Retry action is offered. OCR is the first roadmap item."],
     ["The accuracy evaluation set is small (3 documents, 7 annotated fields).",
      "The 100% precision and recall figures are indicative rather than statistically robust.",
      "Reported transparently; corroborated by the independent eight-document controlled suite."],
     ["The AI component is a deterministic rule-based engine, not a trained transformer.",
      "Recall may degrade on document layouts not anticipated by the patterns.",
      "The AiExtractionService interface allows substitution without changing any caller; every field is human-reviewable before release."],
     ["Automated coverage is concentrated on security, NLP and audit; controllers are covered only by system scripts.",
      "Whole-project line coverage is 9.7%.",
      "Stated plainly; extending JUnit coverage to the controller layer is a maintenance priority."],
     ["Availability, backup rehearsal and usability targets are not yet measured.",
      "NFR-02 and NFR-04 cannot be claimed as met.",
      "Marked 'Not yet measured' rather than asserted; protocols are defined and ready to execute."]],
    widths=[30, 32, 38], font=8)

h3("Unresolved risks")
make_table(
    ["Risk", "Likelihood / impact", "Mitigation"],
    [["The AI misreads or omits a court date, and the error propagates into the diary and the client summary.",
      "Medium / High",
      "Four layers: no AI output reaches a client without explicit practitioner approval (FR-06); every field carries a confidence score and source span for rapid verification; low-confidence fields are flagged for mandatory review; corrections feed a retraining set whose accuracy is re-measured before promotion."],
     ["Dependence on the external Google Calendar API.",
      "Medium / Low",
      "The CalendarGateway abstraction degrades to local .ics export rather than failing."],
     ["Encryption key or JWT secret disclosure.",
      "Low / Critical",
      "Secrets are injected as environment variables and excluded from source control by .gitignore; a managed secret store is specified for production."]],
    widths=[32, 14, 54], font=8)

h3("Ethical and security considerations")
para("Three considerations are material. First, accountability: the system must not be permitted "
     "to give legal advice. It organises and summarises, and the design enforces this by "
     "requiring a qualified practitioner to approve every summary before a client sees it, so a "
     "human professional remains accountable for what the client is told. Second, explainability: "
     "an opaque model would be unacceptable in a regulated profession, which is why every "
     "extracted field carries its confidence score, verbatim source span and page number - a "
     "practitioner can always answer the question 'why was this date diarised?'. Third, privacy: "
     "the documents concerned contain privileged communications and personal information, so "
     "consent is recorded before processing, access is scoped by role, storage is encrypted with "
     "authenticated encryption, and the hash-chained audit log makes any retrospective alteration "
     "of the access record detectable. A fourth consideration, accessibility, is presently a "
     "weakness: the browser client has not been evaluated against WCAG 2.1 and does not yet carry "
     "ARIA landmarks, which is recorded below as a maintenance need.")

h3("Maintenance needs")
bullet("Extend automated test coverage from the security, NLP and audit classes to the REST controller layer.")
bullet("Introduce versioned database migrations (Flyway or Liquibase) in place of the current conditional DDL.")
bullet("Add WCAG 2.1 AA accessibility compliance, including ARIA landmarks, keyboard navigation and verified contrast ratios.")
bullet("Execute and document the quarterly backup-restore rehearsal, and begin recording availability to evidence NFR-02.")
bullet("Add dependency vulnerability scanning to the CI pipeline and keep the Javalin, PDFBox and POI dependencies current.")

h3("Two realistic future improvements")
para("1. Optical character recognition for scanned documents. Integrating a Tesseract-based OCR "
     "stage ahead of the existing text-extraction step would convert the system's most visible "
     "limitation into a capability. The pipeline already isolates text extraction behind "
     "DocumentTextExtractor, so the change is additive: where no machine-readable text is found, "
     "the document would be rasterised and passed through OCR before the extraction engine runs, "
     "rather than terminating in the FAILED state. This is the highest-value single improvement "
     "because most documents in the target market arrive as scans.")
para("2. A statute-of-limitation (prescription) tracker. Under South African law most claims "
     "prescribe three years after the debt becomes due. Capturing the incident date on each "
     "matter and deriving the prescription date automatically, with escalating warnings at six, "
     "three and one months, would address precisely the malpractice risk identified in the "
     "problem statement. The deadline infrastructure, the dashboard warning components and the "
     "notification channel all already exist, so this improvement largely composes existing "
     "capabilities into a new and highly relevant one.")

page_break()

# ================================================================ REFERENCES
h1("Reference List")
refs = [
    "Apache Software Foundation. 2024. Apache PDFBox: a Java PDF library. [Online] Available at: https://pdfbox.apache.org [Accessed 28 September 2026].",
    "Apache Software Foundation. 2024. Apache POI: the Java API for Microsoft documents. [Online] Available at: https://poi.apache.org [Accessed 28 September 2026].",
    "Bass, L., Clements, P. and Kazman, R. 2021. Software architecture in practice. 4th ed. Boston: Addison-Wesley.",
    "Desruisseaux, B. (ed.) 2009. Internet calendaring and scheduling core object specification (iCalendar). RFC 5545. Fremont: Internet Engineering Task Force.",
    "Fowler, M. 2018. Refactoring: improving the design of existing code. 2nd ed. Boston: Addison-Wesley.",
    "Gamma, E., Helm, R., Johnson, R. and Vlissides, J. 1994. Design patterns: elements of reusable object-oriented software. Reading: Addison-Wesley.",
    "Google LLC. 2024. Google Calendar API v3 reference. [Online] Available at: https://developers.google.com/calendar/api [Accessed 28 September 2026].",
    "Humble, J. and Farley, D. 2010. Continuous delivery: reliable software releases through build, test, and deployment automation. Boston: Addison-Wesley.",
    "International Organization for Standardization. 2022. ISO/IEC 27001:2022 Information security, cybersecurity and privacy protection. Geneva: ISO.",
    "Javalin. 2024. Javalin 6 documentation. [Online] Available at: https://javalin.io/documentation [Accessed 28 September 2026].",
    "Jones, M., Bradley, J. and Sakimura, N. 2015. JSON Web Token (JWT). RFC 7519. Fremont: Internet Engineering Task Force.",
    "Larman, C. 2004. Applying UML and patterns: an introduction to object-oriented analysis and design and iterative development. 3rd ed. Upper Saddle River: Prentice Hall.",
    "Object Management Group. 2017. OMG Unified Modeling Language (OMG UML) version 2.5.1. Needham: OMG.",
    "PostgreSQL Global Development Group. 2024. PostgreSQL 16 documentation. [Online] Available at: https://www.postgresql.org/docs/16 [Accessed 28 September 2026].",
    "Republic of South Africa. 2013. Protection of Personal Information Act 4 of 2013. Pretoria: Government Printer.",
    "Republic of South Africa. 2014. Legal Practice Act 28 of 2014. Pretoria: Government Printer.",
    "Sommerville, I. 2016. Software engineering. 10th ed. Harlow: Pearson Education.",
    "Wiegers, K. and Beatty, J. 2013. Software requirements. 3rd ed. Redmond: Microsoft Press.",
]
for r in sorted(refs):
    p = doc.add_paragraph()
    run = p.add_run(r)
    run.font.size = Pt(10.5); run.font.name = BODY_FONT
    p.paragraph_format.left_indent = Cm(1.0)
    p.paragraph_format.first_line_indent = Cm(-1.0)
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(6)

footer_pagenum("Assignment 3")
doc.save(OUT)
print("WROTE", OUT, os.path.getsize(OUT), "bytes")
