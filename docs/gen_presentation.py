#!/usr/bin/env python3
"""Generates a presentation-ready summary deck (PPTX) for the ISJ107V project.
Theme: navy + gold, with embedded screenshots and speaker notes."""
import os
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "LegalCase_ERP_Presentation.pptx")
SHOTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "screenshots")

NAVY = RGBColor(0x16, 0x23, 0x3C)
NAVY2 = RGBColor(0x1E, 0x30, 0x50)
GOLD = RGBColor(0xC9, 0xA2, 0x27)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
INK = RGBColor(0x24, 0x30, 0x4A)
MUTED = RGBColor(0x66, 0x73, 0x8C)
LIGHT = RGBColor(0xEE, 0xF1, 0xF7)
GREEN = RGBColor(0x2E, 0x7D, 0x32)
RED = RGBColor(0xC6, 0x28, 0x28)

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
W, H = prs.slide_width, prs.slide_height
BLANK = prs.slide_layouts[6]

def add_slide():
    return prs.slides.add_slide(BLANK)

def rect(slide, x, y, w, h, fill, line=None):
    sh = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, x, y, w, h)
    sh.fill.solid(); sh.fill.fore_color.rgb = fill
    if line:
        sh.line.color.rgb = line; sh.line.width = Pt(1)
    else:
        sh.line.fill.background()
    sh.shadow.inherit = False
    return sh

def txt(slide, x, y, w, h, text, size=18, bold=False, color=INK, align=PP_ALIGN.LEFT,
        font="Calibri", anchor=MSO_ANCHOR.TOP, italic=False, wrap=True, line_spacing=1.0):
    tb = slide.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame
    tf.word_wrap = wrap
    tf.vertical_anchor = anchor
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = align
        p.line_spacing = line_spacing
        r = p.add_run(); r.text = ln
        r.font.name = font; r.font.size = Pt(size); r.font.bold = bold
        r.font.italic = italic; r.font.color.rgb = color
    return tb

def bullets(slide, x, y, w, h, items, size=17, color=INK, gap=6, bold_first=False):
    tb = slide.shapes.add_textbox(x, y, w, h)
    tf = tb.text_frame; tf.word_wrap = True
    for i, it in enumerate(items):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(gap); p.line_spacing = 1.05
        r = p.add_run(); r.text = ("\u2022  " if not it.startswith("\u2022") else "") + it
        r.font.name = "Calibri"; r.font.size = Pt(size); r.font.color.rgb = color
        if bold_first and ":" in it:
            seg = it.split(":", 1)
            p.runs[0].text = "\u2022  " + seg[0] + ":"
            p.runs[0].font.bold = True
            r2 = p.add_run(); r2.text = seg[1]
            r2.font.name = "Calibri"; r2.font.size = Pt(size); r2.font.color.rgb = color
    return tb

def pic(slide, path, x, y, w=None, h=None):
    return slide.shapes.add_picture(path, x, y, w, h)

def header(slide, title, kicker=None):
    rect(slide, 0, 0, W, Inches(1.0), NAVY)
    rect(slide, 0, Inches(1.0), W, Pt(3), GOLD)
    txt(slide, Inches(0.6), Inches(0.12), Inches(10), Inches(0.8), title,
        size=28, bold=True, color=WHITE, anchor=MSO_ANCHOR.MIDDLE)
    if kicker:
        txt(slide, Inches(11.2), Inches(0.12), Inches(1.8), Inches(0.8), kicker,
            size=11, color=GOLD, align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)

def footer(slide, n):
    txt(slide, Inches(0.6), Inches(7.12), Inches(8), Inches(0.3),
        "Legal Case Management ERP  \u00b7  ISJ107V 2026  \u00b7  Ally Mongala (219361629)",
        size=10, color=MUTED)
    txt(slide, Inches(12.4), Inches(7.12), Inches(0.6), Inches(0.3), str(n),
        size=10, color=MUTED, align=PP_ALIGN.RIGHT)

