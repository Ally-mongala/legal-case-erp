#!/usr/bin/env python3
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pdfkit import *
UML = os.path.join(os.path.dirname(__file__), "..", "uml")

story = []

# 1. Introduction
story += [heading("1", "Introduction"),
P("This document is the project documentation for the Legal Case Management ERP, incorporating UML "
  "designs as required for Assignment 3. It records the requirements analysis, user stories, the "
  "architecture and design patterns, the user-interface design and the verification evidence.")]

# 2. Requirements analysis
story += [heading("2", "Requirements Analysis"),
sub("2.1", "Actors and roles"),
P("Three actors use the system: the Lawyer (manages cases, documents, AI summaries, deadlines and "
  "client messages), the Client (views approved summaries, shares and downloads documents, messages "
  "the lawyer and rates the lawyer) and the Admin (manages users and access, views audits and "
  "reports, and manages system metrics, retraining runs and datasets)."),
sub("2.2", "User stories"),
P("The following user stories were used to drive development (from the project brief and the "
  "functional requirements):"),
]
t = Table([
 ["ID", "User story"],
 ["US1", "As a lawyer, I upload a case document so the system can extract dates and parties and create a case record automatically."],
 ["US2", "As a lawyer, I review the AI-generated summary, make edits where needed and approve it before it goes to the client."],
 ["US3", "As a client, I receive a simplified three-paragraph update along with calendar reminders for upcoming deadlines."],
 ["US4", "As an admin, I retrain the model using corrected annotations so accuracy keeps improving over time."],
 ["US5", "As a lawyer, I automatically get calendar events created for court dates pulled from documents."],
 ["US6", "As a client, I can rate my lawyer after a matter."],
 ["US7", "As an admin, I can revoke a user's access and download audit and rejection reports."],
], colWidths=[1.6*cm, 13.4*cm], repeatRows=1)
t.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 9.5),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t, Spacer(1, 6),
sub("2.3", "Acceptance criteria (examples)"),
P("<b>Upload (FR1):</b> given a logged-in lawyer on a case, when a PDF or Word file is uploaded with "
  "consent, then a document record is created and the AI worker starts processing it. "
  "<b>AI validation (FR2):</b> given a document about a different matter, when processing completes, "
  "then the document is blocked with a visible reason and its extracted fields remain available for "
  "review. <b>Messaging (FR5):</b> given a client message, when it is saved, then the lawyer's "
  "notification bell updates instantly.")]

# 3. UML designs
story += [heading("3", "UML Designs"),
P("The complete UML set (use-case, class, sequence, activity and deployment) is presented in the system specification (Assignment 2). The diagrams most relevant to the project documentation are reproduced here with additional explanation."),
sub("3.1", "Use-Case Diagram")]
story += figure(os.path.join(UML, "use-case.png"),
                "Figure 3.1 - Use-case diagram", 15*cm)

story += [sub("3.2", "Class Diagram - design patterns")]
story += figure(os.path.join(UML, "class.png"),
                "Figure 3.2 - Class diagram", 15*cm)
story += [P("The class diagram shows the application of several design patterns (B.1.4): the Strategy pattern is used by the IDataStore interface (demo in-memory versus remote REST implementation), the Gateway pattern by CalendarGateway (local .ics versus Google Calendar API), the Service layer pattern keeps controllers thin, and the Repository pattern encapsulates JDBC access. The Observer pattern underpins the EventBus used for real-time SSE notifications."),
sub("3.3", "Activity Diagram - document lifecycle")]
story += figure(os.path.join(UML, "activity.png"),
                "Figure 3.3 - Activity diagram: document lifecycle and AI validation", 14*cm)

story += [sub("3.4", "Deployment Diagram")]
story += figure(os.path.join(UML, "component.png"),
                "Figure 3.4 - Deployment: web, backend and database containers", 14*cm)

story += [heading("4", "User Interface Design and UX (C.1)"),
P("Two front-ends were designed from the same requirements: a responsive browser client and a JavaFX "
  "desktop client. The UI follows recognised UX principles: a consistent navigation rail, clear "
  "status feedback (badges, chips, toasts), progressive disclosure (a dashboard with drill-down), "
  "instant feedback (real-time notifications), and role-based workspaces that only show what the user "
  "may do."),
P("The design system uses a navy and gold palette for a professional legal identity, with status "
  "colour-coding (green = processed, amber = pending, red = blocked or overdue) and accessible "
  "contrast. Both platforms present the same information architecture: Dashboard, Cases, Calendar, "
  "Activity and Client Ratings for lawyers; My Case, Documents, Messages and Rate Lawyer for "
  "clients; and Users, Audits and System for admins."),
P("UX flows verified: a lawyer uploads a document and the extraction appears immediately; a client "
  "sends a message and the lawyer's bell updates in about seven milliseconds; clicking the bell "
  "navigates to the relevant case and scrolls to the message; dashboard charts are clickable and "
  "update live when deadlines change.")]

