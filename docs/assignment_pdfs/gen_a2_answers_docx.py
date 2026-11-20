#!/usr/bin/env python3
"""
ISJ107V Assignment 2 (S2 2026) - Formal System Proposal and Specification
Generates a submission-ready Word document answering Q1-Q5.

Student: Ally Mongala (219361629)
System:  LegalCase ERP - Legal Case Management ERP for small law firms
"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
UML = os.path.join(ROOT, "docs", "uml", "a2")
OUT = os.path.join(HERE, "Assignment2_ISJ107V_Ally_Mongala.docx")

NAVY = RGBColor(0x1F, 0x38, 0x64)
GREY = RGBColor(0x44, 0x44, 0x44)
BODY_FONT = "Calibri"

doc = Document()

# ---------------------------------------------------------------- page setup
for s in doc.sections:
    s.page_width = Cm(21.0)
    s.page_height = Cm(29.7)
    s.top_margin = Cm(2.54)
    s.bottom_margin = Cm(2.54)
    s.left_margin = Cm(3.17)
    s.right_margin = Cm(3.17)

CONTENT_W = Cm(21.0 - 3.17 - 3.17)   # 14.66 cm usable width

st = doc.styles["Normal"]
st.font.name = BODY_FONT
st.font.size = Pt(11)
st.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
pf = st.paragraph_format
pf.line_spacing = 1.5
pf.space_after = Pt(6)


def _fmt_heading(p, size, color=NAVY, bold=True, space_before=14, space_after=6):
    p.paragraph_format.space_before = Pt(space_before)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing = 1.2
    p.paragraph_format.keep_with_next = True
    for r in p.runs:
        r.font.name = BODY_FONT
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = color
    return p


def h1(text):
    p = doc.add_paragraph()
    p.add_run(text)
    return _fmt_heading(p, 15, space_before=18)


def h2(text):
    p = doc.add_paragraph()
    p.add_run(text)
    return _fmt_heading(p, 12.5)


def h3(text):
    p = doc.add_paragraph()
    p.add_run(text)
    return _fmt_heading(p, 11.5, color=GREY, space_before=10, space_after=4)


def para(text, italic=False, size=11, align=None, space_after=6, bold=False):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.font.name = BODY_FONT
    r.font.size = Pt(size)
    r.italic = italic
    r.bold = bold
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(space_after)
    if align:
        p.alignment = align
    return p


def rich(parts, size=11, space_after=6, align=None):
    """parts = list of (text, bold, italic)"""
    p = doc.add_paragraph()
    for text, b, i in parts:
        r = p.add_run(text)
        r.font.name = BODY_FONT
        r.font.size = Pt(size)
        r.bold = b
        r.italic = i
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(space_after)
    if align:
        p.alignment = align
    return p


def bullet(text, level=0, size=11):
    p = doc.add_paragraph(style="List Bullet")
    r = p.add_run(text)
    r.font.name = BODY_FONT
    r.font.size = Pt(size)
    p.paragraph_format.left_indent = Cm(0.8 + 0.7 * level)
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(3)
    return p


def numbered(text, size=11):
    p = doc.add_paragraph(style="List Number")
    r = p.add_run(text)
    r.font.name = BODY_FONT
    r.font.size = Pt(size)
    p.paragraph_format.left_indent = Cm(0.8)
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(3)
    return p


def caption(text):
    p = doc.add_paragraph()
    r = p.add_run(text)
    r.font.name = BODY_FONT
    r.font.size = Pt(9.5)
    r.italic = True
    r.font.color.rgb = GREY
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(12)
    p.paragraph_format.line_spacing = 1.0
    return p


def shade(cell, hexcolor):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hexcolor)
    tcPr.append(shd)


def _set_col_widths(t, widths):
    """Force fixed layout and apply proportional column widths reliably."""
    total = sum(widths)
    cols_emu = [int(CONTENT_W.emu * w / total) for w in widths]

    tbl = t._tbl
    tblPr = tbl.tblPr

    # fixed layout so Word/LibreOffice honour the widths
    for tag in ("w:tblLayout",):
        for el in tblPr.findall(qn(tag)):
            tblPr.remove(el)
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)

    # total table width
    for el in tblPr.findall(qn("w:tblW")):
        tblPr.remove(el)
    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), str(int(CONTENT_W.emu / 635)))  # twips
    tblW.set(qn("w:type"), "dxa")
    tblPr.append(tblW)

    # grid definition
    for grid in tbl.findall(qn("w:tblGrid")):
        tbl.remove(grid)
    tblGrid = OxmlElement("w:tblGrid")
    for w_emu in cols_emu:
        gc = OxmlElement("w:gridCol")
        gc.set(qn("w:w"), str(int(w_emu / 635)))
        tblGrid.append(gc)
    tbl.insert(list(tbl).index(tblPr) + 1, tblGrid)

    for row in t.rows:
        for ci, cell in enumerate(row.cells):
            if ci < len(cols_emu):
                cell.width = Emu(cols_emu[ci])


def make_table(headers, rows, widths=None, font=8.5, header_fill="1F3864"):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False

    hdr = t.rows[0].cells
    for i, htxt in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        r = p.add_run(htxt)
        r.font.name = BODY_FONT
        r.font.size = Pt(font)
        r.font.bold = True
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        p.paragraph_format.line_spacing = 1.0
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.space_before = Pt(2)
        shade(hdr[i], header_fill)

    # repeat header row on page breaks
    trPr = t.rows[0]._tr.get_or_add_trPr()
    tblHeader = OxmlElement("w:tblHeader")
    tblHeader.set(qn("w:val"), "true")
    trPr.append(tblHeader)

    for ri, row in enumerate(rows):
        cells = t.add_row().cells
        for ci, val in enumerate(row):
            cells[ci].text = ""
            p = cells[ci].paragraphs[0]
            # bold the first column when it looks like an ID
            txt = str(val)
            r = p.add_run(txt)
            r.font.name = BODY_FONT
            r.font.size = Pt(font)
            if ci == 0 and (txt[:3] in ("FR-", "NF", "ET", "TC-", "UC0", "UC1", "UC2") or txt.isupper()):
                r.font.bold = True
            p.paragraph_format.line_spacing = 1.0
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.space_before = Pt(2)
            if ri % 2 == 1:
                shade(cells[ci], "F2F5FA")

    if widths:
        _set_col_widths(t, widths)
    doc.add_paragraph().paragraph_format.space_after = Pt(4)
    return t


def add_image(path, caption_text, max_h_cm=19.5):
    if not os.path.exists(path):
        para("[Diagram not found: %s]" % os.path.basename(path), italic=True)
        return
    with Image.open(path) as im:
        w, h = im.size
    max_w_cm = 14.6
    scale = min(max_w_cm / (w / 96 * 2.54), max_h_cm / (h / 96 * 2.54))
    disp_w = (w / 96 * 2.54) * scale
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(0)
    p.add_run().add_picture(path, width=Cm(disp_w))
    caption(caption_text)


def page_break():
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)


def _orient(section, landscape):
    """Set a section to landscape or portrait A4 with appropriate margins."""
    if landscape:
        section.page_width = Cm(29.7)
        section.page_height = Cm(21.0)
        section.orientation = 1  # WD_ORIENT.LANDSCAPE
        section.top_margin = Cm(1.6)
        section.bottom_margin = Cm(1.6)
        section.left_margin = Cm(1.8)
        section.right_margin = Cm(1.8)
    else:
        section.page_width = Cm(21.0)
        section.page_height = Cm(29.7)
        section.orientation = 0
        section.top_margin = Cm(2.54)
        section.bottom_margin = Cm(2.54)
        section.left_margin = Cm(3.17)
        section.right_margin = Cm(3.17)
    sectPr = section._sectPr
    pgSz = sectPr.find(qn("w:pgSz"))
    if pgSz is not None:
        pgSz.set(qn("w:orient"), "landscape" if landscape else "portrait")
        pgSz.set(qn("w:w"), str(int(section.page_width.twips)))
        pgSz.set(qn("w:h"), str(int(section.page_height.twips)))


def landscape_figure(path, caption_text):
    """Place one diagram alone on a landscape page, then return to portrait."""
    s = doc.add_section(WD_SECTION.NEW_PAGE)
    _orient(s, True)
    if os.path.exists(path):
        with Image.open(path) as im:
            w, h = im.size
        avail_w, avail_h = 25.4, 15.4   # cm inside landscape margins (minus caption)
        w_cm, h_cm = w / 96 * 2.54, h / 96 * 2.54
        scale = min(avail_w / w_cm, avail_h / h_cm)
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.add_run().add_picture(path, width=Cm(w_cm * scale))
    else:
        para("[Diagram not found: %s]" % os.path.basename(path), italic=True)
    caption(caption_text)
    s2 = doc.add_section(WD_SECTION.NEW_PAGE)
    _orient(s2, False)


def footer_pagenum():
    for s in doc.sections:
        s.footer.is_linked_to_previous = False
        f = s.footer.paragraphs[0]
        # clear anything already present so the footer is never duplicated
        for child in list(f._p):
            if child.tag != qn("w:pPr"):
                f._p.remove(child)
        f.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = f.add_run("Ally Mongala  |  219361629  |  ISJ107V Assignment 2  |  Page ")
        r.font.size = Pt(8.5)
        r.font.name = BODY_FONT
        r.font.color.rgb = GREY
        fld = OxmlElement("w:fldSimple")
        fld.set(qn("w:instr"), "PAGE")
        f._p.append(fld)


# ================================================================ COVER PAGE
doc.add_paragraph().paragraph_format.space_after = Pt(40)

p = doc.add_paragraph(); r = p.add_run("TSHWANE UNIVERSITY OF TECHNOLOGY")
r.font.size = Pt(13); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(2)

p = doc.add_paragraph(); r = p.add_run("Faculty of Information and Communication Technology")
r.font.size = Pt(11); r.font.color.rgb = GREY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(36)

p = doc.add_paragraph(); r = p.add_run("ISJ107V - INTEGRATED SOFTWARE PROJECT")
r.font.size = Pt(16); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(6)

p = doc.add_paragraph(); r = p.add_run("Assignment 2")
r.font.size = Pt(20); r.bold = True; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(4)

p = doc.add_paragraph(); r = p.add_run("Formal System Proposal and Specification")
r.font.size = Pt(14); r.italic = True; r.font.color.rgb = GREY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(30)

p = doc.add_paragraph(); r = p.add_run("LegalCase ERP")
r.font.size = Pt(17); r.bold = True; r.font.color.rgb = NAVY; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(2)

p = doc.add_paragraph(); r = p.add_run("An AI-Assisted Legal Case Management ERP for Small Law Firms")
r.font.size = Pt(11.5); r.italic = True; r.font.name = BODY_FONT
p.alignment = WD_ALIGN_PARAGRAPH.CENTER; p.paragraph_format.space_after = Pt(40)

info = doc.add_table(rows=0, cols=2)
info.alignment = WD_TABLE_ALIGNMENT.CENTER
for k, v in [("Student name", "Ally Mongala"),
             ("Student number", "219361629"),
             ("Module code", "ISJ107V"),
             ("Assessment", "Assignment 2 (Individual)"),
             ("Weighting", "10% of the final module mark"),
             ("Total marks", "50"),
             ("Due date", "19 September 2026"),
             ("Date submitted", "16 September 2026"),
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

# ============================================== DECLARATION OF ORIGINALITY
h1("Declaration of Originality")
para("I, Ally Mongala (student number 219361629), declare that:")
bullet("This assignment is my own original work and has been produced by me for the ISJ107V Integrated Software Project module.")
bullet("Where the work of other authors has been used, it has been acknowledged and referenced in accordance with the Harvard referencing convention.")
bullet("I have not allowed, and will not allow, any person to copy my work with the intention of presenting it as their own.")
bullet("I understand that plagiarism is a serious academic offence and that the University may take disciplinary action against me should I be found guilty of it.")
bullet("The software artefacts, diagrams and test results presented in this document were produced from my own working prototype of the LegalCase ERP system.")
doc.add_paragraph().paragraph_format.space_after = Pt(18)

sig = doc.add_table(rows=0, cols=2)
for k, v in [("Signature", "A. Mongala"), ("Student number", "219361629"), ("Date", "16 September 2026")]:
    cells = sig.add_row().cells
    cells[0].width = Cm(4.5); cells[1].width = Cm(9.0)
    cells[0].text = ""; cells[1].text = ""
    r1 = cells[0].paragraphs[0].add_run(k); r1.bold = True; r1.font.size = Pt(11); r1.font.name = BODY_FONT
    r2 = cells[1].paragraphs[0].add_run(v); r2.font.size = Pt(11); r2.font.name = BODY_FONT
    for c in cells:
        c.paragraphs[0].paragraph_format.space_after = Pt(6)

page_break()

# ============================================================ TABLE OF CONTENTS
h1("Table of Contents")
toc = [
    ("Question 1  Project Introduction and Justification  [8]", ""),
    ("    1.1  Project title, description, intended users and emerging technology  (2)", ""),
    ("    1.2  Problem statement  (3)", ""),
    ("    1.3  Aim, objectives, scope boundaries and success criteria  (3)", ""),
    ("Question 2  System Requirements  [15]", ""),
    ("    2.1  Ten prioritised functional requirements with acceptance criteria  (10)", ""),
    ("    2.2  Four measurable non-functional requirements (FURPS+)  (2)", ""),
    ("    2.3  Three testable emerging-technology requirements  (3)", ""),
    ("Question 3  Technical Environment  [10]", ""),
    ("    3.1  Proposed technology stack and justification  (3)", ""),
    ("    3.2  Minimum and recommended hardware requirements  (4)", ""),
    ("    3.3  Architecture, deployment, scalability, security, backup and recovery  (3)", ""),
    ("Question 4  UML Models  [13]", ""),
    ("    4.1  Use-case diagram  (4)", ""),
    ("    4.2  Activity diagram  (3)", ""),
    ("    4.3  Class diagram  (3)", ""),
    ("    4.4  Sequence diagram  (3)", ""),
    ("Question 5  Implementation and Evaluation  [13]", ""),
    ("    5.1  Database, API, entities, validation and privacy  (2)", ""),
    ("    5.2  Emerging-technology integration and interface contract  (2)", ""),
    ("    5.3  Testing and evaluation plan  (2)", ""),
    ("Reference List", ""),
    ("Appendix A  Requirements Traceability Matrix", ""),
]
for line, _ in toc:
    p = doc.add_paragraph()
    r = p.add_run(line)
    r.font.size = Pt(11); r.font.name = BODY_FONT
    if not line.startswith("    "):
        r.bold = True
        r.font.color.rgb = NAVY
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(2)

h2("List of Figures")
for f in ["Figure 1: Use-case diagram - LegalCase ERP",
          "Figure 2: Activity diagram - document upload to client notification",
          "Figure 3: Class diagram - LegalCase ERP domain model",
          "Figure 4: Sequence diagram - UC03 upload legal document"]:
    p = doc.add_paragraph(); r = p.add_run(f)
    r.font.size = Pt(10.5); r.font.name = BODY_FONT
    p.paragraph_format.line_spacing = 1.3
    p.paragraph_format.space_after = Pt(2)

page_break()

# ================================================================ QUESTION 1
h1("Question 1: Project Introduction and Justification  [8 marks]")

h2("1.1  Project title, system description, intended users and emerging technology  (2)")

rich([("Project title: ", True, False),
      ("LegalCase ERP - An AI-Assisted Legal Case Management Enterprise Resource Planning System for Small Law Firms.", False, False)])

h3("System description")
para("LegalCase ERP is a multi-user, web-based enterprise resource planning system that consolidates the "
     "case-management operations of a small law firm into a single secure platform. The system maintains a "
     "central legal database of matters, parties, documents, deadlines and correspondence. When a practitioner "
     "uploads a court document, pleading or affidavit, an artificial-intelligence extraction pipeline reads the "
     "document automatically, identifies the legally significant entities within it - hearing dates, plaintiff, "
     "defendant, instructing attorneys, presiding judge, case number, court and claim amount - and produces a "
     "structured, three-paragraph plain-language summary organised as Facts, Legal Issues and Current Status.")
para("Critically, the same pipeline validates the uploaded document against the matter it was filed under. Where "
     "the document relates to a different matter, or contains no recognisable case content at all, the system "
     "blocks it, records a rejection reason and highlights the conflicting text so that the practitioner can "
     "correct the filing before the error propagates into the client record. Extracted dates are converted into "
     "deadline records that are synchronised to the practitioner's Google Calendar or exported as an iCalendar "
     "(.ics) feed that can be subscribed to from any phone or desktop calendar application. A secure client portal "
     "exposes approved summaries, shared documents, message threads and upcoming court dates to the client, and an "
     "analytics dashboard reports caseload, deadline pressure and processing throughput to the firm.")

h3("Intended users")
make_table(
    ["User group", "Role in the system", "Primary needs addressed"],
    [["Attorney / Legal practitioner (Lawyer)",
      "Primary actor. Creates and maintains matters, uploads documents, reviews and approves AI output, manages deadlines and corresponds with clients.",
      "Reduced document-review time, no missed court dates, a single view of the matter."],
     ["Client of the firm",
      "Primary actor. Accesses a read-only portal scoped strictly to their own matters.",
      "Transparency on case progress without telephoning the firm; plain-language summaries; secure document exchange."],
     ["System administrator / Practice manager",
      "Primary actor. Manages user accounts and access revocation, inspects the tamper-evident audit trail, exports compliance reports and approves model-retraining runs.",
      "Regulatory accountability, information-security governance and oversight of AI quality."],
     ["Paralegal / Legal secretary (secondary)",
      "Operates under the practitioner role with restricted scopes; performs bulk uploads and diary capture.",
      "Efficient administrative capture without access to privileged financial data."]],
    widths=[26, 40, 34])

h3("Emerging technology to be integrated")
para("The emerging technology integrated into this project is artificial intelligence, specifically natural "
     "language processing (NLP) applied to unstructured legal text. The technology performs three distinct "
     "functions inside the workflow: named-entity and date extraction from legal documents; semantic validation of "
     "a document against the matter it was filed under; and abstractive-extractive summarisation that condenses a "
     "lengthy pleading into a three-paragraph brief. Every field the model returns is accompanied by a confidence "
     "score, the exact text span that produced it and the source page number, which makes the model's output "
     "explainable and auditable rather than a black box. This is a deliberate design decision: in a regulated legal "
     "environment a practitioner must be able to justify why a date was diarised, and the Legal Practice Council "
     "expects a human professional to remain accountable for the advice given to a client.")

page_break()

h2("1.2  Problem statement  (3)")

h3("The current problem")
para("Small law firms in South Africa - typically sole practitioners and practices of two to ten attorneys - "
     "manage their matters using a fragmented combination of e-mail threads, shared network folders, spreadsheet "
     "diaries and physical files. There is no single authoritative record of a matter. Court documents arrive as "
     "scanned or digital PDFs and must be read manually by a practitioner who then re-keys the critical dates into "
     "a personal calendar and files the document by hand. Because the diary, the document store and the client "
     "correspondence live in three separate systems that are not linked, the accuracy of the firm's deadline diary "
     "depends entirely on the memory and diligence of an individual under time pressure. Enterprise legal practice "
     "management platforms exist, but their licensing and implementation costs are structured for large commercial "
     "firms and are not economically accessible to a small practice.")

h3("Who is affected")
bullet("Attorneys and sole practitioners, who spend billable hours on manual document review, re-keying dates and answering routine client status queries instead of on substantive legal work.")
bullet("Clients of small firms, who are left uninformed about the progress of their matters and must telephone the firm for updates, and who bear the ultimate consequence when a deadline is missed.")
bullet("Practice managers and firm principals, who cannot demonstrate a defensible audit trail of who accessed which client file and when, as required for professional-indemnity and regulatory purposes.")
bullet("The firm as a business entity, whose reputation, professional-indemnity premium and Legal Practice Council standing are exposed by preventable administrative failure.")

h3("Consequences of leaving the problem unresolved")
para("The consequences are professional, financial and regulatory. A missed prescription date or a late filing can "
     "extinguish a client's claim entirely, giving rise to a professional-negligence action against the attorney "
     "and a claim against the Legal Practitioners' Fidelity Fund or the firm's professional-indemnity cover. Manual "
     "document review consumes chargeable capacity that a small firm cannot afford to lose, which directly "
     "suppresses the firm's revenue per practitioner. Poor client communication drives attrition and negative "
     "referral, which is the principal source of new work for a small practice. Finally, the absence of a "
     "tamper-evident record of access to client files places the firm in a weak position under the Protection of "
     "Personal Information Act 4 of 2013 (POPIA), which requires a responsible party to secure the integrity and "
     "confidentiality of personal information and to be able to demonstrate that it has done so. Left unresolved, "
     "the problem compounds: as caseload grows, the manual system degrades faster than the firm can hire "
     "administrative capacity to support it.")

h2("1.3  Aim, objectives, scope boundaries and success criteria  (3)")

h3("Project aim")
rich([("Aim: ", True, False),
      ("To design, implement and evaluate an affordable, AI-assisted legal case management ERP system that "
       "centralises matter information, automates the extraction and diarising of legally significant dates from "
       "uploaded documents, and gives clients transparent self-service access to the progress of their matters, "
       "thereby improving the operational efficiency of a small law firm by 25% by December 2026.", False, False)])

h3("Measurable objectives")
make_table(
    ["No.", "Measurable objective", "Measure and target date"],
    [["OBJ-01",
      "Implement an NLP extraction pipeline that identifies dates, parties, attorneys, judge, case number and court from uploaded legal documents.",
      "Achieve at least 90% field-level precision and recall on a held-out annotated test set of 50 legal documents by 30 November 2026."],
     ["OBJ-02",
      "Automate deadline capture and calendar synchronisation so that every extracted date becomes a diarised, reminder-enabled calendar entry.",
      "Reduce missed or late-diarised deadlines by 40% against a four-week manual baseline measured during the pilot, by 15 December 2026."],
     ["OBJ-03",
      "Deliver a secure role-scoped client portal that publishes approved summaries, shared documents and upcoming court dates.",
      "Achieve a mean client-satisfaction score of at least 4.0 out of 5.0 from at least 20 portal users, and a 25% reduction in inbound status telephone queries, by 15 December 2026."]],
    widths=[10, 45, 45])

h3("Scope boundaries")
rich([("In scope: ", True, False),
      ("matter and party records; document upload, encryption and storage; AI extraction, validation and "
       "summarisation of machine-readable PDF and DOCX documents; deadline management with Google Calendar and "
       ".ics synchronisation; lawyer-client secure messaging; a read-only client portal; client ratings; "
       "role-based access control with account revocation; a tamper-evident audit trail; administrative reporting "
       "and model-retraining governance; and an analytics dashboard.", False, False)])
rich([("Out of scope: ", True, False),
      ("trust-account and business accounting, billing and invoicing (the system exposes an integration point but "
       "does not implement accounting); e-filing submission directly to court registries; optical character "
       "recognition of scanned image-only documents, which is identified as a known limitation and roadmap item; "
       "the provision of legal advice - the system summarises and organises, it does not advise; native iOS and "
       "Android applications, as mobile access is delivered through a responsive browser interface; and "
       "multi-firm tenancy, as each deployment serves a single practice.", False, False)])

h3("Success criteria")
make_table(
    ["No.", "Success criterion", "How it will be verified"],
    [["SC-01",
      "The end-to-end workflow operates without manual intervention: a practitioner uploads a document and, within 10 seconds, the system has extracted the fields, validated the document against the matter, produced a three-paragraph summary and created the corresponding deadline records.",
      "Demonstrated in the automated end-to-end test suite and in the live project demonstration, with timings recorded."],
     ["SC-02",
      "Across a controlled test set of documents that includes correctly filed documents, documents belonging to a different matter and documents with no case content, the system correctly accepts every valid document and blocks every invalid one, with a stated rejection reason.",
      "The published test-case suite must report 8 of 8 scenarios passed with zero false rejections of valid documents."]],
    widths=[10, 52, 38])

page_break()

# ================================================================ QUESTION 2
h1("Question 2: System Requirements  [15 marks]")

h2("2.1  Ten prioritised functional requirements with acceptance criteria  (10)")
para("Requirements are prioritised using the MoSCoW convention (Must, Should, Could). Each requirement is stated "
     "in the form \"The system shall ...\" and is accompanied by a testable acceptance criterion expressed in "
     "Given-When-Then form. These identifiers are used consistently throughout this document and are traced to "
     "design elements in Appendix A.")

make_table(
    ["ID", "Priority", "Requirement", "Acceptance criterion"],
    [["FR-01", "Must",
      "The system shall allow an authenticated legal practitioner to upload a PDF or DOCX document of up to 25 MB and store it against a selected case record in encrypted form.",
      "Given a practitioner authenticated with the write:case scope and an existing case, when a valid PDF or DOCX file is submitted to POST /api/cases/{id}/documents, then the system returns HTTP 202 with a document identifier, the ciphertext is written to storage, and the document appears in the case document list with status PROCESSING."],
     ["FR-02", "Must",
      "The system shall automatically extract the hearing and filing dates, plaintiff, defendant, instructing attorneys, presiding judge, case number, court and claim amount from an uploaded document, recording a confidence score, source text span and page number for every extracted field.",
      "Given a machine-readable document containing these entities, when processing completes, then GET /api/documents/{id}/extractions returns each field with a non-null value, a confidence between 0.0 and 1.0, the verbatim span of source text and the page number on which it was found."],
     ["FR-03", "Must",
      "The system shall generate a three-paragraph plain-language summary of each successfully processed document, structured as Facts, Legal Issues and Current Status.",
      "Given a document with status PROCESSED, when the practitioner opens the summary view, then exactly three non-empty paragraphs are returned under the headings Facts, Legal Issues and Current Status, with a version number and a status of DRAFT."],
     ["FR-04", "Must",
      "The system shall validate every uploaded document against the case it was filed under and shall block the document, assigning status REJECTED with a stated reason, where the document relates to a different matter or contains no recognisable case content.",
      "Given a document whose extracted parties and case number do not correspond to the target case, when validation runs, then the document status is set to REJECTED, a human-readable rejection reason is stored and displayed, the conflicting text spans are highlighted for review, and the rejection is written to the administrator rejection report."],
     ["FR-05", "Must",
      "The system shall create deadline records from extracted and manually captured dates and shall synchronise them to the practitioner's Google Calendar, or export them as an iCalendar (.ics) feed where no Google account is linked.",
      "Given a case with at least one deadline, when the practitioner invokes calendar synchronisation, then either a Google Calendar event is created and its external event identifier is stored against the deadline, or GET /api/cases/{id}/calendar.ics returns a valid RFC 5545 iCalendar file containing a VEVENT with the correct date and a VALARM reminder."],
     ["FR-06", "Must",
      "The system shall require a legal practitioner to review and explicitly approve an AI-generated summary before it becomes visible to the client.",
      "Given a summary with status DRAFT, when a client requests the case view, then the summary is not returned; and when the practitioner submits POST /api/summaries/{id}/approve, then the status changes to APPROVED, the approving user and timestamp are recorded, and the summary becomes visible in the client portal."],
     ["FR-07", "Must",
      "The system shall provide each client with a secure portal, scoped by role, that displays only the approved summaries, shared documents, message threads and upcoming deadlines belonging to that client's own matters.",
      "Given a client authenticated with the read:client scope, when the client requests a case that belongs to another client, then the system returns HTTP 403 and writes an authorisation-failure entry to the audit log; and when the client requests their own case, then only approved summaries and documents explicitly shared with the client are returned."],
     ["FR-08", "Must",
      "The system shall provide a bidirectional secure messaging thread between practitioner and client at case level, with unread-message notification delivered to the recipient in real time.",
      "Given an open session for the recipient, when a message is posted to POST /api/cases/{id}/messages, then a server-sent event of type new-message is delivered to the recipient in under one second, the unread counter increments, and the counter returns to zero once the recipient opens the thread."],
     ["FR-09", "Must",
      "The system shall maintain a tamper-evident audit log in which every recorded action stores a cryptographic hash of its own content chained to the hash of the preceding entry, and shall allow an administrator to export the audit trail and compliance reports as CSV.",
      "Given a sequence of recorded actions, when the chain-verification routine is executed, then it reports the chain as intact; and when any historical row is altered directly in the database, then verification identifies the first divergent entry. GET /api/admin/reports/audit.csv returns a CSV containing timestamp, user, action, detail and hash columns."],
     ["FR-10", "Should",
      "The system shall allow an administrator to manage user accounts, revoke and restore access, review rejected documents and approve model-retraining runs against uploaded training datasets.",
      "Given an active user, when the administrator invokes POST /api/admin/users/{id}/revoke, then the account is deactivated, any subsequent authentication attempt fails with the message \"Account disabled - access revoked by an administrator\", and the revocation is recorded in the audit log; and when a retraining run is approved, its status changes to APPROVED and the approving administrator is recorded."],
     ["FR-11", "Should",
      "The system shall present an analytics dashboard reporting active caseload, deadlines falling due within 30 days, documents processed and cases by status, updating in real time as underlying data changes.",
      "Given an open dashboard, when a new deadline is created by another session, then the deadlines-in-30-days indicator and the deadlines-per-month chart update without a page reload following receipt of a deadline-updated server-sent event."],
     ["FR-12", "Could",
      "The system shall allow a practitioner to re-run AI processing on a document whose processing previously failed, and shall allow a client to submit a service rating of 1 to 5 with an optional comment for a matter in which they are a party.",
      "Given a document with status FAILED, when the practitioner invokes POST /api/documents/{id}/retry, then stale extractions and summaries are discarded and the document re-enters the PROCESSING state; and given a client party to a case, when a rating between 1 and 5 is submitted, then it is persisted and reflected in the practitioner's average rating."]],
    widths=[8, 10, 36, 46], font=8)

para("Note: twelve requirements are specified in order to cover the full functional surface of the implemented "
     "system. FR-01 to FR-10 constitute the ten prioritised requirements required by the question; FR-11 and "
     "FR-12 are supplementary requirements of lower priority included for completeness.", italic=True, size=9.5)

page_break()

h2("2.2  Four measurable non-functional requirements (FURPS+)  (2)")

make_table(
    ["ID", "FURPS+ category", "Priority", "Requirement", "Acceptance criterion"],
    [["NFR-01", "Performance", "Must",
      "The system shall return any case, document-metadata or deadline query within 2 seconds, and shall complete full AI processing of a 20-page machine-readable document within 10 seconds, when measured at the 95th percentile under a load of 25 concurrent users.",
      "Measured 95th-percentile response time for metadata endpoints is 2 seconds or less, and measured 95th-percentile end-to-end document processing time for a 20-page document is 10 seconds or less, under a 25-user load test."],
     ["NFR-02", "Reliability", "Must",
      "The system shall achieve at least 99.5% monthly availability of its core services and shall lose no committed transaction on failure, with automated database backups taken every 24 hours and a recovery point objective of 24 hours and a recovery time objective of 4 hours.",
      "Measured uptime over a calendar month is 99.5% or higher (a maximum of 3 hours 39 minutes of unplanned downtime). A documented restore rehearsal from the most recent backup completes successfully within 4 hours with no committed data lost."],
     ["NFR-03", "Functionality (Security)", "Must",
      "The system shall encrypt all documents at rest using AES-256-GCM and all data in transit using TLS 1.2 or higher, shall store user passwords only as PBKDF2 salted hashes, and shall enforce role-based authorisation on every API endpoint using signed JSON Web Tokens with explicit scopes.",
      "A penetration test and static configuration review confirm that no endpoint returns data without a valid token and the required scope; stored document files are unreadable without the encryption key; and no plaintext or reversibly encoded password exists in the database."],
     ["NFR-04", "Usability", "Must",
      "The system shall enable a practitioner who has received a single 15-minute orientation to complete the upload-review-approve-synchronise workflow unaided in under 3 minutes, and shall present all primary screens legibly on a 1366x768 display and on a mobile browser viewport of 360 CSS pixels.",
      "At least 8 of 10 participants in usability testing complete the workflow unaided within 3 minutes, and the System Usability Scale score averages 75 or higher. Responsive layout is verified at 360px, 768px and 1366px viewport widths."],
     ["NFR-05", "Supportability", "Should",
      "The system shall be deployable to a clean host through a single container-orchestration command, shall externalise all environment-specific settings as environment variables, and shall emit structured logs for every request and AI processing job.",
      "A clean host executes docker compose up --build and reaches a healthy state with no manual configuration step other than the provision of environment variables; the health endpoint returns HTTP 200; and every request and processing job appears in the structured log with a correlation identifier."],
     ["NFR-06", "Plus (Regulatory constraint)", "Must",
      "The system shall comply with the Protection of Personal Information Act 4 of 2013, requiring explicit recorded client consent before a document is processed, retention of personal information within South African data-residency boundaries, and the ability to export or erase all personal information held for a data subject on request.",
      "Every document record carries a consent flag that must be true before processing may begin; the deployment configuration pins storage and compute to a South African region; and a data-subject export or erasure request is fulfilled completely within 30 days and recorded in the audit log."]],
    widths=[8, 14, 9, 34, 35], font=8)

para("Note: NFR-01 to NFR-04 are the four measurable non-functional requirements required by the question, "
     "covering the performance, reliability, functionality (security) and usability categories of FURPS+. "
     "NFR-05 and NFR-06 are included to address the supportability and constraint ('plus') categories.",
     italic=True, size=9.5)

h2("2.3  Three testable emerging-technology requirements  (3)")
para("The following requirements apply directly to the AI/NLP component and are stated so that each can be "
     "objectively measured against a held-out, human-annotated evaluation set rather than assessed subjectively.")

make_table(
    ["ID", "Attribute", "Priority", "Requirement", "Test method and acceptance criterion"],
    [["ETR-01", "Model accuracy", "Must",
      "The AI extraction component shall correctly identify dates, plaintiff, defendant and case number from machine-readable legal documents with a field-level precision of at least 90% and a recall of at least 85%, measured against a human-annotated held-out evaluation set of at least 50 documents.",
      "An automated evaluation harness compares model output against the gold-standard annotation set and computes per-field precision, recall and F1. The requirement is met when the macro-averaged precision across the four fields is 0.90 or higher and recall is 0.85 or higher. Results are recorded per model version."],
     ["ETR-02", "Inference latency", "Must",
      "The AI extraction and summarisation component shall return a complete result - extracted fields, validation verdict and three-paragraph summary - within 500 milliseconds per page of document text, measured at the 95th percentile on the target production hardware.",
      "A load harness submits 200 documents of known page count and records wall-clock processing time per page. The requirement is met when the 95th-percentile per-page processing time is 500 milliseconds or less on a host with 4 vCPU and 8 GB RAM."],
     ["ETR-03", "Validation integrity and explainability", "Must",
      "The AI validation component shall correctly block documents that belong to a different matter or that contain no recognisable case content, with a false-rejection rate of no more than 5% on correctly filed documents, and shall return the exact source text span and page number that justifies every extracted field and every rejection decision.",
      "The published controlled test suite of correctly filed, wrong-matter and non-legal documents is executed automatically; the requirement is met when every invalid document is blocked with a stated reason, no valid document is rejected, and every extraction and rejection carries a non-empty span and a valid page number. The current suite reports 8 of 8 scenarios passed."],
     ["ETR-04", "Human-in-the-loop governance", "Should",
      "The AI component shall record every practitioner correction of an extracted field or summary as a labelled training example, and no retrained model version shall be promoted to production without explicit administrator approval and the ability to roll back to the previous version.",
      "A correction submitted through the user interface appears as a labelled record in the retraining dataset; a training run remains in status PENDING until approved by an administrator; and a documented rollback to the previous model version is demonstrated successfully."]],
    widths=[8, 13, 9, 35, 35], font=8)

para("Note: ETR-01 to ETR-03 are the three testable emerging-technology requirements required by the question. "
     "ETR-04 is included because responsible-AI governance is a material design concern in a regulated legal "
     "environment.", italic=True, size=9.5)

page_break()

# ================================================================ QUESTION 3
h1("Question 3: Technical Environment  [10 marks]")

h2("3.1  Proposed technology stack and justification  (3)")
para("The stack below is the stack of the implemented working prototype. It was selected to satisfy the module's "
     "requirement that components be capable of execution on separate machines, while remaining deployable on "
     "hardware that a small law firm can actually afford.")

make_table(
    ["Layer", "Technology selected", "Justification"],
    [["User interface (primary)",
      "Responsive browser client built with HTML5, CSS3 and vanilla JavaScript (ES2022), served as static assets by the application server; charts rendered as inline SVG.",
      "Requires no installation on practitioner or client devices and runs on any modern browser, including mobile. Avoiding a heavy front-end framework keeps the payload small and removes a large transitive dependency surface, which matters for a firm without in-house IT support."],
     ["User interface (secondary)",
      "JavaFX 21 desktop client using FXML and CSS, consuming the same REST API.",
      "Demonstrates multi-platform interoperability: two structurally different clients operate against one API contract, evidencing that the application layer is genuinely decoupled from presentation."],
     ["Application layer",
      "Java 21 (LTS) with the Javalin 6.3 micro-framework exposing a stateless REST API; document processing executed asynchronously on a managed worker thread pool.",
      "Java 21 is a long-term-support release with a mature legal-sector deployment record. Javalin is lightweight and embeds Jetty directly, so the service ships as a single executable JAR with no application server to administer. Asynchronous processing keeps upload requests responsive while AI work proceeds in the background."],
     ["Database",
      "PostgreSQL 16 in production; H2 2.2 in PostgreSQL-compatibility mode for development and automated testing. Connection pooling by HikariCP.",
      "PostgreSQL provides ACID guarantees essential to a legal record, mature full-text search, JSON support for semi-structured extraction output, and point-in-time recovery. H2 running in PostgreSQL compatibility mode allows the identical schema to run in CI without provisioning a database server."],
     ["AI / NLP component",
      "A dedicated extraction and summarisation service exposed behind the AiExtractionService interface. The prototype implements a deterministic rule- and pattern-based engine tuned to South African legal document conventions; the production target is a transformer model fine-tuned on legal text, served behind the same interface.",
      "Defining the component as an interface contract - extract, validateAgainstCase, summarise - makes the system model-agnostic. The deterministic engine is fully explainable, has no inference cost and runs offline, which allows the complete workflow to be demonstrated and tested; substituting a transformer implementation requires no change to any caller."],
     ["Document text extraction",
      "Apache PDFBox 2.0 for PDF and Apache POI 5.2 for DOCX.",
      "Both are mature, permissively licensed Java libraries that expose text on a per-page basis, which is required to attach page numbers to extracted fields for explainability."],
     ["APIs and integration",
      "Internal REST/JSON API secured with JJWT-signed JSON Web Tokens carrying explicit scopes; Server-Sent Events for real-time notification; external integration with the Google Calendar API v3 and RFC 5545 iCalendar (.ics) export.",
      "REST/JSON is universally consumable and keeps the two clients interchangeable. Server-Sent Events provide push notification over ordinary HTTP without the operational complexity of WebSockets. Offering .ics alongside the Google integration means calendar synchronisation still works for a firm that does not use Google Workspace."],
     ["Security",
      "PBKDF2 password hashing with per-user salt; AES-256-GCM authenticated encryption of stored documents; TLS termination at the reverse proxy; role-based scopes (read:client, write:case, admin:model); hash-chained audit log.",
      "Legal documents are privileged. Authenticated encryption protects both confidentiality and integrity, and the hash-chained audit log makes retrospective tampering detectable, which supports both POPIA accountability and professional-indemnity defensibility."],
     ["Development tools",
      "Apache Maven 3.9 multi-module build; IntelliJ IDEA; Git with GitHub; GitHub Actions for continuous integration; PlantUML for UML models; JUnit-style automated smoke, integration and end-to-end suites.",
      "Maven's multi-module structure enforces the shared/backend/frontend separation at build time. GitHub Actions builds every commit, runs the test suites and validates the container build, which evidences the DevOps outcome of the module."],
     ["Deployment platform",
      "Docker containers orchestrated with Docker Compose: an nginx 1.27 reverse proxy, the Java application container and a PostgreSQL 16 container, each able to run on a separate host; target hosting on a South African cloud region.",
      "Containerisation makes the deployment reproducible and allows the three tiers to be distributed across different machines, satisfying the module requirement. Pinning the region addresses POPIA data residency."]],
    widths=[17, 33, 50], font=8)

page_break()

h2("3.2  Minimum and recommended hardware requirements  (4)")

h3("Server-side infrastructure")
make_table(
    ["Component", "Minimum specification", "Recommended specification"],
    [["Application server - processor", "2 vCPU, x86-64, 2.0 GHz", "4 vCPU, x86-64, 2.8 GHz or higher"],
     ["Application server - memory", "4 GB RAM (2 GB JVM heap)", "8 GB RAM (4 GB JVM heap)"],
     ["Application server - storage", "40 GB SSD for the operating system, application and container images", "100 GB NVMe SSD"],
     ["Database server - processor", "2 vCPU", "4 vCPU"],
     ["Database server - memory", "4 GB RAM", "8 GB RAM"],
     ["Database server - storage", "100 GB SSD with daily snapshot", "500 GB NVMe SSD with daily snapshot and point-in-time recovery, plus offsite replica"],
     ["Document storage", "100 GB encrypted object or block storage (approximately 20 000 documents at 5 MB)", "1 TB encrypted object storage with versioning and lifecycle archiving"],
     ["Network - server", "10 Mbps symmetric, static public IP, TLS certificate", "100 Mbps symmetric with redundant uplink and CDN for static assets"],
     ["Backup target", "Daily off-host backup with 30-day retention", "Hourly incremental plus daily full backup, 12-month retention, geographically separate location"]],
    widths=[26, 37, 37], font=8.5)

h3("Client devices")
make_table(
    ["Client type", "Minimum specification", "Recommended specification"],
    [["Practitioner workstation (browser client)", "Dual-core 2.0 GHz, 4 GB RAM, 1366x768 display, Chrome / Edge / Firefox current version minus one, 5 Mbps connection", "Quad-core 2.5 GHz, 8 GB RAM, 1920x1080 display, 20 Mbps connection"],
     ["Practitioner workstation (JavaFX desktop client)", "Dual-core 2.0 GHz, 4 GB RAM, Java 21 runtime with JavaFX, Windows 10 / macOS 12 / Ubuntu 22.04", "Quad-core 2.5 GHz, 8 GB RAM, dedicated or integrated GPU with OpenGL 3.2 support"],
     ["Client portal user (mobile)", "Android 10 or iOS 15, 360 CSS-pixel viewport, 3 Mbps mobile data", "Android 13 or iOS 17 on a 5 Mbps or faster connection"],
     ["Administrator workstation", "As practitioner workstation, with access to the administration console over TLS", "As practitioner workstation, with hardware-backed multi-factor authentication token"]],
    widths=[22, 41, 37], font=8.5)

h3("Specialised hardware required by the emerging technology")
make_table(
    ["Requirement", "Minimum", "Recommended", "Rationale"],
    [["AI inference compute", "CPU-only inference on 4 vCPU with AVX2 instruction support; 8 GB RAM", "8 vCPU with AVX-512, or an NVIDIA GPU with 8 GB VRAM (for example T4 or A10G) with CUDA 12", "The deterministic prototype engine runs comfortably on CPU. A fine-tuned transformer model meets the ETR-02 latency target on CPU only for short documents; GPU acceleration is required to hold the 500 ms per-page target under concurrent load."],
     ["Model training compute", "Not required in production; training performed off-platform", "NVIDIA GPU with 16 GB VRAM or greater, or a managed cloud training instance, with 64 GB system RAM", "Fine-tuning a transformer on an annotated legal corpus is memory-bound and impractical without GPU acceleration."],
     ["Model and dataset storage", "20 GB for model weights and versioned artefacts", "100 GB with model-version retention for rollback under ETR-04", "Multiple model versions must be retained concurrently so that a promoted model can be rolled back."],
     ["Document scanner (optional, firm-side)", "Not required for machine-readable documents", "300 dpi duplex document scanner with an OCR-capable driver", "Required only when the firm intends to ingest paper originals; OCR of image-only documents is a roadmap item and is presently out of scope."]],
    widths=[20, 24, 26, 30], font=8)

h2("3.3  Architecture, deployment, scalability, security, backup and recovery  (3)")

h3("Architectural style and component communication")
para("The system implements a three-tier, service-oriented architecture with an asynchronous worker pattern for "
     "document processing. The presentation tier consists of the responsive browser client and the JavaFX desktop "
     "client. Neither client holds business logic; both communicate exclusively with the application tier over "
     "HTTPS using the same stateless REST/JSON contract, authenticated by a signed JSON Web Token carried in the "
     "Authorization header and validated on every request. Because the API is stateless, no session affinity is "
     "required and instances can be added or removed freely.")
para("The application tier is the Javalin service. It decomposes into controllers, which handle transport and "
     "authorisation concerns only, and services, which hold the business logic. When a document is uploaded the "
     "controller persists the encrypted file, creates the document record and immediately returns HTTP 202 "
     "Accepted, handing the work to the AI pipeline's worker pool. This separation means a slow or failing model "
     "never blocks the user interface. The AI component is reached only through the AiExtractionService interface, "
     "so it can execute in-process, as it does in the prototype, or be relocated to a separate inference host "
     "behind an HTTP call without any change to the calling services. The data tier is PostgreSQL, accessed "
     "through a HikariCP connection pool; encrypted document payloads are held on the storage volume with only "
     "their metadata and storage path in the database. Real-time notification is delivered from the application "
     "tier to connected clients over Server-Sent Events, and outbound integration with the Google Calendar API is "
     "isolated behind the CalendarGateway interface, which also has a local implementation that emits RFC 5545 "
     "iCalendar files.")

h3("Deployment approach")
para("Deployment is containerised and orchestrated with Docker Compose across three services that may be placed on "
     "three separate machines: an nginx reverse proxy terminating TLS and serving static assets, with "
     "response buffering disabled on the event stream so that Server-Sent Events are delivered without delay; the "
     "Java application container built from a multi-stage Dockerfile that compiles with Maven and runs on an "
     "Eclipse Temurin 21 JRE base image; and the PostgreSQL 16 container with a persistent named volume. All "
     "environment-specific configuration - database URL, credentials, encryption key and JWT signing secret - is "
     "injected as environment variables and never committed to source control. A GitHub Actions workflow builds "
     "every commit, executes the smoke, integration and end-to-end suites, builds the container image and "
     "validates the compose configuration before any change reaches the main branch.")

h3("Scalability")
para("Because the application tier is stateless, horizontal scalability is achieved by running additional "
     "application containers behind the nginx proxy. Document processing is the workload most likely to saturate, "
     "and it is already decoupled onto a worker pool, so the pool size scales with available cores and the "
     "processing stage can be extracted into an independently scaled worker service without redesign. The "
     "database scales vertically first and then through read replicas serving the analytics dashboard and "
     "reporting queries, which are read-heavy and tolerant of brief replication lag. Indexes are defined on every "
     "foreign key used in list queries - documents by case, extractions by document, deadlines by case, messages "
     "by case and audit entries by timestamp - so that query cost grows logarithmically rather than linearly with "
     "caseload. Where a transformer model replaces the prototype engine, inference is scaled separately on "
     "GPU-backed hosts behind the same interface.")

h3("Security")
para("Security is enforced in depth. All traffic is carried over TLS 1.2 or higher. Authentication issues a signed "
     "JSON Web Token carrying the user's role and explicit scopes, and every endpoint declares the scope it "
     "requires, so that a client token can never reach a practitioner or administrator resource. Passwords are "
     "stored only as PBKDF2 hashes with per-user salt. Documents are encrypted at rest with AES-256-GCM, which "
     "provides authenticated encryption and therefore detects tampering as well as preventing disclosure. Every "
     "security-relevant action is written to a hash-chained audit log in which each entry stores a hash of its own "
     "content combined with the hash of its predecessor, so that any retrospective alteration breaks the chain and "
     "is detectable. Administrator revocation takes effect at the next authentication attempt, and uploads are "
     "constrained by MIME type and size to limit the attack surface. Client consent is recorded against each "
     "document before processing may begin, and the deployment region is pinned to satisfy POPIA data residency.")

h3("Backup and recovery")
para("The database is backed up nightly with a 30-day retention period at the minimum specification, and hourly "
     "incrementally with point-in-time recovery and a twelve-month retention at the recommended specification. "
     "Encrypted document storage is backed up on the same schedule with object versioning enabled, so that an "
     "accidental deletion is recoverable independently of the database. Backups are written to a location "
     "geographically separate from the primary host and are themselves encrypted, with encryption keys held in a "
     "managed secret store rather than alongside the data. The recovery point objective is 24 hours at minimum "
     "specification and 1 hour at recommended specification; the recovery time objective is 4 hours. Because all "
     "infrastructure is declared in the Dockerfile and compose file, recovery of the application tier is a matter "
     "of redeploying the containers against a restored data volume. A restore rehearsal is performed quarterly and "
     "the result recorded, since an untested backup is not a backup. The hash-chained audit log is verified after "
     "every restore to confirm that the recovered record is intact.")

page_break()

# ================================================================ QUESTION 4
h1("Question 4: UML Models  [13 marks]")
para("The four models below use the same actor, class, component and requirement names as the rest of this "
     "submission. Source files in PlantUML format accompany each diagram.", italic=True, size=10)

h2("4.1  Use-case diagram  (4)")
para("The diagram shows the LegalCase ERP system boundary containing twenty use cases grouped by functional area. "
     "The primary actors are the Lawyer, the Client and the System Administrator, all of which generalise from the "
     "abstract Firm User actor that holds the shared authentication behaviour. Three supporting actors sit outside "
     "the boundary: the AI Extraction Service, which is the emerging-technology component; the Google Calendar "
     "API; and the Server-Sent Events notification service.")
para("Include relationships capture mandatory sub-behaviour: UC03 Upload Legal Document always includes UC04 "
     "Extract Case Data and UC06 Generate Three-Paragraph AI Summary, UC04 always includes UC05 Validate Document "
     "Against Case, UC09 Synchronise Calendar always includes UC08 Manage Deadlines, and both UC02 and UC12 "
     "include UC01 Authenticate User. Extend relationships capture conditional behaviour: UC20 Block and Report "
     "Rejected Document extends UC05 when validation fails, UC19 Retry Failed Processing extends UC04 when the "
     "document status is FAILED, UC10 Export Calendar extends UC09 when no Google account is linked, and UC11 "
     "Exchange Secure Messages extends UC12 when a new-message notification arrives.")
landscape_figure(os.path.join(UML, "a2-usecase.png"), "Figure 1: Use-case diagram - LegalCase ERP (system boundary, actors and major use cases)")

h2("4.2  Activity diagram  (3)")
para("This diagram models the central end-to-end workflow of the system, from document upload to client "
     "notification, partitioned into swimlanes for the Lawyer interface, the application layer, the AI Extraction "
     "Service, the Google Calendar API and the Client Portal. It begins at the initial node when the practitioner "
     "opens a case and ends at one of four final nodes.")
para("Three decision nodes create the alternative paths. The first rejects an unsupported file type or missing "
     "client consent before any processing occurs. The second, inside the AI swimlane, routes a document with no "
     "machine-readable text to the FAILED state with a retry action offered. The third is the emerging-technology "
     "decision proper: after the model has extracted the fields and cross-validated them against the target case "
     "record, a document that does not match is set to REJECTED, its conflicting spans are highlighted and the "
     "rejection is published to the administrator report, while a matching document proceeds to summarisation. The "
     "human-in-the-loop review loop returns the practitioner to editing until the summary is accurate, with each "
     "correction stored as a retraining example. A final decision node selects Google Calendar synchronisation or "
     "iCalendar export depending on whether an account is linked, after which the client is notified in real time. "
     "The emerging-technology steps - NLP field extraction, the attachment of confidence scores and text spans, "
     "cross-validation against the case, and summary generation - all occur in the shaded AI Extraction Service "
     "partition.")
landscape_figure(os.path.join(UML, "a2-activity.png"), "Figure 2: Activity diagram - document upload to client notification (end-to-end workflow)")

h2("4.3  Class diagram  (3)")
para("The domain model centres on the abstract User class, from which Lawyer, Client and Administrator inherit, "
     "each adding role-specific operations. Case is the aggregate root of the model. It stands in composition with "
     "LegalDocument and Deadline - a document or a deadline has no meaning independently of its matter and is "
     "deleted with it - and in weaker aggregation with ClientMessage. LegalDocument is in turn in composition with "
     "Extraction, since an extracted field cannot outlive the document it was read from, and is associated with "
     "Summary, of which successive approved versions are retained.")
para("Multiplicities record the business rules: a Lawyer manages zero or more Cases, a Client is party to zero or "
     "more Cases, a Case contains zero or more LegalDocuments and schedules zero or more Deadlines, a Deadline "
     "corresponds to at most one external CalendarEvent, and a Case carries at most one Rating. The "
     "emerging-technology component appears as the AiExtractionService interface, stereotyped accordingly and "
     "realised by ExtractionEngine; AiPipeline holds a reference to the interface rather than to any concrete "
     "implementation, which is what makes the model substitutable. CalendarGateway follows the same pattern, "
     "realised by both GoogleCalendarGateway and LocalCalendarGateway. The enumerations Role, CaseStatus and "
     "ProcessingStatus constrain their respective attributes, and AuditLog carries the prevHash and hash "
     "attributes together with the verifyChain operation that implement the tamper-evident requirement FR-09.")
landscape_figure(os.path.join(UML, "a2-class.png"), "Figure 3: Class diagram - LegalCase ERP domain model")

h2("4.4  Sequence diagram  (3)")
para("The diagram shows the time-ordered interaction for UC03 Upload Legal Document, the core use case of the "
     "system, across all four required layers: the user interface boundary, the application logic represented by "
     "DocumentController and AiPipeline, the data service represented by the PostgreSQL or H2 database, and the "
     "emerging-technology component represented by ExtractionEngine.")
para("The practitioner selects a file and confirms consent; the controller validates the token and scope, encrypts "
     "the payload, persists the record with status PROCESSING, enqueues the job and returns HTTP 202 immediately, "
     "so the interface is never blocked. The worker then extracts the text and enters the first alternative "
     "fragment: with no machine-readable text the document fails and a retry is offered. Otherwise the worker "
     "calls extract on the emerging-technology component, retrieves the case record for comparison and calls "
     "validateAgainstCase. The second alternative fragment carries the validation verdict - a failure sets status "
     "REJECTED, writes a hash-chained audit entry and returns highlighted evidence to the practitioner, while a "
     "pass proceeds to summarise, persists the extractions, summary and derived deadlines, and publishes the "
     "document-processed event. The practitioner then approves the summary, which triggers calendar "
     "synchronisation through the CalendarGateway and publication of the deadline-updated and new-message events, "
     "and the client receives a live portal notification.")
landscape_figure(os.path.join(UML, "a2-sequence.png"), "Figure 4: Sequence diagram - UC03 upload legal document with AI extraction and summary")

# ================================================================ QUESTION 5
h1("Question 5: Implementation and Evaluation  [13 marks]")

h2("5.1  Database, API, entities, inputs, outputs, validation and privacy  (2)")

h3("Database and main data entities")
para("The system uses a relational database - PostgreSQL 16 in production and H2 in PostgreSQL-compatibility mode "
     "for development and continuous integration - because a legal record requires ACID transactional guarantees "
     "and referential integrity. The principal entities are set out below.")

make_table(
    ["Entity (table)", "Key attributes", "Purpose and relationships"],
    [["users", "id, name, email (unique), password_hash, role, active, created_at", "Authentication and authorisation. One user has many cases, audit entries and messages."],
     ["cases", "id, reference (unique), title, client_id, client_name, opposing_party, status, matter_type, court, opened_date, amount_involved", "The aggregate root. Owns documents, deadlines, messages and at most one rating."],
     ["documents", "id, case_id, filename, storage_path, doc_type, uploaded_by, uploaded_at, size_kb, status, rejection_reason, consent_granted, full_text", "Encrypted document metadata. status is one of PENDING, PROCESSING, PROCESSED, REJECTED or FAILED."],
     ["extractions", "id, document_id, field_name, field_value, confidence, span_text, page_number", "One row per AI-extracted field. The confidence, span and page columns implement explainability (ETR-03)."],
     ["summaries", "id, document_id, case_id, paragraph1, paragraph2, paragraph3, version, status, created_by, created_at, approved_at", "Versioned three-paragraph summaries. Only rows with status APPROVED are visible to a client (FR-06)."],
     ["deadlines", "id, case_id, title, deadline_date, dl_type, reminder_days, synced", "Diary entries derived from extracted dates or captured manually."],
     ["calendar_events", "id, deadline_id, event_id, calendar_id, created_at", "Links a local deadline to its external Google Calendar event so that it can be updated or unsynchronised."],
     ["messages", "id, case_id, from_name, subject, body, sent_at, from_lawyer, read_by_client, read_by_lawyer", "Bidirectional case-level correspondence with per-party read state."],
     ["ratings", "id, case_id, client_id, lawyer_name, score, comment, created_at", "Client service ratings constrained to a score of 1 to 5."],
     ["audit_log", "id, ts, user_name, action, details, level, prev_hash, hash", "Tamper-evident record. Each row hashes its own content together with the previous row's hash (FR-09)."],
     ["training_runs / datasets", "id, model_name, dataset_id, status, requested_by, approved_at, accuracy / id, name, records, size_kb, storage_path", "Model governance: retraining runs require administrator approval before promotion (ETR-04)."]],
    widths=[17, 40, 43], font=8)

h3("API and data exchange")
para("Data is exchanged over a stateless REST/JSON API secured by bearer JSON Web Tokens carrying explicit scopes "
     "(read:client, write:case, admin:model). Representative endpoints are listed below; the complete surface "
     "comprises approximately sixty endpoints.")
make_table(
    ["Method and path", "Input", "Output", "Authorising scope"],
    [["POST /api/auth/login", "email, password", "JWT, role, display name", "public"],
     ["POST /api/cases", "reference, title, client, matter type, court", "created case (201)", "write:case"],
     ["POST /api/cases/{id}/documents", "multipart file (PDF or DOCX, max 25 MB), consent flag", "document id and status PROCESSING (202)", "write:case"],
     ["GET /api/documents/{id}/extractions", "document id", "array of field, value, confidence, span, page", "write:case / read:client"],
     ["GET /api/cases/{id}/summaries", "case id", "versioned three-paragraph summaries", "write:case / read:client"],
     ["POST /api/summaries/{id}/approve", "summary id", "approved summary", "write:case"],
     ["POST /api/cases/{id}/calendar-sync", "case id, reminder days", "created calendar event identifiers", "write:case"],
     ["GET /api/cases/{id}/calendar.ics", "case id", "RFC 5545 iCalendar feed", "write:case / read:client"],
     ["GET /api/cases/{id}/client-view", "case id", "approved summaries, shared documents, deadlines, messages", "read:client"],
     ["GET /api/events", "Accept: text/event-stream", "server-sent events new-message, deadline-updated", "any authenticated"],
     ["GET /api/admin/reports/audit.csv", "optional date range", "CSV audit export", "admin:model"]],
    widths=[30, 24, 27, 19], font=8)

h3("Validation")
para("Validation is applied at three levels. At the transport level the API rejects requests with a missing or "
     "expired token, an insufficient scope, an unsupported MIME type, a file exceeding 25 MB or a malformed JSON "
     "body. At the domain level the services enforce business rules: a case reference must be unique, a deadline "
     "date must be a real calendar date, a rating must lie between 1 and 5, a summary must contain exactly three "
     "non-empty paragraphs, and consent must be recorded before processing may begin. At the AI level the "
     "extraction engine validates each candidate date as a genuine calendar date before accepting it and "
     "cross-validates the extracted parties and case number against the target case record, blocking the document "
     "where they do not correspond. At the database level uniqueness, foreign-key and not-null constraints provide "
     "a final guarantee of integrity.")

h3("Privacy requirements")
para("Legal case files contain personal information and privileged communications, so the system is designed to "
     "the Protection of Personal Information Act 4 of 2013. Processing is lawful only where a recorded consent "
     "flag is present on the document. Collection is minimised to the fields necessary for case management. "
     "Access is purpose-limited by role scope, so a client can retrieve only their own matters and only approved "
     "summaries and explicitly shared documents. Documents are encrypted at rest with AES-256-GCM and in transit "
     "with TLS. Data residency is enforced by pinning storage and compute to a South African region. A data "
     "subject may request export or erasure of their personal information, which is fulfilled within 30 days and "
     "recorded in the audit log. Retention follows the firm's statutory obligation to retain matter files, after "
     "which records are securely destroyed. The hash-chained audit log provides the accountability evidence the "
     "Act requires of a responsible party.")

page_break()

h2("5.2  Integration of the emerging-technology component and interface contract  (2)")

h3("Where the component is integrated")
para("The AI component is integrated at a single, deliberately narrow point in the workflow: immediately after a "
     "document is persisted and before any of its content is surfaced to a user. The upload controller does not "
     "call the model directly. It writes the encrypted file, creates the document record with status PROCESSING "
     "and hands a job to AiPipeline, which executes on a managed worker pool and returns control to the user "
     "immediately. The pipeline then performs four steps in order: it extracts the raw text page by page using "
     "PDFBox or Apache POI; it calls extract on the AI component to obtain the structured fields with their "
     "confidence scores, source spans and page numbers; it calls validateAgainstCase to determine whether the "
     "document genuinely belongs to the matter it was filed under, blocking it if not; and finally, only for "
     "documents that pass validation, it calls summarise to produce the three-paragraph brief. Derived deadlines "
     "are created from the accepted dates, everything is persisted in a single transaction, a hash-chained audit "
     "entry is written and a server-sent event notifies the interface.")
para("The entire component is reached only through the AiExtractionService interface. The prototype binds a "
     "deterministic engine tuned to South African legal document conventions; the production target binds a "
     "transformer model fine-tuned on legal text and served over HTTP from a GPU host. Because both satisfy the "
     "same contract, substitution requires no change to AiPipeline or to any controller. Practitioner corrections "
     "are captured as labelled training examples and no retrained model reaches production without explicit "
     "administrator approval, preserving human accountability.")

h3("Interface contract")
code_lines = [
    "public interface AiExtractionService {",
    "",
    "    /** Returns one Extraction per recognised field. Every Extraction carries a",
    "     *  confidence score (0.0-1.0), the verbatim source span and the page number,",
    "     *  satisfying the explainability requirement ETR-03. */",
    "    List<Extraction> extract(String documentText);",
    "",
    "    /** Determines whether the document genuinely belongs to the target case. */",
    "    ValidationResult validateAgainstCase(LegalDocument doc, Case target);",
    "",
    "    /** Returns a three-paragraph summary: Facts / Legal Issues / Current Status. */",
    "    Summary summarise(String documentText);",
    "}",
    "",
    "record ValidationResult(boolean matches, String reason, List<String> evidenceSpans) { }",
]
h3("Pseudocode fragment - the processing pipeline")
code2 = [
    "PROCEDURE processDocument(document, targetCase)",
    "    text <- extractTextPerPage(document.file)            // PDFBox / Apache POI",
    "",
    "    IF text IS empty THEN",
    "        setStatus(document, FAILED, \"No extractable text - scan requires OCR\")",
    "        publishEvent(\"document-failed\", document.id)",
    "        RETURN",
    "    END IF",
    "",
    "    fields  <- aiService.extract(text)                   // ETR-01, ETR-02",
    "    verdict <- aiService.validateAgainstCase(document, targetCase)",
    "",
    "    IF NOT verdict.matches THEN                          // FR-04",
    "        setStatus(document, REJECTED, verdict.reason)",
    "        storeExtractions(fields)                         // retained for review",
    "        highlightSpans(verdict.evidenceSpans)",
    "        writeAuditEntry(\"DOCUMENT_REJECTED\", document.id, verdict.reason)",
    "        publishEvent(\"document-rejected\", document.id)",
    "        RETURN",
    "    END IF",
    "",
    "    summary <- aiService.summarise(text)                 // FR-03",
    "    FOR EACH field IN fields WHERE field.name = \"date\" DO",
    "        createDeadline(targetCase, field.value, field.confidence)   // FR-05",
    "    END FOR",
    "",
    "    persist(fields, summary, deadlines)                  // single transaction",
    "    setStatus(document, PROCESSED)",
    "    writeAuditEntry(\"DOCUMENT_PROCESSED\", document.id)  // FR-09 hash-chained",
    "    publishEvent(\"document-processed\", document.id)",
    "    publishEvent(\"deadline-updated\", targetCase.id)      // FR-11 live dashboard",
    "END PROCEDURE",
]


def code_block(lines):
    p = doc.add_paragraph()
    pf2 = p.paragraph_format
    pf2.line_spacing = 1.0
    pf2.space_before = Pt(4)
    pf2.space_after = Pt(10)
    pf2.left_indent = Cm(0.4)
    for i, line in enumerate(lines):
        r = p.add_run(line)
        r.font.name = "Consolas"
        r.font.size = Pt(8.5)
        r._element.rPr.rFonts.set(qn("w:ascii"), "Consolas")
        r._element.rPr.rFonts.set(qn("w:hAnsi"), "Consolas")
        if i < len(lines) - 1:
            r.add_break()
    pPr = p._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear"); shd.set(qn("w:color"), "auto"); shd.set(qn("w:fill"), "F4F6F9")
    pPr.append(shd)
    return p


# place the interface contract first
_tmp = doc.paragraphs
code_block(code_lines)
code_block(code2)

h2("5.3  Testing and evaluation plan  (2)")

h3("Functional testing")
make_table(
    ["Level", "Scope and method", "Tooling and evidence"],
    [["Unit testing", "Business logic in isolation: extraction pattern matching, date validation, password hashing, JWT scope checks, audit hash-chain construction, iCalendar generation.", "Automated assertions executed on every commit. Target: 70% line coverage of the service and NLP packages."],
     ["Integration testing", "Controller-to-service-to-database paths against a live H2 instance: case creation, document upload and retrieval, summary approval, deadline synchronisation, message read-state.", "Automated integration suite run in continuous integration against a freshly migrated schema."],
     ["End-to-end testing", "The complete workflow across the running service: authenticate, create case, upload document, poll until processed, verify extractions and summary, approve, synchronise calendar, confirm the client portal reflects the change.", "Scripted end-to-end suites executed against the deployed container. Current status: all suites passing."],
     ["Controlled document suite", "Eight prepared documents covering correctly filed PDFs and DOCX files, wrong-matter documents, documents with no case content and a scanned image-only PDF, each with an expected terminal status.", "Automated upload harness. Current recorded result: 8 of 8 scenarios passed, 0 failed."],
     ["Security testing", "Authorisation matrix testing of every endpoint against every role, verification that encrypted files are unreadable without the key, and confirmation that a revoked account cannot authenticate.", "Scripted negative-path tests asserting HTTP 403 and 401 responses, plus manual review."],
     ["Regression testing", "The full automated suite is re-executed on every push to the main branch before a container image is published.", "GitHub Actions workflow; a failing suite blocks the build."]],
    widths=[16, 48, 36], font=8)

h3("Measurable emerging-technology metric")
para("The primary emerging-technology metric is field-level extraction accuracy, reported as precision, recall and "
     "F1 for each of the four critical fields - dates, plaintiff, defendant and case number - computed against a "
     "human-annotated held-out evaluation set of at least 50 legal documents that the model has never seen. The "
     "acceptance threshold is a macro-averaged precision of 0.90 or higher and a recall of 0.85 or higher, as "
     "specified in ETR-01. The evaluation harness is run automatically against every candidate model version and "
     "the results are stored against that version, so that accuracy can be compared across versions and a "
     "regression triggers rollback under ETR-04. Two secondary metrics are tracked alongside it: 95th-percentile "
     "inference latency per page, with a threshold of 500 milliseconds (ETR-02), and the false-rejection rate on "
     "correctly filed documents, with a threshold of 5% (ETR-03). Reporting precision and recall separately rather "
     "than a single accuracy figure is deliberate, because the operational cost of the two error types differs "
     "sharply: a missed court date is far more damaging to a client than a spurious extra diary entry, so recall "
     "on dates is weighted more heavily than precision in the review of results.")

h3("User acceptance testing")
para("User acceptance testing is conducted as a four-week pilot with three small law firms, comprising at least "
     "five practitioners and twenty portal clients. Each participant receives a single 15-minute orientation and "
     "then works on live matters. Acceptance is measured against four criteria: at least 8 of 10 practitioners "
     "complete the upload-review-approve-synchronise workflow unaided within 3 minutes; the System Usability "
     "Scale score averages 75 or higher; the mean client-satisfaction rating is 4.0 or higher out of 5.0; and the "
     "count of missed or late-diarised deadlines falls by at least 40% against the four-week manual baseline "
     "captured before the pilot. Every defect raised during the pilot is triaged by severity, and no severity-one "
     "defect may remain open at sign-off. Formal acceptance is recorded by the principal of each participating "
     "firm.")

h3("Technical risk and mitigation")
make_table(
    ["Risk", "Likelihood / Impact", "Consequence", "Mitigation"],
    [["Extraction inaccuracy: the AI model misreads or omits a court date, a party or a case number, and the error propagates into the diary and the client-facing summary.",
      "Medium / High",
      "A missed or wrongly diarised court date could prejudice a client's matter and expose the firm to a professional-negligence claim - precisely the harm the system exists to prevent.",
      "Four layers of mitigation. First, no AI output reaches a client without explicit practitioner approval (FR-06), so a human professional remains accountable. Second, every extracted field carries a confidence score and the verbatim source span with its page number, so a practitioner verifies a doubtful field in seconds rather than re-reading the document (ETR-03). Third, fields below a confidence threshold are flagged for mandatory review rather than accepted silently. Fourth, every correction is captured as a labelled training example and accuracy is re-measured against the held-out set before any model version is promoted, with rollback available if it regresses (ETR-04). The residual risk is accepted on the basis that the system augments rather than replaces professional judgement."]],
    widths=[26, 11, 25, 38], font=8)

para("Two further risks are noted. Scanned image-only documents cannot currently be processed and fail with an "
     "explicit reason rather than silently; the mitigation is the visible FAILED status with a retry action, and "
     "the addition of an OCR stage is the first roadmap item. Dependence on the Google Calendar API creates an "
     "external availability risk; the mitigation is the CalendarGateway abstraction with a local iCalendar "
     "implementation, so that deadline synchronisation degrades to an .ics feed rather than failing outright.")

page_break()

# ================================================================ REFERENCES
h1("Reference List")
refs = [
    "Apache Software Foundation. 2024. Apache PDFBox: a Java PDF library. [Online] Available at: https://pdfbox.apache.org [Accessed 14 September 2026].",
    "Apache Software Foundation. 2024. Apache POI: the Java API for Microsoft documents. [Online] Available at: https://poi.apache.org [Accessed 14 September 2026].",
    "Bass, L., Clements, P. and Kazman, R. 2021. Software architecture in practice. 4th ed. Boston: Addison-Wesley.",
    "Desruisseaux, B. (ed.) 2009. Internet calendaring and scheduling core object specification (iCalendar). RFC 5545. Fremont: Internet Engineering Task Force.",
    "Dustin, E., Garrett, T. and Gauf, B. 2009. Implementing automated software testing. Boston: Addison-Wesley.",
    "Fowler, M. 2018. Refactoring: improving the design of existing code. 2nd ed. Boston: Addison-Wesley.",
    "Google LLC. 2024. Google Calendar API v3 reference. [Online] Available at: https://developers.google.com/calendar/api [Accessed 14 September 2026].",
    "Grady, R.B. 1992. Practical software metrics for project management and process improvement. Englewood Cliffs: Prentice Hall.",
    "International Organization for Standardization. 2022. ISO/IEC 27001:2022 Information security, cybersecurity and privacy protection - information security management systems - requirements. Geneva: ISO.",
    "Javalin. 2024. Javalin 6 documentation: a lightweight web framework for Java and Kotlin. [Online] Available at: https://javalin.io/documentation [Accessed 14 September 2026].",
    "Jones, M., Bradley, J. and Sakimura, N. 2015. JSON Web Token (JWT). RFC 7519. Fremont: Internet Engineering Task Force.",
    "Larman, C. 2004. Applying UML and patterns: an introduction to object-oriented analysis and design and iterative development. 3rd ed. Upper Saddle River: Prentice Hall.",
    "Object Management Group. 2017. OMG Unified Modeling Language (OMG UML) version 2.5.1. Needham: OMG.",
    "PostgreSQL Global Development Group. 2024. PostgreSQL 16 documentation. [Online] Available at: https://www.postgresql.org/docs/16 [Accessed 14 September 2026].",
    "Republic of South Africa. 2013. Protection of Personal Information Act 4 of 2013. Pretoria: Government Printer.",
    "Republic of South Africa. 2014. Legal Practice Act 28 of 2014. Pretoria: Government Printer.",
    "Sommerville, I. 2016. Software engineering. 10th ed. Harlow: Pearson Education.",
    "Vaswani, A., Shazeer, N., Parmar, N., Uszkoreit, J., Jones, L., Gomez, A.N., Kaiser, L. and Polosukhin, I. 2017. Attention is all you need. In: Advances in Neural Information Processing Systems 30. Long Beach: NeurIPS, pp. 5998-6008.",
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

page_break()

# ================================================================ APPENDIX A
h1("Appendix A: Requirements Traceability Matrix")
para("The matrix demonstrates that every requirement specified in Question 2 is realised by an identified use "
     "case, domain class and API endpoint, and is covered by a stated verification method.", italic=True, size=10)

make_table(
    ["Req. ID", "Use case", "Primary class(es)", "Endpoint", "Verification"],
    [["FR-01", "UC03", "LegalDocument, Case", "POST /api/cases/{id}/documents", "Integration + E2E suite"],
     ["FR-02", "UC04", "ExtractionEngine, Extraction", "GET /api/documents/{id}/extractions", "Unit + accuracy harness (ETR-01)"],
     ["FR-03", "UC06", "Summarizer, Summary", "GET /api/cases/{id}/summaries", "Integration + E2E suite"],
     ["FR-04", "UC05, UC20", "ExtractionEngine, LegalDocument", "GET /api/admin/rejections", "Controlled document suite (8/8)"],
     ["FR-05", "UC08, UC09, UC10", "Deadline, CalendarGateway, CalendarEvent", "POST /api/cases/{id}/calendar-sync; GET /api/cases/{id}/calendar.ics", "Integration + iCalendar validation"],
     ["FR-06", "UC07", "Summary, Lawyer", "POST /api/summaries/{id}/approve", "E2E suite + negative-path test"],
     ["FR-07", "UC12, UC13", "Client, Case, Summary", "GET /api/cases/{id}/client-view", "Authorisation matrix test"],
     ["FR-08", "UC11", "ClientMessage, EventBus", "POST /api/cases/{id}/messages; GET /api/events", "E2E + SSE latency measurement"],
     ["FR-09", "UC16", "AuditLog", "GET /api/admin/reports/audit.csv", "Hash-chain verification test"],
     ["FR-10", "UC15, UC17", "Administrator, TrainingRun, Dataset", "POST /api/admin/users/{id}/revoke", "Integration + revocation test"],
     ["FR-11", "UC18", "MetricsService, EventBus", "GET /api/admin/metrics", "Live dashboard update test"],
     ["FR-12", "UC19, UC14", "AiPipeline, Rating", "POST /api/documents/{id}/retry; POST /api/ratings", "Integration suite"],
     ["NFR-01", "All", "All service classes", "All", "Load test at 25 concurrent users"],
     ["NFR-02", "All", "Database, backup configuration", "GET /api/health", "Uptime monitoring + restore rehearsal"],
     ["NFR-03", "UC01", "EncryptionUtil, JwtUtil, PasswordHasher", "All", "Penetration test + configuration review"],
     ["NFR-04", "All", "Browser and JavaFX clients", "n/a", "Usability testing (SUS >= 75)"],
     ["NFR-05", "n/a", "Dockerfile, compose configuration", "GET /api/health", "Clean-host deployment rehearsal"],
     ["NFR-06", "UC03, UC16", "LegalDocument.consentGranted, AuditLog", "GET /api/admin/reports/audit.csv", "Compliance review"],
     ["ETR-01", "UC04", "ExtractionEngine", "GET /api/documents/{id}/extractions", "Accuracy harness on held-out set"],
     ["ETR-02", "UC04, UC06", "AiPipeline, ExtractionEngine", "n/a (internal)", "Latency harness, 95th percentile"],
     ["ETR-03", "UC05, UC20", "ExtractionEngine, Extraction", "GET /api/admin/reports/rejections.csv", "Controlled document suite"],
     ["ETR-04", "UC17", "TrainingRun, Dataset", "POST /api/admin/training-runs/{id}/approve", "Governance workflow test + rollback"]],
    widths=[9, 13, 27, 34, 22], font=8)

footer_pagenum()
doc.save(OUT)
print("WROTE", OUT, os.path.getsize(OUT), "bytes")
