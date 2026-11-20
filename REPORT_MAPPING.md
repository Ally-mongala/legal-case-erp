# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").


---

## Latest round of changes (instant message bell)

- Added `GET /api/events` (Server-Sent Events) in the backend; `EventBus` keeps a list
  of connected SSE clients and publishes `new-message` (with the case id) whenever
  `MessageService.insert` succeeds - i.e. the moment a client sends a message, a lawyer
  replies, a summary is approved, or a case is updated.
- Browser client keeps a live SSE connection (fetch + ReadableStream parser, JWT in
  header, `Accept: text/event-stream`) and refreshes the unread bell instantly on
  `new-message`. A 30s poll and a 3s auto-reconnect act as fallbacks; the stream is
  aborted on logout.
- Verified end-to-end: bell appeared ~7ms after a client message (previously up to 8s).
- JavaFX desktop bell poll reduced 8s -> 3s.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").


---

## Latest round of changes (instant message bell)

- Added `GET /api/events` (Server-Sent Events) in the backend; `EventBus` keeps a list
  of connected SSE clients and publishes `new-message` (with the case id) whenever
  `MessageService.insert` succeeds - i.e. the moment a client sends a message, a lawyer
  replies, a summary is approved, or a case is updated.
- Browser client keeps a live SSE connection (fetch + ReadableStream parser, JWT in
  header, `Accept: text/event-stream`) and refreshes the unread bell instantly on
  `new-message`. A 30s poll and a 3s auto-reconnect act as fallbacks; the stream is
  aborted on logout.
- Verified end-to-end: bell appeared ~7ms after a client message (previously up to 8s).
- JavaFX desktop bell poll reduced 8s -> 3s.


---

## Latest round of changes (dashboard charts)

- Deadline-per-month bar chart cleaned: removed the count numbers and the cramped
  MM/YY labels that rendered as jumbled text ("408/09/26..."); bars now show a clean
  month name (e.g. "Sep 2026") and a hover tooltip with the count.
- Both dashboard charts are clickable:
  - Deadline bar -> Calendar view filtered to that month (`show('calendar',{month})`).
  - Status row -> Cases view pre-filtered by that status (`show('cases',{status})`),
    with a new status drop-down on the Cases screen.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").


---

## Latest round of changes (instant message bell)

- Added `GET /api/events` (Server-Sent Events) in the backend; `EventBus` keeps a list
  of connected SSE clients and publishes `new-message` (with the case id) whenever
  `MessageService.insert` succeeds - i.e. the moment a client sends a message, a lawyer
  replies, a summary is approved, or a case is updated.
- Browser client keeps a live SSE connection (fetch + ReadableStream parser, JWT in
  header, `Accept: text/event-stream`) and refreshes the unread bell instantly on
  `new-message`. A 30s poll and a 3s auto-reconnect act as fallbacks; the stream is
  aborted on logout.
- Verified end-to-end: bell appeared ~7ms after a client message (previously up to 8s).
- JavaFX desktop bell poll reduced 8s -> 3s.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.
# How the system maps to your assignment — FR1–FR11 edition

This maps the rebuilt system to the **revised functional requirements** (lawyer /
client / admin / regulatory) plus the login-page fixes you asked for.

---

## Login page — the two fixes you requested

1. **Show/Hide password now works.** The previous implementation swapped the
   PasswordField for a TextField in the layout, which broke the toggle. The new
   implementation (`LoginController.togglePasswordVisibility`) keeps both fields in a
   **StackPane overlay**: toggling just flips visibility/managed flags, so the layout
   never jumps and whatever you typed is preserved.
2. **Manual login only.** The demo quick-fill buttons were removed from `login.fxml`.
   Everyone types their credentials; the backend returns a role-based JWT and the shell
   builds the workspace for that role. If an admin has revoked an account, the login is
   rejected with a clear message (FR8).

## Lawyer requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR1 upload PDF **or Word** | File chooser accepts `.pdf/.docx/.doc`; backend uses PDFBox **and** Apache POI; consent checkbox before processing | `DocumentController.upload`, `nlp/DocumentTextExtractor`, `case_detail.fxml` |
| FR2 extract dates, parties, **attorneys, judge**, case number, key facts | Extraction table shows all of these with confidence + source span + page | `nlp/ExtractionEngine` (patterns `ATTORNEY`, `JUDGE`, `extractKeyFacts`) |
| FR3 summary = **Facts / Legal Issues / Current Status** | Case detail shows three labelled text areas; paragraphs start "Facts:", "Legal issues:", "Current status:" | `nlp/Summarizer`, `case_detail.fxml` |
| FR4 sync extracted **and manually captured** deadlines, reminders 7/3/1 days | "**+ Add deadline**" button opens a dialog (title, date, type, reminder combo 1/3/7/14); sync pushes to calendar gateway | `CalendarController.add`, `CaseDetailController.onAddDeadline` |
| FR5 lawyer↔client messages | Thread on case detail + client Messages tab; two-way | `MessageService`, `ClientMessagesController` |

