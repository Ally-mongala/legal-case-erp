# Test Results — LegalCase ERP

> **Help chat robot (added):** `ChatbotServiceTest` covers the question matching (7 tests). Live check on the running backend: client and lawyer answers, a case the client does not own is refused, unauthenticated calls get 401, and the browser widget renders and answers without console errors.
>
> **Current run (final build, with OCR and the hearing-date fix):** `mvn test` on JDK 21.0.13 — **79 tests, 0 failures, 0 errors, 0 skipped**. HD-1 and HD-2 guard the hearing-date rule that once picked the collision date. OCR-1 and OCR-2 cover Tesseract reading an image-only PDF and a text-layer PDF being left alone. Live check: a rendered scan of the Mokoena matter was PROCESSED with 7 fields and an `OCR_APPLIED` audit entry.
> Per class: SecurityTest 12 · ExtractionEngineTest 9 · AuditChainIntegrationTest 8 · CalendarGatewayTest 8 ·
> ErpEnhancementsIntegrationTest 6 · PracticeComplianceAndSearchTest 6 · LegalWorkflowEnhancementsTest 5 · PerformanceMetricsTest 4.
> PostgreSQL 17 run: 10/10 smoke checks pass, including restart persistence. Caddy front door: health, login, live events pass.
> Sections below that say 33 tests describe the earlier Assignment 4 run and are kept as historical evidence.

**Project:** LegalCase ERP — AI-assisted legal case management for small law firms
**Student:** Ally Mongala · 219361629 · ISJ107V Integrated Software Project (S2 2026)
**Test run:** 28 October 2026, 20:41–20:43 UTC (22:41–22:43 SAST)
**Build tested:** `legal-backend.jar` built from commit on `main` at the time of this run
**Result (Assignment 4 run, historical):** all checks passed — 33/33 unit and integration tests, 8/8 end-to-end AI decisions, 20/20 API, security and explainability checks

Raw logs for every claim below are in `docs/testing/logs/`. Nothing in this report is
estimated: each figure was produced by the command shown next to it during the run above.

---

## 1. Environment

| Property | Value |
|---|---|
| OS | Debian Linux, kernel amd64 (the project is also run on Windows 11) |
| Java | OpenJDK 21.0.13 (LTS) — the build target |
| Maven | Apache Maven 3.9 |
| Backend | Javalin 6.3.0 on embedded Jetty 11.0.23 |
| Database | H2 file database, `./data/erpdb` (PostgreSQL 16 profile documented separately) |
| Processors available | 2 |
| Maximum heap | 464 MB |
| Server | `http://localhost:8080`, started from `backend/target/legal-backend.jar` |

Reproduce the whole run from a clean checkout:

```bash
mvn -q clean install -DskipTests      # build
mvn test                              # 33 tests  -> docs/testing/logs/01-mvn-test.log
java -jar backend/target/legal-backend.jar &
bash docs/test-case-upload.sh         # 8 documents -> docs/testing/logs/02-e2e-ai-battery.log
mvn -pl backend -Dtest=PerformanceMetricsTest test   # regenerates docs/metrics/metrics-results.md
```

---

## 2. Automated test suite — 33 tests, 0 failures

`mvn test` · log: `logs/01-mvn-test.log`

| Test class | Tests | Failures | Errors | Skipped | What it verifies |
|---|---|---|---|---|---|
| `SecurityTest` | 12 | 0 | 0 | 0 | PBKDF2 hashing and per-user salt, JWT issue/verify, tampered-token rejection, scope enforcement |
| `ExtractionEngineTest` | 9 | 0 | 0 | 0 | Field extraction, case-number forms incl. `Case Number: GP/PTA/2026/4471`, confidence range, span, page attribution |
| `AuditChainIntegrationTest` | 8 | 0 | 0 | 0 | Hash chaining, chain verification, detection of an edited row, detection of a deleted row |
| `PerformanceMetricsTest` | 4 | 0 | 0 | 0 | Extraction latency, accuracy harness, credential cost |
| **Total** | **33** | **0** | **0** | **0** | **BUILD SUCCESS** |

