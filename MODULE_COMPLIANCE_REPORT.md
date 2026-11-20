# ISJ107V Alignment Report — does LegalCase ERP meet what the module requires?

**Module:** ISJ107V Integrated Software Project (NQF 7) · S2 2026 · Tshwane University of Technology
**Student:** Ally Mongala · 219361629
**System:** LegalCase ERP — AI-assisted legal case management for small South African law firms
**Document version:** 2.0 — **supersedes version 1 (22 August 2026)**, which listed UML diagrams,
Docker/CI and the Assignment 4 document as missing. All three now exist in the repository.

**Sources this report is measured against**
1. Study Guide §23 — learning outcomes and assessment criteria (A.1, B.1, C.1, D.1).
2. Study Guide §24 — assessment plan and weightings.
3. `ISJ107V_Assignment_5 final Presentation.pdf` — final presentation, 50 marks, 40% of the final module mark.
4. `ISJ107V_Assignment_4 Prototype Presentation.pdf`, `ISJ107V_Assignment_3.pdf`, `ISJ107V_Assignment_2.pdf`.
5. `docs/assignment_pdfs/Assignment1_Project_Proposal.pdf` — the approved proposal (aim, objectives, scope).

---

## 1. Verdict in one paragraph

**On substance: yes — the project aligns.** Every learning outcome (A.1–D.1) has a concrete
artefact, all four predicate assessments exist as documents, and the 50% project is a real,
running, tested system rather than a mock-up (50 automated tests over 7 classes, 17 database
tables, ~94 HTTP route registrations, ~8 800 lines of Java, a browser client and a JavaFX desktop
client, Docker Compose topology and a CI workflow).

**On submission: not yet — four things can still cost marks, and three of them are outside the
code.** (1) The repository is still a placeholder (`github.com/<username>/...`) and Assignment 5
requires an *accessible final repository*; (2) the final deck is still branded "Assignment 4",
quotes **33** tests instead of 50 and runs to a 10-minute plan instead of 12–15 minutes; (3) three
A5 rubric items are not on any slide — organisational benefit + an ethics/privacy/accessibility
consideration (Q4.3), a future enhancement (Q4.4), and personal contribution + lessons learned
(Q5.4); (4) four internal documents now contradict the code (test counts and "missing" artefacts).

**Estimated position if presented as-is:** roughly 40–44 of 50 on the A5 rubric — strong Q2 (live
system) and Q3 (UML), full Q4.2 metrics, and losses only where evidence is absent from the slides,
not where the system is weak.

---

## 2. Study Guide §24 — assessment plan

| # | Task | Weight | Requirement | Evidence in this project | Status |
|---|---|---|---|---|---|
| 1 | Assignment 1 — Project proposal | 10% | Exec summary, background, vision, deliverables, timeframe, resources, success criteria | `docs/assignment_pdfs/Assignment1_Project_Proposal.pdf` | ✅ submitted |
| 2 | Assignment 2 — Formal system specification + UML | 10% | Technical/hardware requirements document incorporating UML | `docs/assignment_pdfs/Assignment2_System_Specification.pdf`, `docs/uml/a2/` (use case, activity, class, sequence in `.puml` + `.png` + `.svg`) | ✅ complete |
| 3 | Assignment 3 — Project documentation + UML | 15% | Documentation with UML designs, traceability, ERD, API documentation | `docs/assignment_pdfs/Assignment3_Project_Documentation.pdf`, `docs/uml/a3/` (architecture, use case, class, sequence, activity, component, **deployment**, state, ERD) | ✅ complete |
| 4 | Assignment 4 — Use of applicable technologies | 15% | Document outlining technology choices and justification | `docs/ASSIGNMENT4_TECHNOLOGIES.md` + `ISJ107V_Assignment 4_219361629.docx`; prototype deck `ISJ107V_Assignment4_SLIDES_RUBRIC_219361629.pptx` (21 slides, mapped to rubric) | ✅ complete |
| 5 | Project / Assignment 5 — working system, components hosted on different machines | 50% (A5 = 40%, 50 marks) | Live demonstration, UML, evaluation, individual defence | Running system (browser + JavaFX), `docker-compose.yml` + `backend/Dockerfile` + `deploy/nginx.conf`, `.github/workflows/build.yml`, 50 tests, metrics harness | ⚠️ system ready; **multi-machine proof and repository publication still to be shown** |

---

## 3. Study Guide §23 — learning outcomes and assessment criteria

