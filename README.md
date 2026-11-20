# LegalCase ERP

**AI-assisted legal case management for small law firms** — a working prototype built for
ISJ107V Integrated Software Project (S2 2026).

| | |
|---|---|
| **Student** | Ally Mongala · 219361629 |
| **Module** | ISJ107V Integrated Software Project |
| **Emerging technology** | Artificial intelligence / NLP — document extraction, validation and summarisation |
| **Stack** | Java 21 · Javalin 6 · Jetty 11 · H2 / PostgreSQL 16 · PDFBox · Apache POI · JUnit 5 |
| **Interfaces** | Browser client (primary, served by the backend) · JavaFX desktop client (same REST contract) |
| **Status** | Prototype — 11 of 12 functional requirements implemented; see the readiness table below |

---

## What problem it solves

A two- to five-attorney practice runs its matters on shared folders, e-mail and paper, and three
failures follow:

1. **Documents are filed by hand**, so a document belonging to another matter is easily attached to
   the wrong case — and nobody notices until a deadline is missed.
2. **The useful information is buried in prose** — parties, case number, court and the dates that
   create deadlines — so the attorney re-reads pleadings to rebuild a chronology.
3. **Clients have no visibility**, so the office answers the same questions by telephone instead of
   billing the time.

The system reads every uploaded document, **refuses documents that do not belong to the matter**,
drafts a three-paragraph summary for practitioner approval, turns extracted dates into calendar
deadlines, and publishes approved material to a scoped client portal.

---

## Quick start

Requires **JDK 21** (JDK 26 is not supported by this build) and **Maven 3.9+**. Nothing else —
the database, the web server and the AI pipeline are all inside the application.

```bash
mvn -q clean install -DskipTests
java -jar backend/target/legal-backend.jar
```

or use the launcher for your platform:

```bash
run-backend.bat      # Windows
./run-backend.sh     # Linux / macOS
```

Then open **<http://localhost:8080>**.

### Demo accounts (seeded on first start)

| Role | E-mail | Password | What you can do |
|---|---|---|---|
| Attorney | `ally@mongalalaw.co.za` | `lawyer123` | Dashboard, cases, upload and review AI output, deadlines, calendar, ratings |
| Client | `thabo@example.com` | `client123` | Own matter only: approved summaries, documents, messaging, rating |
| Administrator | `admin@mongalalaw.co.za` | `admin123` | Users and access, audit trail, rejection report, metrics, AI training runs |

To start from a clean database, delete the `data/` folder and restart.

### Try the AI validation in 60 seconds

Sign in as the attorney → open **C-2026-014** (Mokoena v Siyaya Transport) → upload from
`docs/test-cases/`:

| Upload this | What happens |
|---|---|
| `1-match_mokoena_affidavit.pdf` | PROCESSED — fields with confidence, span and page; summary drafted; deadlines created |
| `2-wrong_matter_nkosi_abba.pdf` | **BLOCKED** — reason names the parties it found and the matter it was filed against |
| `3-no_case_info_grocery_list.pdf` | **BLOCKED** — no case information at all |
| `6-scanned_mokoena_statement_image_only.pdf` | Real image-only scan of the Mokoena matter: read by OCR, PROCESSED, hearing date 2026-12-14 |
| `5-scanned_no_text.pdf` | Blank page with no text or image: correctly FAILED with a Retry action. Real scans are read by OCR (see below). |

---

## Architecture

```
┌───────────────────────────┐        REST + JWT          ┌──────────────────────────────────────┐
│  Browser client           │ ─────────────────────────▶ │  Backend (Javalin on Jetty)          │
│  index.html · app.js      │   /api/auth/login          │  auth (JWT + role scopes)            │
│  served by the backend    │   /api/cases               │  H2 file DB  ·  PostgreSQL profile   │
│                           │   /api/documents           │  PDFBox + POI text extraction        │
│  JavaFX desktop client    │ ◀───────────────────────── │  AI pipeline (extraction, validation,│
│  (same REST contract)     │        JSON + SSE          │  summarisation) · worker pool        │
└───────────────────────────┘                            │  AES-256-GCM at rest · hash-chained  │
                                                         │  audit log · Google Calendar / .ics  │
                                                         └──────────────────────────────────────┘
```

* **AI component** runs in-process on a worker pool (the Assignment 3 design places it in a
  separate service; the difference and its risk are documented in the Assignment 4 report, §3.5).
* **Every extracted field carries a confidence, the exact source span and the page number**, which
  is what the interface highlights when a document is rejected.
* **Nothing reaches a client until a practitioner approves it.** Approval records who and when.

---

## Requirement status