## Client requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR6 secure portal (summaries, selected docs, messages) | Client nav: My Case / Documents / Messages; only approved summaries shown; only the client's own cases accessible (owner check) | `ClientPortalController`, `client_home/documents/messages.fxml` |
| FR7 rate the lawyer | New "**Rate Lawyer**" tab: 5 star buttons + comment + submit; stored server-side and shown in the firm's ratings list | `RatingController`, `ClientRatingsController`, `client_ratings.fxml` |

## Admin requirements

| Req | Evidence | Files / endpoints |
|---|---|---|
| FR8 view user roles + revoke access | "Users & Roles" screen: role badges, ACTIVE/REVOKED status, Revoke/Restore buttons; revoked accounts get HTTP 403 on login | `AdminController.listUsers/revoke/restore`, `AdminUsersController`, `admin_users.fxml` |
| FR9 view audits + download reports | "Audits & Reports": full hash-chained audit table + **Download audit report (CSV)** and **Download case register (CSV)** via a file save dialog | `AdminController.auditCsv/casesCsv`, `AdminAuditController`, `admin_audit.fxml` |
| FR10 manage system, usage metrics, approve retraining, manage datasets | "System & Model": metrics grid (users, cases, docs, avg confidence, ratings, residency, encryption), training-runs table with **Approve run** (human-in-the-loop ETR3), datasets table | `AdminController.metrics/trainingRuns/approveTrainingRun/datasets`, `AdminSystemController`, `admin_system.fxml` |

## Regulatory / Plus (FR11)