Reported by Surefire as `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`.

Two real defects were found and fixed earlier in development, and both remain covered by these
tests: an audit hash computed from nanosecond precision that could not be recomputed from the
persisted microsecond column, and a case-number heading form the extractor originally missed.

---

## 3. End-to-end AI behaviour — 8 documents, 8 correct decisions

`bash docs/test-case-upload.sh` · log: `logs/02-e2e-ai-battery.log`

Each document was uploaded against a real matter; the script waited for the AI pipeline to reach a
final state and compared the outcome with the expected one.

| # | Document | Expected | Actual | Result |
|---|---|---|---|---|
| 1 | `1-match_mokoena_affidavit.pdf` | PROCESSED | PROCESSED | ✓ |
| 2 | `1-match_vanwyk_statement.docx` | PROCESSED | PROCESSED | ✓ |
| 3 | `2-wrong_matter_nkosi_abba.pdf` | REJECTED | REJECTED | ✓ |
| 4 | `2-wrong_matter_divorce.docx` | REJECTED | REJECTED | ✓ |
| 5 | `3-no_case_info_grocery_list.pdf` | REJECTED | REJECTED | ✓ |
| 6 | `3-no_case_info_utility_bill.docx` | REJECTED | REJECTED | ✓ |
| 7 | `4-partial_match_wrong_defendant.pdf` | PROCESSED | PROCESSED | ✓ |
| 8 | `5-scanned_no_text.pdf` | FAILED | FAILED | ✓ |

**Result: 8 passed, 0 failed.**

Rejection reasons recorded verbatim by the system during this run:

> *“The document relates to a different matter (extracted parties: thabo nkosi v absa bank ltd) —
> it does not match case C-2026-020 (Thabo Mokoena v Siyaya Transport (Pty) Ltd).”*

> *“The document contains no case information (no parties, dates, case number or legal content
> could be identified).”*

Document 8 (image-only scan) correctly ends in FAILED with a Retry action, because OCR is a
documented deferred feature — not a silent accept.

---

## 4. API, access-control and validation checks — 20 checks, all as expected

`logs/03-api-security-evidence.log`

| ID | Check | Expected | Actual | Status |
|---|---|---|---|---|
| TC-01 | Sign in as attorney, client and administrator | 200, token issued | 200 × 3 | Pass |
| TC-02 | `GET /api/health` | 200 `{"status":"ok"}` | 200 | Pass |
| TC-03 | `GET /` serves the browser client | 200 | 200 | Pass |
| TC-04 | `GET /api/cases` with no token | 401 | 401 | Pass |
| TC-05 | `GET /api/cases` with a forged token | 401 | 401 | Pass |
| TC-06 | Client token → `/api/admin/users` | 403 | 403 | Pass |
| TC-07 | Client token → lawyer case list | 403 | 403 | Pass |
| TC-08 | Client token → another client's matter | 403 | 403 | Pass |
| TC-09 | SSE `/api/events` without token | 401 | 401 | Pass |
| TC-10 | Upload a `.txt` file | 400 + reason | 400 `"Only PDF or Word (.doc/.docx) files are supported (FR1)"` | Pass |
| TC-11 | Upload with `consent=false` | 400 + POPIA reason | 400 `"Client consent is required before processing the document (POPIA / FR11)"` | Pass |
| TC-12 | `GET /api/cases/{unknown}` | 404 | 404 | Pass |
| TC-13 | Sign in with a wrong password | 401 | 401 | Pass |
| TC-14 | `GET /api/cases` as attorney | populated list | 7 matters | Pass |
| TC-15 | `GET /api/cases/c1/calendar.ics` | valid RFC 5545 | `VCALENDAR` + `VEVENT` + `VALARM` present | Pass |
| TC-16 | `GET /api/admin/reports/audit.csv` | CSV export | 26 audit rows, header `timestamp,user,action,details,level` | Pass |
| TC-17 | `GET /api/admin/rejections` | blocked documents with reasons | 5 entries, each with its reason | Pass |
| TC-18 | `GET /api/cases` × 40 (latency) | within NFR-01 budget | min 7.1 ms, p50 7.7 ms, **p95 10.2 ms**, max 18.0 ms | Pass |
| TC-19 | Upload → final state, 5 runs | fast, non-blocking UI | 89, 30, 79, 81, 85 ms (**mean 73 ms**) | Pass |
| TC-20 | Every extracted field carries confidence, span and page | all fields | 8/8 fields, e.g. `Case Number = GP/JHB/2026/000841, conf 0.94, page 1, span "Case Number: GP/JHB/2026/000841"` | Pass |

