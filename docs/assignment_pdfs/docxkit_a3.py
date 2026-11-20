"""Shared Word formatting helpers for the ISJ107V assignment generators."""
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
UML = os.path.join(ROOT, "docs", "uml", "a3")
OUT = os.path.join(HERE, "Assignment3_ISJ107V_Ally_Mongala.docx")

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


def footer_pagenum(label="Assignment 2"):
    for s in doc.sections:
        s.footer.is_linked_to_previous = False
        f = s.footer.paragraphs[0]
        # clear anything already present so the footer is never duplicated
        for child in list(f._p):
            if child.tag != qn("w:pPr"):
                f._p.remove(child)
        f.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = f.add_run(f"Ally Mongala  |  219361629  |  ISJ107V {label}  |  Page ")
        r.font.size = Pt(8.5)
        r.font.name = BODY_FONT
        r.font.color.rgb = GREY
        fld = OxmlElement("w:fldSimple")
        fld.set(qn("w:instr"), "PAGE")
        f._p.append(fld)




def code_block(lines):
    """Monospaced, shaded block for code, pseudocode and directory trees."""
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
