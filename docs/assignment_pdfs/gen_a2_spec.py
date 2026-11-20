#!/usr/bin/env python3
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pdfkit import *
UML = os.path.join(os.path.dirname(__file__), "..", "uml")

story = []

# 1. Introduction
story += [heading("1", "Introduction"),
P("This document is the formal system specification for the Legal Case Management ERP, as required for "
  "Assignment 2. It describes the technical and hardware requirements and incorporates UML designs. "
  "The system integrates a central legal database, an AI document-understanding pipeline, Google "
  "Calendar synchronisation, a client portal, an administrator portal and an analytics dashboard."),
P("The specification is derived from the business problem and opportunity described in Assignment 1 and "
  "from the functional requirements FR1–FR11 set by the project brief.")]

# 2. Hardware & technical requirements
story += [heading("2", "Hardware and Technical Requirements"),
sub("2.1", "Server-side"),
P("The backend must run on a modern x86-64 server or cloud instance with at least 2 vCPUs, 2 GB RAM and "
  "10 GB storage. The application is packaged as a Java 21 fat jar and deployed in a Docker container; "
  "the database is PostgreSQL 16 (or H2 for development). The AI document pipeline runs as a worker "
  "thread pool inside the same process in this build, but is isolated behind an interface so it can be "
  "scaled as a separate service."),
sub("2.2", "Client-side"),
P("Two clients are supported: a browser client (HTML, CSS and JavaScript, no build step) and a JavaFX "
  "21 desktop client for Windows, macOS and Linux. Both require only a modern browser or a Java 21 "
  "runtime respectively. No additional hardware is required beyond a standard office computer and "
  "internet access."),
sub("2.3", "Network and integration"),
P("The system communicates over HTTP/HTTPS with JSON payloads and a JWT bearer token. Server-Sent "
  "Events provide real-time push for message and deadline notifications. Google Calendar v3 REST API "
  "is integrated using OAuth2 with a refresh token; a local iCalendar (.ics) export is provided as a "
  "fallback that works with any calendar application.")]

# 3. Functional requirements
story += [heading("3", "Functional Requirements (FR1–FR11)"),
P("The following functional requirements were specified by the project brief and are implemented by "
  "the built system."),
]
t = Table([
 ["ID", "Requirement"],
 ["FR1", "Allow a lawyer or paralegal to upload a legal PDF or Word document and store it against the relevant case record in the central database."],
 ["FR2", "Automatically extract case dates, parties (plaintiff, defendant, attorneys, judge), case number and key facts from the uploaded document using the NLP model."],
 ["FR3", "Generate a three-paragraph AI summary (Facts / Legal Issues / Current Status) for each document."],
 ["FR4", "Sync extracted and manually captured deadlines to Google Calendar with configurable reminders (for example, 7, 3 or 1 day before)."],
 ["FR5", "Allow a lawyer to exchange messages with their client."],
 ["FR6", "Give clients a secure portal to view case summaries, selected documents and to exchange messages with their lawyer."],
 ["FR7", "Give clients a tab to rate a lawyer."],
 ["FR8", "Give admin a portal to view user roles and be able to revoke access."],
 ["FR9", "Give admin the ability to view the audits and download all the reports."],
 ["FR10", "Provide an admin interface for managing the system, reviewing usage metrics, approving model retraining runs and managing the training datasets."],
 ["FR11", "Maintain tamper-evident audit logs, encryption of client data, consent controls for processing and data-residency options to support compliance with POPIA and Law Society record-keeping rules."],
], colWidths=[1.6*cm, 13.4*cm], repeatRows=1)
t.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 9.5),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t, Spacer(1, 6),
P("A full user-story and acceptance-criteria breakdown is provided in the project documentation "
  "(Assignment 3).")]

# 4. UML designs
story += [heading("4", "UML Designs"),
sub("4.1", "Use-Case Diagram"),
P("Figure 4.1 shows the actors (Lawyer, Client and Admin) and the use cases they perform, mapped to the functional requirements.")]
story += figure(os.path.join(UML, "use-case.png"),
                "Figure 4.1 - Use-case diagram (actors and FR1-FR11 use cases)", 16*cm)

story += [sub("4.2", "Class Diagram"),
P("Figure 4.2 shows the domain model and the layered architecture: the shared domain model, the backend service and API layers, the calendar gateway and the two front-end clients.")]
story += figure(os.path.join(UML, "class.png"),
                "Figure 4.2 - Domain and architecture class diagram", 16*cm)

story += [sub("4.3", "Sequence Diagram - Document Upload and AI Processing"),
P("Figure 4.3 models the flow from upload to AI extraction, validation and summary generation.")]
story += figure(os.path.join(UML, "sequence-upload.png"),
                "Figure 4.3 - Sequence diagram: document upload and AI pipeline", 16*cm)

story += [sub("4.4", "Sequence Diagram - Client Message and Instant Notification"),
P("Figure 4.4 models messaging between the client and the lawyer, including the Server-Sent Events push that updates the notification bell instantly.")]
story += figure(os.path.join(UML, "sequence-message.png"),
                "Figure 4.4 - Sequence diagram: client message and SSE notification", 16*cm)