---

## 5. Quantitative measures — regenerated today

`mvn -pl backend -Dtest=PerformanceMetricsTest test` · output: `docs/metrics/metrics-results.md`

**PT-01 — AI extraction latency (ETR-02, budget 500 ms/page at p95)**

| Document size | Runs | Mean (ms) | p95 (ms) | Mean per page (ms) |
|---|---|---|---|---|
| 1 page | 200 | 0.92 | 2.61 | 2.61 |
| 5 pages | 200 | 1.01 | 1.07 | 0.21 |
| 10 pages | 200 | 1.70 | 1.85 | 0.19 |
| 20 pages | 200 | 3.33 | 3.74 | **0.19** |

**PASS** — two to three orders of magnitude inside the budget.

**PT-02 — Extraction accuracy (ETR-01, recall ≥ 85 %)**

| Metric | Value |
|---|---|
| Annotated documents / fields | 3 / 7 |
| True positives | 7 |
| False negatives | 0 |
| Recall | **100.0 %** |
| Precision on annotated fields | **100.0 %** |

**PASS** — but note the corpus is small; this is stated as a limitation in the Assignment 4 report
(§4.3) and is not a claim about legal drafting in general.

**PT-03 — Security primitive cost (NFR-03)**

| Operation | Runs | p50 (ms) | p95 (ms) |
|---|---|---|---|
| PBKDF2 verify, 120 000 iterations | 20 | 23.99 | 50.13 |
| JWT parse and validate | 200 | 0.3584 | 2.1758 |

PBKDF2 is deliberately slow (brute-force resistance); JWT validation is cheap enough to run on
every request.

---

## 6. What this run does **not** prove

Stated deliberately, because over-claiming is the fastest way to lose marks under questioning:

| Not measured | Why | Where it is discussed |
|---|---|---|
| Availability, backup restore (NFR-02) | needs production operation over a calendar month | Assignment 4 §4.3, limitation 2 |
| Practitioner task time and SUS (NFR-04) | needs a user pilot; none has been run | Assignment 4 §4.3, limitation 2 |
| Google Calendar push (FR-05) | implemented against the API but needs firm OAuth credentials; the `.ics` export is demonstrated instead | Assignment 4 §1.2, Table 1 |
| OCR for scanned documents | deferred; such documents end in FAILED with Retry | Assignment 4 §1.2, Table 1 |
| Accuracy on a large real corpus | the annotated set is 3 documents / 7 fields | Assignment 4 §4.3, limitation 1 |

---

## 7. Log inventory

| File | Contents |
|---|---|
| `logs/00-run-timestamp.txt` | UTC timestamps marking the start of each phase |
| `logs/01-mvn-test.log` | full Maven output, including Surefire per-class results |
| `logs/02-e2e-ai-battery.log` | the eight-document upload battery, with expected vs actual |
| `logs/03-api-security-evidence.log` | the 20 API/security checks with HTTP codes and messages |
| `logs/04-explainability-check.log` | every extracted field with its confidence, page and span |
| `docs/metrics/metrics-results.md` | PT-01/02/03 measurements, regenerated by the test run |

---

*Prepared 28 October 2026. This report accompanies the Assignment 4 submission and the
Assignment 5 final demonstration; it should be regenerated (one command each) on the day of the
presentation so the figures are current.*