| Criterion | What the module asks for | Concrete evidence in the repository | Status |
|---|---|---|---|
| **A.1.1** | Literature surveyed for evolving trends | Proposal + A4 technology document (AI/NLP in LegalTech, cloud, containerisation) | ✅ |
| **A.1.2** | Modern trends, technologies, architectures outlined | Multi-module Maven, REST API + separate clients, worker pool for AI jobs, SSE real-time channel, container topology | ✅ |
| **A.1.3** | Technology appropriateness assessed against design specifications | `docs/ASSIGNMENT4_TECHNOLOGIES.md` — each choice justified against FR/NFR, with alternatives rejected and reasons | ✅ |
| **A.1.4** | Technology or combination recommended | Same document; PDFBox + Apache POI chosen over OCR/Tesseract for text PDFs and Word, with the OCR limitation documented | ✅ |
| **B.1.1** | Business case analysed into user and system requirements | 12 FR, 6 NFR, 3 ETR (emerging-technology requirements) with unique IDs and acceptance criteria, carried unchanged from A2 → A3 → A4 → A5 | ✅ |
| **B.1.2** | User-requirement models created | `docs/uml/a2/a2-usecase.puml`, `docs/uml/a3/images/Figure_02_Use_Case_Diagram.*`, `docs/uml/a4/a4-usecase.puml` (18 use cases, 3 human + 2 system actors, include/extend) | ✅ |
| **B.1.3** | Functional requirements modelled | Activity, sequence (upload and messaging), state-machine and ERD models in `docs/uml/a2`, `a3`, `a4` | ✅ |
| **B.1.4** | Design patterns recognised, outlined and contrasted | Gateway (`CalendarGateway` with real/Demo implementations, `IDataStore` Demo/Remote), Service layer, Repository, Strategy, Producer–consumer work queue, hash-chain for audit | ✅ (code + A4 report) |
| **B.1.5** | Applicable pattern used in the functional design | `CalendarGateway` swap is what makes the offline demonstration and `.ics` fallback possible — a genuine, explainable pattern decision | ✅ |
| **C.1.1** | Conceptual UI design from specifications with UX principles | Role-shaped navigation generated from the signed-in user, inline confidence/span chips, toasts, inline 400/403 explanations, destructive-action confirmation, keyboard-free flows | ✅ |
| **C.1.2** | Concept of UX design discussed | Covered in A3/A4 documentation and the deck's error-feedback and recovery slides; keep it explicit when answering | ✅ (say it, don't assume) |
| **C.1.3** | Different UI designs for different platforms | **Two** clients on one REST contract: browser client served by the backend, and a JavaFX desktop client (`run-connected.bat`) | ✅ |
| **D.1.1** | DevOps shown in theory and practice | `.github/workflows/build.yml` — build all modules, smoke test, e2e, Docker image build on `main`, `docker compose config -q`; Dockerfiles and Compose profile | ⚠️ file exists, but it has **never run green** until the repo is pushed |
| **D.1.2** | Cloud services in designs **and implementation** | Real Google Calendar v3 API integration behind a gateway; container topology with PostgreSQL 16 + Nginx; JDBC URL and storage path are configuration-driven so a managed database or object store drops in | ⚠️ design + local implementation; **no cloud host yet** |
| **D.1.3** | Various relevant technologies incorporated | Java 21, Javalin 6, Jetty 11, H2 + PostgreSQL 16, HikariCP, PDFBox, Apache POI (`.xlsx` workbook now used for reporting), JJWT, SSE, Jackson, JUnit 5, Docker, GitHub Actions | ✅ strong |
| **D.1.4** | Best practices outlined | PBKDF2 (120 000 iterations, per-user salt), JWT scopes, AES-256-GCM at rest, SHA-256 hash-chained audit, POPIA consent enforced before processing, RBAC, parameterised SQL, safe error envelopes, 3-attempt lockout with 15-minute cooldown and HTTP 429 | ✅ |
| **D.1.5** | Multi-platform functionality and interoperability | Web + desktop + REST + RFC 5545 `.ics` export (Apple/Outlook/Google) + CSV reports + multi-sheet `.xlsx` workbook + invoice PDF generation | ✅ strong |

---

## 4. Assignment 5 rubric map (50 marks) — what is covered and what is not

Rubric: Q1 [6] · Q2 [16] · Q3 [10] · Q4 [8] · Q5 [10].