story += [sub("4.5", "Activity Diagram - Document Lifecycle"),
P("Figure 4.5 shows the decision flow through the document lifecycle, including the AI validation that can block incorrect or irrelevant documents.")]
story += figure(os.path.join(UML, "activity.png"),
                "Figure 4.5 - Activity diagram: document lifecycle", 15*cm)

story += [sub("4.6", "Component / Deployment Diagram"),
P("Figure 4.6 shows the components deployed as separate containers (browser, backend, database and external calendar services), meeting the module requirement that components are hosted on different machines.")]
story += figure(os.path.join(UML, "component.png"),
                "Figure 4.6 - Component and deployment diagram (Docker)", 15*cm)

# 5. Data design
story += [heading("5", "Database Design"),
P("The schema mirrors the entities in the class diagram and is created by "
  "<font face='Courier'>db/schema.sql</font>. The core tables are: users, cases, documents, "
  "extractions, summaries, deadlines, messages, ratings, training_runs, datasets, calendar_events "
  "and audit_log. The audit_log table stores a SHA-256 hash chain for tamper evidence (FR11). "
  "Documents are stored encrypted at rest with AES-256-GCM (FR11)."),
P("The schema is written in SQL that runs on both H2 (development) and PostgreSQL (production); the "
  "only difference, CLOB versus TEXT, is adapted automatically at startup.")]

# 6. API design
story += [heading("6", "REST API Design"),
P("The API is implemented with the Javalin framework. All endpoints except authentication and health "
  "require a JWT bearer token, and each endpoint enforces a role scope. Key endpoints include:"),
]
t2 = Table([
 ["Method", "Path", "Scope", "Purpose"],
 ["POST", "/api/auth/login", "public", "Authenticate and receive a JWT"],
 ["GET", "/api/cases?q=", "read:case", "List and search cases"],
 ["POST", "/api/cases", "write:case", "Create a case"],
 ["PATCH", "/api/cases/{id}", "write:case", "Update a case and alert the client"],
 ["DELETE", "/api/cases/{id}", "write:case", "Delete a case"],
 ["POST", "/api/cases/{id}/documents", "write:case / write:client", "Upload PDF/Word with POPIA consent"],
 ["GET", "/api/documents/{id}/content", "read:case", "Read the full extracted text"],
 ["DELETE", "/api/documents/{id}", "write:case / write:client", "Delete a document"],
 ["POST", "/api/cases/{id}/deadlines", "write:case", "Add a manual deadline"],
 ["POST", "/api/calendar/sync", "write:case", "Sync deadlines to the calendar"],
 ["GET", "/api/cases/{id}/calendar.ics", "read:case", "Export a case calendar (.ics)"],
 ["GET", "/api/events", "read:client", "SSE stream (message/deadline events)"],
 ["POST", "/api/ratings", "write:client", "Client rates a lawyer"],
 ["GET", "/api/admin/reports/rejections.csv", "admin:model", "Download the rejection report"],
 ["GET", "/api/admin/metrics", "admin:model", "Usage metrics"],
], colWidths=[2.0*cm, 6.4*cm, 3.6*cm, 3.0*cm], repeatRows=1)
t2.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 8.5),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t2, Spacer(1, 6)]

# 7. Non-functional
story += [heading("7", "Non-Functional Requirements"),
P("<b>Security (NFR1):</b> data is encrypted at rest (AES-256-GCM) and in transit (HTTPS); role-based "
  "access control is enforced on every endpoint; passwords are hashed with PBKDF2."),
P("<b>Availability (NFR2):</b> the backend targets a 99.5% uptime SLA and runs as a stateless container "
  "behind a reverse proxy, so it can be restarted or scaled without data loss."),
P("<b>Scalability (NFR3):</b> the document-processing worker is isolated behind an interface and can be "
  "scaled out as a separate service; the database is PostgreSQL."),
P("<b>Performance (NFR4):</b> metadata API responses are under 200 ms; AI inference on a short document "
  "targets under 500 ms, and a typical 20-page PDF is processed within roughly five seconds."),
P("<b>Privacy (NFR5):</b> the system complies with POPIA: client consent is captured before document "
  "processing, uploaded data is encrypted, and a data-residency option is configurable.")]

refs = [
 "Erl, T. 2017. Service-Oriented Architecture: Analysis and Design for Services and Microservices. 2nd ed. Prentice Hall.",
 "Sommerville, I. Software Engineering. 10th ed. Pearson.",
 "Tshwane University of Technology. 2026. ISJ107V Integrated Software Project study guide. Pretoria: Department of Computer Science.",
 "Fowler, M. 2003. UML Distilled: A Brief Guide to the Standard Object Modeling Language. 3rd ed. Addison-Wesley.",
 "Object Management Group. Unified Modeling Language. [Online] Available at: https://www.omg.org/spec/UML/ [Accessed: 2026].",
]

build("Assignment2_System_Specification.pdf", "Assignment 2 — System Specification",
      "Legal Case Management ERP — technical and hardware requirements with UML designs",
      story, refs,
      [("1", "Introduction"), ("2", "Hardware and Technical Requirements"),
       ("3", "Functional Requirements (FR1–FR11)"), ("4", "UML Designs"),
       ("5", "Database Design"), ("6", "REST API Design"),
       ("7", "Non-Functional Requirements"), ("", "References")])