| Status | Requirements |
|---|---|
| **Implemented** | FR-01 upload + encrypted storage · FR-02 extraction with confidence/span/page · FR-03 three-paragraph summary · FR-04 validation that blocks mis-filed documents · FR-06 approval before client visibility · FR-07 scoped client portal · FR-08 messaging with live unread state · FR-09 hash-chained audit + CSV reports · FR-10 account and access management · FR-11 live dashboard · FR-12 retry + ratings · ETR-01–03 |
| **Partial** | FR-05 Google Calendar push (needs firm OAuth credentials; `.ics` export works) · ETR-04 correction capture · NFR-06 data-subject erasure |
| **OCR** | Scanned, image-only PDFs are read with Tesseract (`OCR_APPLIED` in the audit log). Accuracy on real scans is not yet measured. |
| **Deferred** | mobile/offline PWA · external anchoring of the audit hash chain |
| **Not measured** | NFR-02 availability and backup restore · NFR-04 usability (no pilot yet) |

Honest status is deliberate: the prototype is demonstrable and tested, but not a production system.

---

## Testing

| Suite | Command | Last result |
|---|---|---|
| Unit + integration (JUnit 5) | `mvn test` | **79 tests, 0 failures, 0 errors, 0 skipped** |
| End-to-end AI battery (8 documents) | `bash docs/test-case-upload.sh` | **8 passed, 0 failed** |
| Performance / accuracy harness | `mvn -pl backend -Dtest=PerformanceMetricsTest test` | latency 0.19 ms/page p95 · recall 100 % |
| API, security, explainability | see `docs/testing/logs/03-*.log`, `04-*.log` | **20/20 as expected** |

**Full dated report with raw logs: [`docs/testing/TEST_RESULTS.md`](docs/testing/TEST_RESULTS.md)** —
last run **28 October 2026**.

---

## Project layout

```
legal-case-erp/
├── backend/           Javalin REST API, AI pipeline, web client, schema, tests
│   ├── src/main/java/za/co/mongala/legal/backend/
│   │   ├── api/            controllers (case, document, summary, calendar, admin, portal)
│   │   ├── nlp/            ExtractionEngine, Summarizer, DocumentTextExtractor
│   │   ├── service/        AiPipeline, AuditService, DocumentService, messaging, calendar
│   │   ├── auth/           JwtUtil, PasswordHasher, scope guard
│   │   └── security/       EncryptionUtil (AES-256-GCM)
│   ├── src/main/resources/web/    browser client (index.html, app.js, app.css)
│   └── src/test/java/             JUnit 5 suites
├── frontend/          JavaFX desktop client (FXML + CSS)
├── shared/            domain model shared by both clients
├── docs/
│   ├── testing/       TEST_RESULTS.md + raw logs (dated)
│   ├── metrics/       metrics-results.md (regenerated by the harness)
│   ├── uml/           PlantUML sources + PNG/SVG/PDF exports (A2, A3, A4)
│   ├── assignment_pdfs/  Assignment 3 and 4 submissions (docx + pdf)
│   ├── test-cases/    the eight documents used for AI validation testing
│   ├── DEMO_SCRIPT_A4.md   presentation script and fallback plan
│   └── PUSH_TO_GITHUB.md   publishing guide
├── screenshots/       interface evidence used in the reports
├── deploy/            nginx config for the container profile
├── docker-compose.yml PostgreSQL 16 + backend + nginx
├── .github/workflows/build.yml   CI: build, test, Docker image
├── HOW_TO_RUN.txt     step-by-step run and troubleshooting notes
└── push-to-github.bat one-command publish helper
```

---

## Deployment

**Single JAR (demonstration):** `java -jar backend/target/legal-backend.jar`

**Containers (intended production shape):**

```bash
docker compose up --build     # PostgreSQL 16 + backend + nginx on :8080
```

Configuration is externalised through environment variables — `LCE_DB_URL`, `LCE_DB_USER`,
`LCE_DB_PASSWORD`, `LCE_PORT`, `LCE_JWT_SECRET`, `LCE_ENC_KEY`, `LCE_DEMO_AUTH`,
`LCE_GOOGLE_CAL_ID`, `LCE_GOOGLE_REFRESH_TOKEN`, `LCE_GOOGLE_CLIENT_ID`, `LCE_GOOGLE_CLIENT_SECRET`.
No secret is committed to this repository; see `HOW_TO_RUN.txt`.

---

## Security and POPIA

* JWT bearer tokens with role scopes; every endpoint declares the scope it requires.
* PBKDF2 password hashing, 120 000 iterations, per-user salt.
* Documents encrypted at rest with AES-256-GCM; only ciphertext on disk.
* POPIA consent captured per document and enforced — processing is refused without it.
* Tamper-evident, hash-chained audit log with CSV export for the firm.

---

## Licence and academic use

Submitted as coursework for ISJ107V at Tshwane University of Technology, 2026. Third-party
libraries retain their own licences (Apache 2.0 for PDFBox and POI; Apache 2.0 for Javalin and
Jetty). The content of `docs/assignment_pdfs/` is the student's own assessed work.
"# legal-case-erp" 
