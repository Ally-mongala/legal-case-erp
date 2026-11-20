#!/usr/bin/env python3
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pdfkit import *

story = []

story += [heading("1", "Introduction"),
P("This document outlines the use of applicable technologies in the Legal Case Management ERP, as "
  "required for Assignment 4. It justifies each technology against the functional requirements "
  "(FR1–FR11), the non-functional requirements and the module learning outcomes (A.1, B.1, C.1 and "
  "D.1). The system is an integrated software project: a REST API backend, a real database, real "
  "AI/NLP document processing, two front-ends (web and desktop), third-party calendar integration "
  "and real-time push, all working together as one solution.")]

story += [heading("2", "Architecture Overview"),
P("The system follows a modular, layered architecture: a shared domain model; a Javalin REST API "
  "with a worker-based AI pipeline; H2 (development) or PostgreSQL (production) persistence; a "
  "browser client and a JavaFX desktop client; Google Calendar and iCalendar gateways; and an SSE "
  "event bus for real-time notifications. This satisfies the defining features of an integrated "
  "software project: modular components, interoperability through APIs, automation, scalability and "
  "data synchronisation.")]

story += [heading("3", "Technology Selection and Justification"),
]
t = Table([
 ["Layer", "Technology", "Why it was selected", "Alternatives", "Outcome"],
 ["Language", "Java 21 (LTS)", "Strong typing, huge ecosystem, object-oriented, platform-independent", "Python, C#, Node.js", "Selected"],
 ["REST framework", "Javalin 6", "Lightweight; first-class SSE support (live notifications)", "Spring Boot, Express, FastAPI", "Selected"],
 ["Web client", "HTML/CSS/JS", "Zero build step; runs in any browser", "React, Vue, Angular", "Selected"],
 ["Desktop client", "JavaFX 21", "Multi-platform desktop UI; FXML + CSS separation", "Swing, Electron", "Selected"],
 ["Database", "H2 / PostgreSQL", "H2 for zero-setup dev; PostgreSQL ACID for production; schema is shared", "MySQL, SQLite, MongoDB", "Selected"],
 ["Data access", "JDBC + HikariCP", "Full SQL control; database-agnostic", "JPA, jOOQ", "Selected"],
 ["PDF text", "Apache PDFBox", "Industry-standard Java PDF text extraction", "iText, Tika", "Selected"],
 ["Word text", "Apache POI", "Reads .docx and .doc (FR1)", "—", "Selected"],
 ["AI/NLP", "Rule-based extractor + extractive TF-IDF summariser", "Real text processing with confidence, spans and pages; deterministic and explainable", "Hugging Face transformers, spaCy", "Selected (transformer-swappable)"],
 ["Calendar", "Google Calendar v3 API + .ics gateway", "Real third-party integration (FR4); .ics works with any calendar app", "Microsoft Graph", "Selected (both)"],
 ["Auth", "JWT (jjwt) + PBKDF2", "Stateless role-based access (NFR1)", "Sessions, full OAuth flow", "Selected"],
 ["Encryption", "AES-256-GCM", "Encryption at rest (FR11/POPIA)", "Jasypt, Vault", "Selected"],
 ["Real-time", "Server-Sent Events", "One-way push ideal for notifications; simpler than WebSockets", "WebSockets, polling", "Selected"],
 ["Build", "Maven multi-module", "Reproducible builds, dependency management", "Gradle", "Selected"],
 ["Deployment", "Docker + docker-compose", "DevOps (D.1.1); components on different machines (project requirement); cloud-ready (D.1.2)", "—", "Selected"],
 ["CI/CD", "GitHub Actions", "Automated build, test and image build on push", "Jenkins, GitLab CI", "Selected"],
], colWidths=[2.6*cm, 3.2*cm, 5.4*cm, 2.6*cm, 1.6*cm], repeatRows=1)
t.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 8),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t, Spacer(1, 8)]

story += [heading("4", "Emerging Technologies"),
sub("4.1", "AI and natural language processing (D.1.3)"),
P("The system does not simply store files - it reads them. DocumentTextExtractor (PDFBox and POI) "
  "pulls text page by page; ExtractionEngine returns structured fields with a confidence score, the "
  "exact source span and the page number (explainability); Summarizer builds a three-paragraph "
  "extractive summary (Facts / Legal Issues / Current Status); and a validation step blocks "
  "documents that are irrelevant or about a different matter while keeping the extraction for "
  "review. This is genuine NLP text processing and demonstrates the incorporation of a newer "
  "technology into system design and development."),
sub("4.2", "Cloud services and cloud-ready architecture (D.1.2)"),
P("Uploaded documents are stored encrypted at rest (AES-256-GCM) with a configurable data-residency "
  "value, following POPIA-aware design. The Google Calendar v3 REST API is integrated over OAuth2 - "
  "a genuine third-party cloud service. Deployment targets containers with PostgreSQL, so the same "
  "code runs on any cloud provider without redevelopment, and storage paths model object storage "
  "(S3/Azure Blob) for a real bucket to be attached."),
sub("4.3", "DevOps (D.1.1)"),
P("A multi-stage Dockerfile produces a small runtime image; docker-compose orchestrates the "
  "database, backend and web containers with health checks and persistent volumes; and a GitHub "
  "Actions workflow builds all modules, runs the smoke and end-to-end tests on every push and "
  "builds the Docker image on main. Environment-variable configuration, a health endpoint and a "
  ".dockerignore follow DevOps best practice (D.1.4)."),
sub("4.4", "Real-time and interoperability (D.1.5)"),
P("An SSE endpoint pushes new-message and deadline-updated events; the browser notification bell "
  "updates in about seven milliseconds and the dashboard charts refresh live. The calendar exports "
  "a valid iCalendar (.ics) file per case and globally, importable into Apple Calendar, Outlook and "
  "Google Calendar. Two front-ends (web and JavaFX desktop) share one API, demonstrating "
  "multi-platform functionality.")]

