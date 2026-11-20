#!/usr/bin/env python3
"""Generates the ISJ107V assignment PDFs in the required written-assignment format:
  - Calibri 11 (fallback Carlito/Liberation Sans for embedding)
  - 1.5 line spacing
  - margins: top/bottom 2.54cm, left/right 3.17cm
  - cover page, table of contents, declaration of originality, Harvard references
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib.enums import TA_JUSTIFY, TA_CENTER, TA_LEFT
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor
from reportlab.platypus import (BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer,
                                PageBreak, Table, TableStyle, Image, NextPageTemplate,
                                KeepTogether)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

OUT = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)

# ---------- fonts (Calibri -> Carlito, metric-compatible clone) ----------
def find_font(name):
    import glob
    for pat in [f"/usr/share/fonts/**/{name}", f"/usr/share/fonts/**/*{name}*"]:
        for f in glob.glob(pat, recursive=True):
            if f.endswith((".ttf", ".TTF")):
                return f
    return None

CALIBRI = "/usr/share/fonts/truetype/crosextra/Carlito-Regular.ttf"
CALIBRI_B = "/usr/share/fonts/truetype/crosextra/Carlito-Bold.ttf"
CALIBRI_I = "/usr/share/fonts/truetype/crosextra/Carlito-Italic.ttf"
if CALIBRI and os.path.exists(CALIBRI):
    pdfmetrics.registerFont(TTFont("Calibri", CALIBRI))
    pdfmetrics.registerFont(TTFont("Calibri-Bold", CALIBRI_B or CALIBRI))
    pdfmetrics.registerFont(TTFont("Calibri-Italic", CALIBRI_I or CALIBRI))
    FONT = "Calibri"
    # real mono font for code spans (base-14 Courier is not embedded)
    MONO = find_font("LiberationMono-Regular.ttf") or find_font("DejaVuSansMono.ttf") or find_font("UbuntuMono-Regular.ttf")
    if MONO:
        pdfmetrics.registerFont(TTFont("Courier", MONO))
    else:
        FONT_MONO = None
else:
    # fallback Liberation Sans (metric-compatible with Arial)
    pdfmetrics.registerFont(TTFont("Calibri", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"))
    pdfmetrics.registerFont(TTFont("Calibri-Bold", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"))
    pdfmetrics.registerFont(TTFont("Calibri-Italic", "/usr/share/fonts/truetype/liberation/LiberationSans-Italic.ttf"))
    FONT = "Calibri"

NAVY = HexColor("#16233C")
GOLD = HexColor("#C9A227")

STUDENT = "Ally Mongala"
STUDENT_NO = "219361629"
MODULE = "ISJ107V Integrated Software Project"
YEAR = "2026"
EMAIL = "tut4life@tut4life.ac.za"

# ---------- styles ----------
def body_style(size=11, leading=None, align=TA_JUSTIFY):
    return ParagraphStyle(
        "body", fontName=FONT, fontSize=size,
        leading=leading or (size * 1.5),      # 1.5 line spacing
        alignment=align, spaceAfter=6, textColor=HexColor("#1a1a1a"))

S_BODY = body_style()
S_BODY_L = body_style(align=TA_LEFT)
S_H1 = ParagraphStyle("h1", fontName="Calibri-Bold", fontSize=16, leading=20,
                      textColor=NAVY, spaceBefore=10, spaceAfter=8)
S_H2 = ParagraphStyle("h2", fontName="Calibri-Bold", fontSize=13, leading=17,
                      textColor=NAVY, spaceBefore=8, spaceAfter=4)
S_H3 = ParagraphStyle("h3", fontName="Calibri-Bold", fontSize=11.5, leading=15,
                      textColor=HexColor("#1f3050"), spaceBefore=6, spaceAfter=3)
S_CAPTION = ParagraphStyle("cap", fontName="Calibri-Italic", fontSize=10, leading=13,
                           alignment=TA_CENTER, textColor=HexColor("#555555"), spaceAfter=10)
S_REF = ParagraphStyle("ref", fontName=FONT, fontSize=10.5, leading=15,
                       alignment=TA_LEFT, leftIndent=1.1*cm, firstLineIndent=-1.1*cm,
                       spaceAfter=5)
S_TOC = ParagraphStyle("toc", fontName=FONT, fontSize=11, leading=16, alignment=TA_LEFT)

# ---------- helpers ----------
def P(text, style=S_BODY):
    return Paragraph(text, style)

def heading(num, text):
    return Paragraph(f"<b>{num}. {text}</b>", S_H1)

def sub(num, text):
    return Paragraph(f"<b>{num} {text}</b>", S_H2)

def figure(path, caption, width=15*cm):
    im = Image(path, width=width, height=width * 0.75)
    return [im, Spacer(1, 4), Paragraph(caption, S_CAPTION)]

def cover(doc, title, subtitle):
    """Cover page in the TUT style."""
    st = []
    st.append(Spacer(1, 1.2*cm))
    st.append(Paragraph("<b>Faculty of Information and Communication Technology</b>", 
                        ParagraphStyle("c1", fontName="Calibri-Bold", fontSize=13, leading=17, alignment=TA_CENTER)))
    st.append(Paragraph("<b>Department of Computer Science</b>",
                        ParagraphStyle("c2", fontName="Calibri-Bold", fontSize=13, leading=17, alignment=TA_CENTER)))
    st.append(Spacer(1, 1.5*cm))
    st.append(Paragraph(f"<b>{title}</b>",
                        ParagraphStyle("t", fontName="Calibri-Bold", fontSize=20, leading=26,
                                       alignment=TA_CENTER, textColor=NAVY)))
    st.append(Spacer(1, 0.4*cm))
    st.append(Paragraph(subtitle,
                        ParagraphStyle("st", fontName="Calibri-Italic", fontSize=13, leading=18,
                                       alignment=TA_CENTER, textColor=HexColor("#44506a"))))
    st.append(Spacer(1, 2.2*cm))
    info = [
        ["Module", MODULE],
        ["Assessment", title.split(" — ")[0]],
        ["Student", f"{STUDENT}  ({STUDENT_NO})"],
        ["Email", EMAIL],
        ["Year", YEAR],
    ]
    t = Table(info, colWidths=[4.5*cm, 11*cm])
    t.setStyle(TableStyle([
        ("FONTNAME", (0,0), (-1,-1), FONT),
        ("FONTSIZE", (0,0), (-1,-1), 11),
        ("FONTNAME", (0,0), (0,-1), "Calibri-Bold"),
        ("TEXTCOLOR", (0,0), (0,-1), NAVY),
        ("BOTTOMPADDING", (0,0), (-1,-1), 6),
        ("TOPPADDING", (0,0), (-1,-1), 6),
        ("LINEBELOW", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
    ]))
    st.append(t)
    st.append(Spacer(1, 2.5*cm))
    st.append(Paragraph(f"<b>Due date:</b> as per the ISJ107V 2026 schedule",
                        ParagraphStyle("dd", fontName=FONT, fontSize=11, leading=15, alignment=TA_CENTER)))
    st.append(Spacer(1, 1.5*cm))
    st.append(Paragraph("This assessment is my original work. See the Declaration of Originality inside.",
                        ParagraphStyle("n", fontName="Calibri-Italic", fontSize=9.5, leading=13, alignment=TA_CENTER,
                                       textColor=HexColor("#666666"))))
    st.append(PageBreak())
    return st

def declaration():
    st = []
    st.append(Paragraph("<b>DECLARATION OF ORIGINALITY</b>", S_H1))
    st.append(Spacer(1, 8))
    st.append(P("I, <b>Ally Mongala</b> (student number <b>219361629</b>), declare that this assessment is my own original "
                "work. Where the work of others has been used, whether from a printed source, the internet or any "
                "other source, it has been properly acknowledged and referenced in accordance with the Harvard "
                "referencing method."))
    st.append(P("I have not allowed, and will not allow, anyone to copy my work with the intention of passing it "
                "off as their own."))
    st.append(Spacer(1, 10))
    st.append(Table([["Signature: ______________________________"],
                     ["Date: ______________________________"]],
                    colWidths=[15*cm], style=TableStyle([
                        ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 11),
                        ("TOPPADDING", (0,0), (-1,-1), 10)])))
    st.append(PageBreak())
    return st

def toc(items):
    st = [Paragraph("<b>TABLE OF CONTENTS</b>", S_H1), Spacer(1, 6)]
    for num, title in items:
        st.append(Paragraph(f"{num}  {title}", S_TOC))
    st.append(PageBreak())
    return st

def references(refs):
    st = [PageBreak(), Paragraph("<b>REFERENCES</b>", S_H1), Spacer(1, 6)]
    for r in refs:
        st.append(Paragraph(r, S_REF))
    return st

def build(filename, title, subtitle, story, refs, toc_items, landscape_pages=None):
    doc = BaseDocTemplate(os.path.join(OUT, filename), pagesize=A4,
                          leftMargin=3.17*cm, rightMargin=3.17*cm,
                          topMargin=2.54*cm, bottomMargin=2.54*cm,
                          title=title, author=STUDENT)
    frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="f")

    def on_page(canvas, doc_):
        canvas.saveState()
        # header rule
        canvas.setStrokeColor(NAVY); canvas.setLineWidth(0.6)
        canvas.line(doc_.leftMargin, A4[1]-2.2*cm, A4[0]-doc_.rightMargin, A4[1]-2.2*cm)
        canvas.setFont("Calibri", 8); canvas.setFillColor(HexColor("#888888"))
        canvas.drawString(doc_.leftMargin, A4[1]-1.9*cm, "Legal Case Management ERP — ISJ107V 2026")
        canvas.drawRightString(A4[0]-doc_.rightMargin, A4[1]-1.9*cm, STUDENT)
        # footer
        canvas.setFont("Calibri", 8)
        canvas.drawCentredString(A4[0]/2, 1.4*cm, f"Page {canvas.getPageNumber()}")
        canvas.restoreState()

    doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=on_page)])
    story = cover(doc, title, subtitle) + declaration() + toc(toc_items) + story + references(refs)
    doc.build(story)
    print("built:", filename)