def notes(slide, text):
    slide.notes_slide.notes_text_frame.text = text

# ================= SLIDE 1 — TITLE =================
s = add_slide()
rect(s, 0, 0, W, H, NAVY)
rect(s, 0, Inches(4.6), W, Pt(3), GOLD)
txt(s, Inches(1.0), Inches(2.0), Inches(11.3), Inches(1.4),
    "Legal Case Management ERP", size=54, bold=True, color=WHITE)
txt(s, Inches(1.0), Inches(3.3), Inches(11.3), Inches(0.9),
    "An integrated, AI-driven LegalTech system for small law firms",
    size=24, color=GOLD)
txt(s, Inches(1.0), Inches(5.0), Inches(11.3), Inches(1.4),
    "ISJ107V Integrated Software Project  \u00b7  2026\nAlly Mongala (219361629)",
    size=18, color=WHITE)
notes(s, "Welcome. Today I present the Legal Case Management ERP - an integrated software "
         "project for small law firms. The system combines a central case database, real AI "
         "document understanding, calendar synchronisation, client and admin portals, and "
         "real-time notifications.")

# ================= SLIDE 2 — AGENDA =================
s = add_slide(); header(s, "Agenda")
items = [
    "The business problem",
    "The solution & architecture",
    "Key features - lawyer, client, admin",
    "Emerging technologies used",
    "AI document pipeline & validation",
    "Live demo highlights",
    "Deployment - components on different machines",
    "Security & compliance",
    "Testing & verification",
    "Roadmap & SMART goals",
]
bullets(s, Inches(1.0), Inches(1.6), Inches(11.3), Inches(5.4), items, size=20, gap=10)
notes(s, "Here is the plan for today's presentation.")

# ================= SLIDE 3 — PROBLEM =================
s = add_slide(); header(s, "The business problem")
bullets(s, Inches(0.9), Inches(1.5), Inches(6.4), Inches(5.0), [
    "Small law firms rely on email, spreadsheets and manual filing",
    "Missed deadlines \u2192 malpractice risk and reputational damage",
    "Poor client communication \u2192 low satisfaction",
    "Manual document review eats into billable time",
    "Enterprise ERPs are too costly for small practices",
], size=18, gap=10)
rect(s, Inches(7.6), Inches(1.6), Inches(5.0), Inches(5.2), LIGHT)
txt(s, Inches(7.9), Inches(1.8), Inches(4.4), Inches(0.6), "Impact", size=20, bold=True, color=NAVY)
bullets(s, Inches(7.9), Inches(2.5), Inches(4.4), Inches(4.0), [
    "Missed court dates and filing deadlines",
    "Frustrated clients switching firms",
    "Lost billable hours on repetitive review",
    "No audit trail for compliance",
], size=16, gap=8)
notes(s, "Explain the pain points: fragmentation, missed deadlines, poor communication, and the "
         "cost of enterprise systems for small firms.")

# ================= SLIDE 4 — SOLUTION / ARCHITECTURE =================
s = add_slide(); header(s, "The solution - an integrated system")
txt(s, Inches(0.9), Inches(1.3), Inches(11.5), Inches(0.6),
    "Browser + JavaFX clients \u2194 Javalin REST API \u2194 H2 / PostgreSQL \u2194 AI pipeline \u2194 Google Calendar",
    size=15, bold=True, color=NAVY2, align=PP_ALIGN.CENTER)