# 5. Architecture & technologies
story += [heading("5", "Architecture and Technologies"),
P("The system is a multi-module Maven project: <b>shared</b> (domain model), <b>backend</b> (Javalin "
  "REST API, JDBC services, AI pipeline, calendar gateway, SSE event bus, encryption and audit) and "
  "<b>frontend</b> (JavaFX desktop client plus a browser client served by the backend). "
  "Technologies are justified in detail in the Assignment 4 document."),
P("The worker pipeline is asynchronous: uploading a document returns immediately and a worker "
  "extracts text, runs the extraction engine, validates relevance, generates the summary and "
  "publishes calendar and notification events. Failures are surfaced as FAILED status with a retry "
  "action (FR7).")]

# 6. Verification & testing
story += [heading("6", "Verification and Testing"),
P("The system was verified at three levels: unit/service-level smoke tests, API end-to-end suites "
  "and browser-level acceptance tests."),
P("<b>Service-layer smoke test:</b> 17 assertions covering authentication, case search, AI "
  "extraction (confidence and source spans), summary approval, calendar sync, the audit trail and "
  "client-portal queries."),
P("<b>API end-to-end suites</b> (docs/e2e*.sh): full journeys including login, upload, AI "
  "processing, approval, calendar sync, client view and role checks (401/403)."),
P("<b>Test-case uploader</b> (docs/test-case-upload.sh): eight scenarios with expected versus actual "
  "outcomes - matching documents are PROCESSED, wrong-matter and no-information documents are "
  "BLOCKED, and a blank scanned document FAILS with a retry option."),
]
t2 = Table([
 ["Scenario", "Expected", "Result"],
 ["Matching PDF on its case", "PROCESSED", "PROCESSED"],
 ["Matching Word document on its case", "PROCESSED", "PROCESSED"],
 ["Wrong matter (different parties)", "BLOCKED", "REJECTED"],
 ["Divorce document on a different case", "BLOCKED", "REJECTED"],
 ["Grocery list (no case information)", "BLOCKED", "REJECTED"],
 ["Utility bill (no case information)", "BLOCKED", "REJECTED"],
 ["Partial match (client matches)", "PROCESSED", "PROCESSED"],
 ["Blank / scanned document", "FAILED", "FAILED"],
], colWidths=[7.0*cm, 3.4*cm, 4.6*cm], repeatRows=1)
t2.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 9.5),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t2, Spacer(1, 6),
P("Browser acceptance tests (Playwright, during development) verified the live notification bell "
  "(about 7 ms), the clickable and live-updating dashboard charts, the document upload flow and the "
  "client portal.")]

# 7. Conclusion
story += [heading("7", "Conclusion"),
P("The project documentation records a complete, verifiable software project: requirements analysed "
  "into user stories and acceptance criteria, UML designs covering behaviour and structure, a "
  "pattern-based architecture, UX-driven multi-platform interfaces, and a documented test strategy "
  "with green results. The system is deployed as separate containers and continuously tested by a "
  "CI pipeline, satisfying the module's project requirements.")]

refs = [
 "Erl, T. 2017. Service-Oriented Architecture: Analysis and Design for Services and Microservices. 2nd ed. Prentice Hall.",
 "Fowler, M. 2003. UML Distilled. 3rd ed. Addison-Wesley.",
 "Hall, G. 2017. Adaptive Code: Agile coding with design patterns and SOLID principles. 2nd ed. Microsoft Press.",
 "Nielsen, J. 1994. Usability Engineering. Academic Press.",
 "Sommerville, I. Software Engineering. 10th ed. Pearson.",
 "Tshwane University of Technology. 2026. ISJ107V Integrated Software Project study guide. Pretoria.",
]

build("Assignment3_Project_Documentation.pdf", "Assignment 3 — Project Documentation",
      "Legal Case Management ERP — documentation incorporating UML designs",
      story, refs,
      [("1", "Introduction"), ("2", "Requirements Analysis"),
       ("3", "UML Designs"), ("4", "UI/UX Design"),
       ("5", "Architecture and Technologies"), ("6", "Verification and Testing"),
       ("7", "Conclusion"), ("", "References")])