| Criterion | Marks | Where it is evidenced today | Gap to close before presenting |
|---|---|---|---|
| **1.1** Title, problem, users, aim, value | 2 | Deck slides 2, report §1.1 | Slide must say **Assignment 5 / final system**, not Assignment 4 |
| **1.2** Final scope + functional/non-functional requirements + approved changes and incomplete items | 2 | Deck slide 3 (FR table with Completed / Partial / Deferred) | Counts are stale and self-contradictory ("11 of 12 implemented · 3 partial · 3 deferred"). Restate cleanly and add the three post-A4 additions: firm billing & trust overview, admin user onboarding, multi-sheet ERP workbook |
| **1.3** Presentation roadmap + technology stack + emerging technology | 2 | Deck slides 2, 4, 5 | Add a one-slide roadmap with clock; currently implied rather than shown |
| **2.1** Principal end-to-end workflow: navigation, validation, data processing, output, **recovery from invalid input** | 6 | Deck slides 6–7; live demo | None — strongest part of the deck |
| **2.2** Emerging technology in operation + how its output drives a decision | 4 | Deck slide 8 (document is refused with a stated reason) | None |
| **2.3** Data persistence / integration | 3 | Deck slide 9 (17 tables, restart-proof, `.ics`) | Update "12 tables" → **17 tables**; mention the `.xlsx` workbook as an integration output |
| **2.4** Security, privacy, reliability, access control, credential and error handling | 3 | Deck slide 10 (JWT scopes, POPIA, AES-256-GCM, PBKDF2, hash chain, env secrets, safe errors) | Add the lockout rule (3 attempts → 15-minute cooldown → 429) as a demonstrable control |
| **3.1** Final use-case diagram, actors, services demonstrated | 2 | Deck slide 11, `docs/uml/a4/a4-usecase.puml` | Add UC for **firm billing & trust overview** and **user onboarding** (or state explicitly that they extend UC15/UC16) |
| **3.2** Final class diagram, key classes, relationships, design decisions | 2 | Deck slide 13, `a4-class-domain` + `a4-class-services` | Service diagram omits `BillingService`, `TrustAccountingService`, `ExcelWorkbookService`, `UserService` — add them so the model matches the demonstrated system |
| **3.3** One sequence or activity diagram matching the demonstrated workflow and AI interaction | 2 | Deck slides 12, 14; `a4-activity.puml`, `a4-sequence.puml` | Add a second sequence for **trust settlement** if you demo it |
| **3.4** Component + deployment views with modules, databases, external services, environment | 2 | Deck slide 15; `a4-component.puml`, `a4-deployment.puml` | Deployment diagram must show the *actual* demo environment, and the Compose profile as the intended shape — be explicit that it is one host, three containers |
| **3.5** Consistency between requirements, UML and implementation + justified deviations | 2 | Deck slide 15 (three named deviations) + A3 traceability matrix | State the consistency claim in one sentence and name the deviations as *approved changes*, not defects |
| **4.1** Functional, integration, system and UAT results with passed / failed / unresolved counts | 3 | Deck slide 16; `docs/testing/TEST_RESULTS.md` | **Recount.** `TEST_RESULTS.md` still says 33; a fresh `mvn test` on the current code ran **50 tests, 0 failures, 0 errors, 0 skipped** in 7 classes (`SecurityTest` 12, `ExtractionEngineTest` 9, `AuditChainIntegrationTest` 8, `ErpEnhancementsIntegrationTest` 6, `PracticeComplianceAndSearchTest` 6, `PerformanceMetricsTest` 4, `LegalWorkflowEnhancementsTest` 5) |
| **4.2** Two quantitative measures + one emerging-technology metric | 2 | Deck slide 17: 0.19 ms/page extraction p95, 9.6 ms metadata query p95, 100% field recall on 7 annotated fields, 73 ms upload→final state | None — keep the honesty note about corpus size |
| **4.3** Expected user/organisational benefit + one ethical, privacy, security or accessibility consideration | 2 | **Not on any slide.** Material exists: POPIA consent gate, per-matter ownership check returning 403, human approval before client visibility | **Add one slide**: the three upgrades are the benefit story (firm sees billed vs collected vs trust-held; unpaid invoices settle from Section 86 trust without overdraft; admin onboards staff in-app; one workbook exports the firm's financial and AI-quality position) + the ethical rule "AI proposes, the attorney decides" |
| **4.4** Principal limitation + one realistic future enhancement | 1 | Deck slide 18 covers two limitations and corrective actions | Add the **future enhancement** framing explicitly (e.g. Tesseract OCR pass for scanned documents; or move the AI worker to a separate service so AI load cannot starve request threads) |
| **5.1** Clear narrative, readable slides, professional delivery, 12–15 minutes | 3 | Deck is visually consistent, black/blue/white/green, readable | Deck is built for a 10-minute A4 slot — **re-time to 12–15 minutes** |
| **5.2** Explanation↔demonstration transitions, time management, prepared fallback | 2 | Deck slide 19: clock-by-clock run order, 8 failure modes, `demo-preflight.bat` / `demo-preflight.sh`, `demo-reset.bat` | Re-time; keep the fallback table (it is excellent) |
| **5.3** Answer technical questions from requirements, UML, code, data, testing, deployment | 3 | Deck slide 20 + evidence throughout | Be ready on: why H2 and not PostgreSQL by default; why AI is in-process; how the hash chain detects a deleted row; why OCR is deferred |
| **5.4** Personal contribution, lessons learned, most important design decision | 2 | Deck slide 20 covers *decisions* only | **Add personal contribution + two lessons learned.** A5 asks for this explicitly and it is easy marks |

---

## 5. Proposal (Assignment 1) → delivered system

| Proposal element | Delivered in the system | Status |
|---|---|---|
| Goal: one place where a small firm's documents, deadlines and client communication live | Cases + documents + deadlines + messaging + client portal + dashboard | ✅ |
| Objective 1: capture documents and stop misfiling | Upload with PDFBox/POI extraction, validation that blocks another matter's document, rejection report for the administrator | ✅ |
| Objective 2: reduce re-reading of pleadings | Extraction with confidence, source span and page; three-paragraph summary in DRAFT until approved | ✅ |
| Objective 3: give clients visibility | Scoped client portal (403 on cross-client access), approved summaries, messaging with live unread state, ratings | ✅ |
| Value to the practice: get paid, and account for client money | **New:** firm-wide Billing & Trust overview (billed incl. 15% VAT, collected, debtors, trust held, per-matter rows), AI-extracted claim quantum with a fee-to-claim ratio warning above 25%, settlement from Section 86 trust with overdraft protection, direct-payment recording, fee note PDF with credits and balance | ✅ |
| Value to the practice: manage people and records | **New:** administrator creates Lawyer / Client / Admin accounts in-app (PBKDF2 hashing, optional linking of a new client to an existing matter) and downloads a four-sheet ERP + AI Intelligence workbook (Matters & Finance · AI Extractions & Quality · Billing & 15% VAT · Trust & Deadlines) | ✅ |

---

## 6. Verified facts about the build (use these numbers, not the old ones)

| Fact | Value | How it was counted |
|---|---|---|
| Test classes / test methods | **7 classes, 50 tests** (0 failures, 0 errors, 0 skipped) | `mvn test` on the current code, JDK 21.0.13 — the Surefire summary is authoritative; a raw `grep -c '@Test'` over-reports by one because `@TestMethodOrder` also matches |
| Database tables | **17** (`users, cases, documents, extractions, summaries, deadlines, messages, audit_log, calendar_events, processing_jobs, ratings, training_runs, datasets, ai_corrections, billing_entries, system_backups, trust_ledger`) | `backend/src/main/resources/db/schema.sql` |
| HTTP route registrations | ~94 (BillingController alone registers 15) | `grep` over `backend/src/main/java/.../api` and `Main.java` |
| Java source | ~8 800 lines across `backend` + `shared` | `wc -l` |
| Browser client | 1 417 lines (`web/app.js`) + `index.html` + service worker (cache v3) | `wc -l` |
| Interfaces | Browser client **and** JavaFX desktop client over the same REST contract | `run-backend.bat`, `run-connected.bat` |
| Seed data | Cases and deadlines all dated from **November 2026** onward | `seed/SeedData.java` |
| Security controls | PBKDF2 120 000 iterations, JWT scopes, AES-256-GCM at rest, SHA-256 audit chain, 3-attempt lockout → 15-minute cooldown → HTTP 429 | `auth/`, `service/AuditService.java`, `SecurityTest` (12 tests) |
| Deployment artefacts | `backend/Dockerfile`, `docker-compose.yml` (PostgreSQL 16 + backend + Nginx), `deploy/nginx.conf`, `.github/workflows/build.yml` | repository |
| UML artefacts | `.puml` sources **and** rendered `.png`/`.svg` for A2, A3 and A4 (use case, activity, class, sequence, component, deployment, state, ERD) | `docs/uml/` |

---

## 7. Gap register — what still has to happen, in priority order

### P1 — blockers for the Assignment 5 submission (do these first)

| # | Gap | Why it costs marks | Action |
|---|---|---|---|
| 1 | **Repository not published** — deck still shows `github.com/<username>/legal-case-erp` | A5 instruction: *"an accessible final repository through MyTUTor"*; Q1.3 also asks for the repository location | Run `push-to-github.bat`, replace `<username>` on slides 4 and 21 and in the README, make sure the repo is public or that the lecturer has access |
| 2 | **Deck branded "Assignment 4", 10-minute plan** | Q1, Q5.1, Q5.2 — the brief is 12–15 minutes plus ≈5 minutes of questions | Re-badge every slide/footer to "Assignment 5 · Final system presentation and demonstration" and re-pace the run order to 12–15 minutes |
| 3 | **Test numbers stale** — slides 16 and 20 say "33 automated tests"; `TEST_RESULTS.md` says 33 as well | Q4.1 asks for the number of tests passed, failed and unresolved; quoting 33 understates 50 | Re-run `mvn test`, record the exact figure, and update the deck, `TEST_RESULTS.md` and the README |
| 4 | **Three rubric items absent from the slides** — Q4.3 benefit + ethics/privacy/accessibility, Q4.4 future enhancement, Q5.4 personal contribution + lessons learned | Direct mark loss in Q4 (4 of 8 marks at risk) and Q5 | Add two slides (see §4 rows 4.3/4.4 and 5.4) |

### P2 — makes the "hosted on different machines" claim defensible

| # | Gap | Why | Action |
|---|---|---|---|
| 5 | Multi-machine demonstration | The Project rubric says *"working or simulated in a real live environment, including having its components hosted on different machines"*; the current Compose file runs three containers on **one** host | Either (a) run the `db` service on a second laptop/Pi and point `LCE_DB_URL` at it, or (b) deploy the Compose stack to one cheap cloud VM and screenshot the public URL — then state plainly in the demo which parts are separate machines |
| 6 | CI has never executed | D.1.1 asks for DevOps in *theory and practice*; an unexecuted workflow is only theory | Push, then show the green run (badge on the README) |
| 7 | Cloud service in the *implementation* | D.1.2 asks for cloud in designs **and** implementation | The Google Calendar API integration partly satisfies this; a free-tier managed PostgreSQL or object-storage bucket closes it fully |

### P3 — internal consistency (cheap, protects credibility)

| # | Document | Problem | Action |
|---|---|---|---|
| 8 | `docs/testing/TEST_RESULTS.md` | 33 tests, no mention of the four newer suites | Regenerate from a fresh `mvn test` run and store the log in `docs/testing/logs/` |
| 9 | `docs/FEATURES_FOR_MODULE_COMPLIANCE.md` | Still says there is "one `SmokeTest.java`" and no JUnit suite | Mark those items as delivered, or archive the file |
| 10 | UML service class diagram | Omits `BillingService`, `TrustAccountingService`, `ExcelWorkbookService`, `UserService` | Add them (PlantUML re-render) so Q3.2/Q3.5 consistency holds |
| 11 | README / deck | "12 tables" → 17; FR table needs the three new capabilities | One pass over `README.md` and slide 3 |
| 12 | Report placeholders and signatures | `[REPLACE WITH YOUR URL BEFORE SUBMITTING]`, cover-page declarations unsigned | Fill and sign |

---

## 8. If the marker asks — three answers that show mastery

**"Is this really an integrated system, or a collection of screens?"**
"One REST contract serves two independent clients — a browser client and a JavaFX desktop client.
The AI processing is in the request path through a worker pool, its output is persisted in 17 tables,
and its decisions change what the client can see. Removing the AI breaks the client portal, so the
integration is structural, not decorative."

**"Where is the emerging technology's output actually used?"**
"It gates storage. A document whose extracted parties and case number do not match the matter is
rejected with a named reason, excluded from the case record, surfaced to the attorney and reported
to the administrator. And it drives money: the firm-wide billing view uses the AI-extracted claim
amount as the quantum and warns when fees exceed 25% of it."

**"What is the honest limitation?"**
"Two. No OCR, so scanned documents fail by design and are retryable — the fix is a Tesseract pass
with its own confidence. And single-node deployment, so NFR-02 availability and NFR-04 usability are
specified but not measured — the fix is a cloud Compose deployment plus a practitioner pilot with
SUS and task timing."

---

## 9. Bottom line

- **Learning outcomes A.1 – D.1:** aligned, with artefacts for every criterion.
- **Assessment plan A1 – A4:** complete and evidenced.
- **Project / A5 (50 marks):** the system earns the technical marks today; the *submission* is what
  is behind — repository, deck branding, timing, refreshed numbers and the three missing rubric
  items. All four P1 items are hours of work, not days.