- **Tamper-evident audit**: `audit_log` stores a SHA-256 of (row content + previous
  row's hash) — `AuditService` builds the chain; the admin audit screen shows it.
- **Encryption of client data**: uploaded documents are stored **AES-256-GCM encrypted at
  rest** (`security/EncryptionUtil`); the AI pipeline decrypts in memory only.
- **Consent controls**: upload requires a POPIA consent checkbox; without it the backend
  returns 400 and nothing is stored/processed. Consent is persisted per document.
- **Data residency**: `LCE_DATA_RESIDENCY` (default `ZA`) surfaced on the admin System
  screen and in metrics.

## Security notes for your report

- JWT with role-based scopes (`read:case`, `write:case`, `read:client`, `write:client`,
  `read:audit`, `write:audit`, `admin:model`).
- Client owner checks: a client token can only read **their own** cases.
- Verified in `docs/e2e_v2.sh`: client → `/api/admin/*` = 403, lawyer → admin = 403,
  no token = 401, revoked user login = 403, consent=false upload = 400.

## Demo script evidence

`docs/e2e_v2.sh` (all green) shows, end-to-end:

- Word (.docx) upload → PROCESSED with `docType=WORD`, `consent=true`
- Extractions: `Hearing Date 2026-12-05`, `Attorney Ntuli & Partners Inc`,
  `Judge Khumalo`, `Claim Amount R1,050,000.00`, `Key Fact 1/2`
- Summary paragraphs labelled Facts / Legal issues / Current status
- Manual deadline `File discovery affidavit @ 2026-10-01 reminder 3`
- Client rating `Ally Mongala 5/5`
- Admin revoked `Elna van Wyk` → her login now returns 403
- CSV audit report downloaded (13+ rows)
- Metrics: `avgConfidence 0.84`, `dataResidency ZA`, `encryption AES-256-GCM at rest`
- Retraining run `tr1` approved; datasets listed

## Honest notes

1. **Stack**: the brief asked for Java, so the backend is a Javalin REST API (same role
   as Express/FastAPI) and the client is JavaFX. Say this explicitly in the report.
2. **NLP is rule-based** (genuine text processing: sentence scoring, spans, confidence)
   implementing the contract of a fine-tuned transformer; swap
   `ExtractionEngine`/`Summarizer` for a Hugging Face model without touching the API or
   UI.
3. **Encryption & audit-chain are real** in this build; the production hardening would
   add key rotation and append-only storage.


---

## Latest round of changes (this build)

**Lawyer**
- Calendar Sync screen + case detail now **synchronise and unsynchronise** (per-deadline Sync/Unsync toggle, Sync all / Unsync all, and case-level `calendar-sync` / `calendar-unsync`).
- **Edit case status** from a dropdown on the case header; **edit case details**; **delete case** (with confirmation). Every change **alerts the client** with a portal message.
- **Message chat** with the client from the case detail (subject + body), with a **red alert icon in the header** that polls for new client messages (count badge; clicking jumps to the relevant case).
- **Download the Activity & Audit history** as CSV (new button on the Activity screen, `GET /api/activity.csv`).
- **View client ratings** (new "Client Ratings" screen with average score).

**Client**
- **Download shared documents** and save them locally — real decrypted bytes from `GET /api/documents/{id}/download`.
- **Share documents with the lawyer** ("+ Share a document" button) with the POPIA consent checkbox.

**Admin**
- **Upload new training datasets** and **download existing ones** (`POST/GET /api/admin/datasets`, `/{id}/download`).
- **Download the whole system & model management report** (`/api/admin/reports/system.csv`).
- **Revoke now works properly**: revoked accounts are rejected not just at login but on **every API request** (the JWT session is re-checked against the user's active flag).

**UI keyword cleanup**
All the requested strings (assignment/FR/ETR jargon and meta-labels) were removed from the UI and replaced with neutral, professional text — verified by grep across the FXML resources.


---

## Latest round of changes (redevelop)

1. **Matter type is a drop-down** (28 practice areas) when creating a new case -
   served by `GET /api/matter-types` (backend) and rendered as a `<select>` in the web UI
   and a `ComboBox` in the JavaFX client.
2. **Document delete** (lawyer + client): `DELETE /api/documents/{id}` removes the row,
   the encrypted file on disk, and cascades extractions/summaries. Client deletions are
   restricted to their own case (owner check, verified 403 on another client's case).
3. **Read the uploaded document in full**: the pipeline now caches the full extracted
   text (`documents.full_text`); `GET /api/documents/{id}/content` returns it and the UI
   shows it in a scrollable "Read full" panel (both web and JavaFX).
4. **Calendar sync to your phone/computer**: `GET /api/calendar/export.ics` builds a
   cumulative `.ics` (every synced deadline with VALARM reminders); the Calendar screen
   has a "Download calendar (.ics)" button - open the file with Apple Calendar / Outlook /
   Google Calendar to import.


---

## Latest round of changes (redevelop - dashboard / AI validation / immediate processing / per-case calendar)

1. **Dashboard on login (connected mode)**: lawyer landing view now shows a Dashboard with
   KPI stats (active matters, deadlines next 30 days, overdue, average client rating,
   unread client messages) and lists of upcoming deadlines + recent activity. Both web
   (`dashboardView`) and JavaFX (existing `LawyerDashboardController`) land here first.
2. **AI validation & document blocking**: `AiPipeline` now runs a relevance check after
   extraction - if no parties/reference are found or the extracted parties belong to a
   different matter, the document is set to **REJECTED (BLOCKED)** with a stored reason
   (`documents.rejection_reason`), and NO summary/deadlines are created. The extracted
   fields are kept so the lawyer can see what was highlighted ("Review AI"). Demo seed
   includes a blocked document (Nkosi v ABSA uploaded to case C-2026-014).
3. **Immediate processing**: the web client polls the document status after upload and
   automatically reveals the AI extraction panel (or the blocking reason) as soon as
   processing completes.
4. **Per-case calendar (.ics)**: `GET /api/cases/{id}/calendar.ics` exports only that
   case's synced deadlines; buttons added in web + JavaFX case detail ("Case .ics" /
   "Case calendar (.ics)").


---

## Latest round of changes (instant message bell)

- Added `GET /api/events` (Server-Sent Events) in the backend; `EventBus` keeps a list
  of connected SSE clients and publishes `new-message` (with the case id) whenever
  `MessageService.insert` succeeds - i.e. the moment a client sends a message, a lawyer
  replies, a summary is approved, or a case is updated.
- Browser client keeps a live SSE connection (fetch + ReadableStream parser, JWT in
  header, `Accept: text/event-stream`) and refreshes the unread bell instantly on
  `new-message`. A 30s poll and a 3s auto-reconnect act as fallbacks; the stream is
  aborted on logout.
- Verified end-to-end: bell appeared ~7ms after a client message (previously up to 8s).
- JavaFX desktop bell poll reduced 8s -> 3s.


---

## Latest round of changes (dashboard charts)

- Deadline-per-month bar chart cleaned: removed the count numbers and the cramped
  MM/YY labels that rendered as jumbled text ("408/09/26..."); bars now show a clean
  month name (e.g. "Sep 2026") and a hover tooltip with the count.
- Both dashboard charts are clickable:
  - Deadline bar -> Calendar view filtered to that month (`show('calendar',{month})`).
  - Status row -> Cases view pre-filtered by that status (`show('cases',{status})`),
    with a new status drop-down on the Cases screen.


---

## Latest round of changes (live dashboard chart)

- Extended the SSE event bus: `deadline-updated` is published from
  `DeadlineService.insert` (covers manual deadlines AND AI-extracted deadlines) and from
  `CalendarSyncService.syncOne` / `unsyncOne`.
- The browser's SSE handler now reacts to `deadline-updated` by re-rendering whatever
  view shows deadlines: Dashboard (chart, KPI stats, upcoming list), Calendar, or the
  open Case Detail - instantly, no polling.
- Verified end-to-end: while the dashboard was open, adding a deadline via the API
  changed the "DEADLINES IN 30 DAYS" stat from 8 to 9 without any manual refresh.