story += [heading("5", "Security and Compliance Technologies"),
]
t2 = Table([
 ["Requirement", "Implementation"],
 ["Authentication", "JWT with role-based scopes (read:case, write:case, read:client, write:client, read:audit, admin:model)"],
 ["Password storage", "PBKDF2-HMAC-SHA256, 120 000 iterations, salted"],
 ["Encryption at rest", "AES-256-GCM for uploaded documents (FR11)"],
 ["Authorisation", "Scope checks on every route plus client-owner checks; revoked users rejected on every call"],
 ["Consent (POPIA)", "Explicit consent required before document processing; stored per document"],
 ["Audit trail", "SHA-256 hash-chained audit_log (tamper-evident, FR10/FR11)"],
 ["Reports", "CSV downloads restricted to admin scope"],
], colWidths=[5.2*cm, 9.8*cm], repeatRows=1)
t2.setStyle(TableStyle([
 ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 9.5),
 ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
 ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
 ("TOPPADDING", (0,0), (-1,-1), 3), ("BOTTOMPADDING", (0,0), (-1,-1), 3),
]))
story += [t2, Spacer(1, 8)]

story += [heading("6", "Testing Technologies"),
P("Service-layer smoke tests (17 assertions) cover authentication, extraction (confidence and "
  "spans), summary approval, calendar sync, the audit trail and client queries. API end-to-end "
  "suites cover full journeys including role checks (401/403). A test-case uploader runs eight "
  "scenarios with expected versus actual outcomes (PROCESSED / BLOCKED / FAILED). Browser "
  "automation (Playwright) verified the live bell, the dashboard charts and the client portal "
  "during development.")]

story += [heading("7", "Conclusion"),
P("The technology choices deliver every functional requirement while demonstrating the module "
  "learning outcomes: an integrated, automated, scalable system with real AI, cloud-ready "
  "deployment (Docker, PostgreSQL and Google Calendar), DevOps practice (CI/CD), real-time push and "
  "multi-platform front-ends. Each technology was selected against the requirements and can be "
  "replaced without redesigning the system.")]

refs = [
 "Erl, T. 2017. Service-Oriented Architecture: Analysis and Design for Services and Microservices. 2nd ed. Prentice Hall.",
 "Hall, G. 2017. Adaptive Code: Agile coding with design patterns and SOLID principles. 2nd ed. Microsoft Press.",
 "Oracle. 2023. Java Platform, Standard Edition documentation. [Online] Available at: https://docs.oracle.com/en/java/ [Accessed: 2026].",
 "OpenJFX. 2023. JavaFX documentation. [Online] Available at: https://openjfx.io/ [Accessed: 2026].",
 "Javalin. 2024. Javalin web framework documentation. [Online] Available at: https://javalin.io/ [Accessed: 2026].",
 "Apache Software Foundation. Apache PDFBox. [Online] Available at: https://pdfbox.apache.org/ [Accessed: 2026].",
 "Apache Software Foundation. Apache POI. [Online] Available at: https://poi.apache.org/ [Accessed: 2026].",
 "PostgreSQL Global Development Group. PostgreSQL documentation. [Online] Available at: https://www.postgresql.org/docs/ [Accessed: 2026].",
 "Google. Google Calendar API v3. [Online] Available at: https://developers.google.com/calendar [Accessed: 2026].",
 "Docker. Docker documentation. [Online] Available at: https://docs.docker.com/ [Accessed: 2026].",
 "GitHub. GitHub Actions documentation. [Online] Available at: https://docs.github.com/actions [Accessed: 2026].",
 "RFC 5545. Internet Calendaring and Scheduling Core Object Specification (iCalendar). [Online] Available at: https://www.rfc-editor.org/rfc/rfc5545 [Accessed: 2026].",
 "Tshwane University of Technology. 2026. ISJ107V Integrated Software Project study guide. Pretoria.",
]

build("Assignment4_Applicable_Technologies.pdf", "Assignment 4 — Use of Applicable Technologies",
      "Legal Case Management ERP — technology justification and emerging technologies",
      story, refs,
      [("1", "Introduction"), ("2", "Architecture Overview"),
       ("3", "Technology Selection and Justification"), ("4", "Emerging Technologies"),
       ("5", "Security and Compliance Technologies"), ("6", "Testing Technologies"),
       ("7", "Conclusion"), ("", "References")])