boxes = [
    ("Clients", "Web client + JavaFX desktop\n(login, dashboards, portals)", NAVY2),
    ("Backend", "Javalin REST API\nJWT + role scopes, SSE events", NAVY),
    ("Data", "H2 / PostgreSQL\nusers, cases, documents,\nextractions, summaries", GOLD),
    ("AI", "PDF/Word \u2192 text \u2192\nextraction + validation +\n3-paragraph summary", NAVY2),
    ("Integrations", "Google Calendar API\n.ics per case, audit, reports", NAVY),
]
x = Inches(0.6); y = Inches(2.3); w = Inches(2.35); h = Inches(3.6)
for i, (t, d, c) in enumerate(boxes):
    rect(s, x, y, w, h, LIGHT)
    rect(s, x, y, w, Inches(0.7), c)
    txt(s, x + Inches(0.1), y + Inches(0.05), w - Inches(0.2), Inches(0.6), t,
        size=16, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    txt(s, x + Inches(0.15), y + Inches(0.9), w - Inches(0.3), h - Inches(1.0), d,
        size=12.5, color=INK, align=PP_ALIGN.CENTER)
    x += w + Inches(0.18)
txt(s, Inches(0.9), Inches(6.3), Inches(11.5), Inches(0.8),
    "Modular components \u00b7 interoperability via REST + JSON \u00b7 automation via the AI worker \u00b7 "
    "scalable containers \u00b7 data synchronisation (calendar + real-time events)",
    size=14, color=MUTED, align=PP_ALIGN.CENTER)
notes(s, "This is the architecture. It is an integrated software project: modular components "
         "(clients, API, database, AI, integrations) interoperating through REST and JSON, with "
         "automation and data synchronisation.")

# ================= SLIDE 5 — LAWYER FEATURES =================
s = add_slide(); header(s, "Lawyer features")
bullets(s, Inches(0.9), Inches(1.5), Inches(6.3), Inches(5.3), [
    "Upload PDF / Word documents (FR1)",
    "AI extraction: dates, parties, attorneys, judge, key facts with confidence + source span (FR2)",
    "3-paragraph summary: Facts / Legal Issues / Current Status (FR3)",
    "Deadlines: add, sync, unsync, per-case .ics export (FR4)",
    "Client messaging with instant alert bell (FR5)",
    "Case status / edit / delete with client alerts",
    "View client ratings, download audit history",
    "Dashboard with live, clickable charts",
], size=16, gap=7)
pic(s, os.path.join(SHOTS, "final_dashboard_charts_clean.png"), Inches(7.5), Inches(1.5),
    w=Inches(5.3), h=Inches(3.1))
txt(s, Inches(7.5), Inches(4.7), Inches(5.3), Inches(0.4), "Dashboard - live charts", size=12, italic=True, color=MUTED, align=PP_ALIGN.CENTER)
pic(s, os.path.join(SHOTS, "final_blocked_doc.png"), Inches(7.5), Inches(5.1),
    w=Inches(5.3), h=Inches(2.1))
txt(s, Inches(7.5), Inches(7.0), Inches(5.3), Inches(0.4), "", size=8)
notes(s, "Lawyers get the full case-management workflow. Highlight the AI extraction with "
         "confidence and source spans, the 3-paragraph summary, the calendar sync and the "
         "instant messaging bell.")

# ================= SLIDE 6 — CLIENT FEATURES =================
s = add_slide(); header(s, "Client features")
bullets(s, Inches(0.9), Inches(1.5), Inches(6.3), Inches(5.3), [
    "Secure portal - approved summaries only (FR6)",
    "Upcoming deadlines with countdowns",
    "Download shared documents to their machine",
    "Share documents with their lawyer (with POPIA consent)",
    "Delete documents they uploaded",
    "Message the lawyer - instant notifications",
    "Rate the lawyer (1-5 stars) (FR7)",
], size=17, gap=9)
pic(s, os.path.join(SHOTS, "final_client_bell_fixed.png"), Inches(7.5), Inches(1.5),
    w=Inches(5.3), h=Inches(3.0))
txt(s, Inches(7.5), Inches(4.6), Inches(5.3), Inches(0.4), "Client portal - bell clears after reading", size=12, italic=True, color=MUTED, align=PP_ALIGN.CENTER)
pic(s, os.path.join(SHOTS, "web_v2_client_docs_delete.png"), Inches(7.5), Inches(5.0),
    w=Inches(5.3), h=Inches(2.2))
notes(s, "Clients see exactly what the firm approves for them - summaries, deadlines, documents "
         "and messages - and can rate their lawyer.")

# ================= SLIDE 7 — ADMIN + COMPLIANCE =================
s = add_slide(); header(s, "Admin & compliance")
bullets(s, Inches(0.9), Inches(1.5), Inches(6.3), Inches(5.3), [
    "User roles & revoke access (FR8)",
    "Audits & reports - download CSV (FR9)",
    "Rejection reasons report",
    "System metrics, retraining runs, datasets (FR10)",
    "Tamper-evident hash-chained audit log (FR11)",
    "Encryption at rest - AES-256-GCM (FR11)",
    "POPIA consent controls + data residency",
], size=17, gap=9)
pic(s, os.path.join(SHOTS, "final_admin_rejections.png"), Inches(7.5), Inches(1.5),
    w=Inches(5.3), h=Inches(3.0))
txt(s, Inches(7.5), Inches(4.6), Inches(5.3), Inches(0.4), "Rejected documents report", size=12, italic=True, color=MUTED, align=PP_ALIGN.CENTER)
pic(s, os.path.join(SHOTS, "final_read_full.png"), Inches(7.5), Inches(5.0),
    w=Inches(5.3), h=Inches(2.2))
notes(s, "Admins manage access, audits, reports and the model - with POPIA and Law Society "
         "compliance built in.")

# ================= SLIDE 8 — EMERGING TECHNOLOGIES =================
s = add_slide(); header(s, "Emerging technologies")
rows = [
    ("AI / NLP", "Real text extraction with confidence, source spans and pages; extractive 3-paragraph summaries; relevance validation that blocks wrong documents"),
    ("Real-time (SSE)", "Server-Sent Events push - message bell updates in ~7 ms; dashboard charts refresh live on deadline changes"),
    ("Cloud-ready", "Google Calendar v3 API (OAuth2), encrypted storage, data-residency config; PostgreSQL + Docker for any cloud"),
    ("DevOps", "Docker multi-stage build, docker-compose (db + backend + web), GitHub Actions CI/CD (build + smoke + e2e)"),
    ("Multi-platform", "Web client + JavaFX desktop client on one API; .ics exports interoperate with Apple, Outlook and Google calendars"),
]
y = Inches(1.4)
for name, desc in rows:
    rect(s, Inches(0.9), y, Inches(2.3), Inches(1.0), NAVY2)
    txt(s, Inches(1.0), y + Inches(0.05), Inches(2.1), Inches(0.9), name, size=16, bold=True,
        color=WHITE, anchor=MSO_ANCHOR.MIDDLE)
    rect(s, Inches(3.2), y, Inches(9.2), Inches(1.0), LIGHT)
    txt(s, Inches(3.4), y + Inches(0.06), Inches(8.8), Inches(0.9), desc, size=14,
        color=INK, anchor=MSO_ANCHOR.MIDDLE)
    y += Inches(1.08)
notes(s, "Map each to learning outcomes A.1 and D.1: trends, cloud, DevOps, multi-platform.")

# ================= SLIDE 9 — AI PIPELINE =================
s = add_slide(); header(s, "AI document pipeline & validation")
steps = [
    ("Upload", "PDF/Word\n+ consent", NAVY2),
    ("Extract", "text page-by-page\nPDFBox / POI", NAVY),
    ("NLP", "fields + confidence\n+ source span", GOLD),
    ("Validate", "relevant to case?\nblock if wrong", RED),
    ("Summarise", "3 paragraphs\nFacts/Legal/Status", GREEN),
]
x = Inches(0.6); y = Inches(1.6); w = Inches(2.3); h = Inches(2.4)
for i, (t, d, c) in enumerate(steps):
    rect(s, x, y, w, h, LIGHT)
    rect(s, x, y, w, Inches(0.6), c)
    txt(s, x + Inches(0.1), y + Inches(0.03), w - Inches(0.2), Inches(0.54), t,
        size=15, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    txt(s, x + Inches(0.12), y + Inches(0.75), w - Inches(0.24), h - Inches(0.85), d,
        size=12.5, color=INK, align=PP_ALIGN.CENTER)
    if i < len(steps) - 1:
        txt(s, x + w - Inches(0.05), y + Inches(0.9), Inches(0.3), Inches(0.6), "\u2192",
            size=24, bold=True, color=GOLD, align=PP_ALIGN.CENTER)
    x += w + Inches(0.28)
pic(s, os.path.join(SHOTS, "web_v3_auto_block.png"), Inches(0.6), Inches(4.4),
    w=Inches(6.0), h=Inches(2.8))
pic(s, os.path.join(SHOTS, "web_v3_auto_extraction.png"), Inches(6.9), Inches(4.4),
    w=Inches(6.0), h=Inches(2.8))
txt(s, Inches(0.6), Inches(7.05), Inches(12.1), Inches(0.4),
    "Wrong or irrelevant documents are BLOCKED with a visible reason; matching documents are processed instantly.",
    size=13, color=RED, align=PP_ALIGN.CENTER)
notes(s, "Walk through the pipeline. Emphasise the validation: a grocery list or a document "
         "about a different matter is blocked; a matching document is processed instantly.")

# ================= SLIDE 10 — DEMO HIGHLIGHTS =================
s = add_slide(); header(s, "Live demo highlights")
demo = [
    ("Login", "Three roles - lawyer, client, admin (demo accounts on the login screen)"),
    ("Dashboard", "KPI cards, live deadlines chart, clickable status chart, unread bell"),
    ("Upload & AI", "Upload mokoena_affidavit.pdf - processed instantly; upload a wrong-matter file - blocked with reason"),
    ("Summary", "Review the 3-paragraph summary and approve - client is notified"),
    ("Calendar", "Sync deadlines, export the per-case .ics, open it in your phone calendar"),
    ("Messaging", "Client sends a message - lawyer's bell lights up in ~7 ms; click jumps to the chat"),
    ("Admin", "Revoke a user, view the rejection reasons report, download CSVs"),
]
y = Inches(1.35)
for name, desc in demo:
    rect(s, Inches(0.9), y, Inches(2.1), Inches(0.72), GOLD)
    txt(s, Inches(1.0), y + Inches(0.03), Inches(1.9), Inches(0.66), name, size=14, bold=True,
        color=WHITE, anchor=MSO_ANCHOR.MIDDLE)
    rect(s, Inches(3.0), y, Inches(9.3), Inches(0.72), LIGHT)
    txt(s, Inches(3.2), y + Inches(0.04), Inches(8.9), Inches(0.66), desc, size=13.5,
        color=INK, anchor=MSO_ANCHOR.MIDDLE)
    y += Inches(0.8)
notes(s, "Run the live demo in this order - it tells the full story.")

# ================= SLIDE 11 — DEPLOYMENT =================
s = add_slide(); header(s, "Deployment - components on different machines")
boxes = [
    ("web", "Nginx\nserves client +\nproxies API/SSE", NAVY2),
    ("backend", "Javalin API\n+ AI worker", NAVY),
    ("db", "PostgreSQL 16\ncentral database", GOLD),
]
x = Inches(1.2); y = Inches(1.8); w = Inches(3.3); h = Inches(2.4)
for i, (t, d, c) in enumerate(boxes):
    rect(s, x, y, w, h, LIGHT)
    rect(s, x, y, w, Inches(0.6), c)
    txt(s, x + Inches(0.1), y + Inches(0.03), w - Inches(0.2), Inches(0.54), t,
        size=16, bold=True, color=WHITE, align=PP_ALIGN.CENTER)
    txt(s, x + Inches(0.15), y + Inches(0.8), w - Inches(0.3), h - Inches(0.9), d,
        size=13, color=INK, align=PP_ALIGN.CENTER)
    if i < len(boxes) - 1:
        txt(s, x + w - Inches(0.1), y + Inches(0.9), Inches(0.5), Inches(0.6), "\u2192",
            size=26, bold=True, color=GOLD, align=PP_ALIGN.CENTER)
    x += w + Inches(0.7)
txt(s, Inches(1.2), Inches(4.6), Inches(11.0), Inches(0.6),
    "docker compose up --build   \u2192   open http://localhost:8080", size=16, bold=True,
    color=NAVY2, align=PP_ALIGN.CENTER)
bullets(s, Inches(1.2), Inches(5.3), Inches(11.0), Inches(1.8), [
    "Three separate containers - db, backend, web (frontend and backend on different hosts)",
    "Google Calendar API and .ics export integrate with external services",
    "GitHub Actions CI/CD builds, tests and packages the image on every push",
], size=15, gap=8)
notes(s, "This directly meets the project rubric: the system works in a real live environment "
         "with its components hosted on different machines.")

# ================= SLIDE 12 — SECURITY =================
s = add_slide(); header(s, "Security & compliance")
bullets(s, Inches(0.9), Inches(1.5), Inches(11.5), Inches(5.2), [
    "JWT authentication with role-based scopes (read:case, write:case, read:client, admin:model)",
    "Passwords hashed with PBKDF2-HMAC-SHA256 (120 000 iterations)",
    "Documents encrypted at rest with AES-256-GCM (FR11)",
    "Client-owner checks - a client can only access their own cases",
    "Revoked accounts are rejected on every API call, instantly",
    "POPIA consent captured before processing; data-residency configurable",
    "Tamper-evident audit log with a SHA-256 hash chain (FR10/FR11)",
], size=17, gap=10)
notes(s, "Summarise the security story - it is a major strength for the report.")

# ================= SLIDE 13 — TESTING =================
s = add_slide(); header(s, "Testing & verification")
rows = [
    ("Service smoke tests", "17 assertions - auth, extraction, summary, sync, audit", GREEN),
    ("API e2e suites", "Full journeys incl. 401/403 role checks", GREEN),
    ("Test-case uploader", "8 scenarios - all pass (PROCESSED / BLOCKED / FAILED)", GREEN),
    ("Browser acceptance", "Live bell ~7 ms, charts, client flows (Playwright)", GREEN),
    ("CI/CD", "Build + smoke + e2e on every push (GitHub Actions)", GREEN),
]
y = Inches(1.4)
for name, desc, c in rows:
    rect(s, Inches(0.9), y, Inches(3.2), Inches(0.8), c)
    txt(s, Inches(1.05), y + Inches(0.04), Inches(3.0), Inches(0.72), name, size=15, bold=True,
        color=WHITE, anchor=MSO_ANCHOR.MIDDLE)
    rect(s, Inches(4.1), y, Inches(8.0), Inches(0.8), LIGHT)
    txt(s, Inches(4.3), y + Inches(0.05), Inches(7.6), Inches(0.72), desc, size=14,
        color=INK, anchor=MSO_ANCHOR.MIDDLE)
    y += Inches(0.9)
rect(s, Inches(0.9), Inches(5.9), Inches(11.2), Inches(1.0), LIGHT)
txt(s, Inches(1.2), Inches(6.0), Inches(10.6), Inches(0.9),
    "Test matrix: matching \u2192 PROCESSED  \u00b7  wrong-matter / no-information \u2192 BLOCKED  \u00b7  "
    "blank scan \u2192 FAILED (retry available)  - 8/8 green", size=15, bold=True, color=NAVY2,
    anchor=MSO_ANCHOR.MIDDLE)
notes(s, "Evidence-based: every layer is tested and green.")

# ================= SLIDE 14 — ROADMAP / GOALS =================
s = add_slide(); header(s, "Roadmap & SMART goals")
goals = [
    ("Goal 1", "Extract case details from 90% of legal PDFs by Sep 2026", "Built - extraction with confidence + spans; validation",
     "On track"),
    ("Goal 2", "Reduce missed deadlines by 40% via Google Calendar by Dec 2026", "Built - auto sync, reminders, per-case .ics", "On track"),
    ("Goal 3", "Raise client satisfaction 25% via AI summaries + portal by Dec 2026", "Built - approved summaries, portal, ratings", "On track"),
    ("Vision", "Improve small-firm efficiency by 25% by Dec 2026", "System delivered; pilot adoption is the next phase", "In progress"),
]
y = Inches(1.35)
for g, goal, done, status in goals:
    rect(s, Inches(0.9), y, Inches(1.5), Inches(1.15), NAVY2)
    txt(s, Inches(0.95), y + Inches(0.05), Inches(1.4), Inches(1.05), g, size=15, bold=True,
        color=WHITE, anchor=MSO_ANCHOR.MIDDLE)
    rect(s, Inches(2.4), y, Inches(8.0), Inches(1.15), LIGHT)
    txt(s, Inches(2.6), y + Inches(0.08), Inches(7.6), Inches(1.0), goal + "\n" + done,
        size=13, color=INK)
    rect(s, Inches(10.4), y, Inches(2.0), Inches(1.15), GREEN)
    txt(s, Inches(10.45), y + Inches(0.05), Inches(1.9), Inches(1.05), status, size=13, bold=True,
        color=WHITE, anchor=MSO_ANCHOR.MIDDLE, align=PP_ALIGN.CENTER)
    y += Inches(1.3)
notes(s, "Link each deliverable back to the SMART goals from the proposal - all built.")

# ================= SLIDE 15 — DEMO ACCOUNTS =================
s = add_slide(); header(s, "Demo accounts")
tbl = [
    ["Role", "Email", "Password", "What to explore"],
    ["Lawyer", "ally@mongalalaw.co.za", "lawyer123", "Dashboard, Cases, AI extraction, Calendar, Ratings"],
    ["Client", "thabo@example.com", "client123", "My Case, Documents, Messages, Rate Lawyer"],
    ["Admin", "admin@mongalalaw.co.za", "admin123", "Users & Roles, Audits & Reports, System & Model"],
]
x0, y0 = Inches(0.9), Inches(1.6)
colw = [Inches(1.6), Inches(4.0), Inches(1.9), Inches(4.4)]
rowh = Inches(0.7)
for r, row in enumerate(tbl):
    x = x0
    for c, val in enumerate(row):
        fill = NAVY if r == 0 else (LIGHT if r % 2 == 0 else WHITE)
        rect(s, x, y0 + r * rowh, colw[c], rowh, fill)
        txt(s, x + Inches(0.12), y0 + r * rowh + Inches(0.03), colw[c] - Inches(0.24), rowh - Inches(0.06),
            val, size=14, bold=(r == 0), color=WHITE if r == 0 else INK, anchor=MSO_ANCHOR.MIDDLE)
        x += colw[c]
txt(s, Inches(0.9), Inches(5.2), Inches(11.5), Inches(1.6),
    "The login page is fully manual (no demo buttons). These accounts are seeded into the "
    "database on first start, with realistic matters, documents, deadlines and messages - "
    "including a deliberately BLOCKED document to demonstrate the AI validation.",
    size=15, color=MUTED)
notes(s, "Give the marker these accounts so they can try everything.")

# ================= SLIDE 16 — THANK YOU =================
s = add_slide()
rect(s, 0, 0, W, H, NAVY)
rect(s, 0, Inches(4.2), W, Pt(3), GOLD)
txt(s, Inches(1.0), Inches(2.4), Inches(11.3), Inches(1.2),
    "Thank you", size=54, bold=True, color=WHITE)
txt(s, Inches(1.0), Inches(3.5), Inches(11.3), Inches(0.8),
    "Questions?", size=24, color=GOLD)
txt(s, Inches(1.0), Inches(4.6), Inches(11.3), Inches(2.0),
    "Legal Case Management ERP\nISJ107V Integrated Software Project \u00b7 2026 \u00b7 Ally Mongala (219361629)",
    size=18, color=WHITE)
notes(s, "Thank the audience and invite questions.")

prs.save(OUT)
print("saved:", OUT)
print("slides:", len(prs.slides.__iter__.__self__._sldIdLst))
