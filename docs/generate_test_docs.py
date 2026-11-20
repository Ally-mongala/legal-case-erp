#!/usr/bin/env python3
"""Generates the test-case documents in docs/test-cases/."""
import os
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from docx import Document

OUT = os.path.join(os.path.dirname(__file__), "test-cases")
os.makedirs(OUT, exist_ok=True)

def make_pdf(name, lines, font="Helvetica", size=11):
    c = canvas.Canvas(os.path.join(OUT, name), pagesize=A4)
    w, h = A4
    y = h - 72
    for line in lines:
        c.setFont(font, size)
        c.drawString(72, y, line)
        y -= 22
        if y < 60:
            c.showPage()
            y = h - 72
    c.save()
    print("pdf:", name)

def make_docx(name, lines, heading=None):
    doc = Document()
    if heading:
        doc.add_heading(heading, level=1)
    for line in lines:
        doc.add_paragraph(line) if line else doc.add_paragraph("")
    doc.save(os.path.join(OUT, name))
    print("docx:", name)

# ---------------- 1. MATCHING docs (should be PROCESSED) ----------------
make_pdf("1-match_mokoena_affidavit.pdf", [
    "IN THE HIGH COURT OF SOUTH AFRICA",
    "GAUTENG DIVISION, JOHANNESBURG",
    "Case Number: GP/JHB/2026/000841",
    "",
    "In the matter between:",
    "THABO MOKOENA Plaintiff",
    "and",
    "SIYAYA TRANSPORT (PTY) LTD Defendant",
    "",
    "AFFIDAVIT OF THE PLAINTIFF",
    "",
    "1. I, Thabo Mokoena, the plaintiff in this matter, state under oath:",
    "2. On 12 January 2026 I was injured in a collision caused by the defendant's driver on the N1 highway.",
    "3. The defendant admits liability for the collision and the matter proceeds on quantum only.",
    "4. The claim for general and special damages is R1,250,000.00.",
    "5. The matter is set down for hearing on 14 September 2026 in the Gauteng Division of the High Court, Johannesburg.",
])

make_docx("1-match_vanwyk_statement.docx", [
    "IN THE MAGISTRATE'S COURT FOR THE DISTRICT OF PRETORIA",
    "Case Number: 8741/2026",
    "In the matter between:",
    "ELNA VAN WYK Plaintiff",
    "and",
    "STANDARD BANK OF SOUTH AFRICA LTD Defendant",
    "SUMMONS AND PARTICULARS OF CLAIM",
    "1. The plaintiff, Elna van Wyk, a pensioner resident at 22 Acacia Street, Brooklyn, Pretoria, sues the defendant for payment of the amount of R412,000.00.",
    "2. The claim arises from the administration of the plaintiff's home loan account number 4452 8891 0042.",
    "3. The plaintiff alleges that the defendant charged interest on arrears in excess of the amounts permitted under the National Credit Act 34 of 2005.",
    "4. The matter has been set down for hearing on 9 September 2026 in the Pretoria Magistrate's Court.",
], heading="Statement of Claim")

# ---------------- 2. WRONG MATTER docs (should be BLOCKED on c1/c2) ----------------
make_pdf("2-wrong_matter_nkosi_abba.pdf", [
    "IN THE HIGH COURT OF SOUTH AFRICA",
    "GAUTENG DIVISION, JOHANNESBURG",
    "Case Number: GP/JHB/2026/991122",
    "",
    "In the matter between:",
    "THABO NKOSI Plaintiff",
    "and",
    "ABSA BANK LTD Defendant",
    "",
    "SUMMONS",
    "",
    "1. The plaintiff, Thabo Nkosi, claims payment of R540,000.00 from the defendant arising from the unlawful cancellation of his bond.",
    "2. The matter is set down for hearing on 4 December 2026.",
])

make_docx("2-wrong_matter_divorce.docx", [
    "IN THE HIGH COURT OF SOUTH AFRICA",
    "GAUTENG DIVISION, JOHANNESBURG",
    "Case Number: 5542/2026",
    "In the matter between:",
    "SIPHO DLAMINI Applicant",
    "and",
    "NOMSA DLAMINI Respondent",
    "DIVORCE SUMMONS",
    "1. The marriage between the parties has broken down irretrievably.",
    "2. The applicant claims a decree of divorce, division of the joint estate and maintenance.",
], heading="Divorce Summons")

# ---------------- 3. NO CASE INFO docs (should be BLOCKED - no parties/reference) ----------------
make_pdf("3-no_case_info_grocery_list.pdf", [
    "Grocery List - January 2026",
    "",
    "Milk R 24.99",
    "Bread R 18.50",
    "Cereal R 89.90",
    "Coffee R 129.00",
    "Total: R 262.39",
    "",
    "Reminder: buy a birthday card before 5 February 2026.",
])

make_docx("3-no_case_info_utility_bill.docx", [
    "CITY POWER - ELECTRICITY INVOICE",
    "Account number: 7741002233",
    "Invoice date: 3 July 2026",
    "Amount due: R 1,842.55",
    "Due date: 18 July 2026",
    "Payment reference: 7741002233",
    "Thank you for your payment.",
], heading="Electricity Invoice")

# ---------------- 4. PARTIAL / SUSPICIOUS doc (same client, wrong defendant - still processed) ----------------
make_pdf("4-partial_match_wrong_defendant.pdf", [
    "IN THE HIGH COURT OF SOUTH AFRICA",
    "GAUTENG DIVISION, JOHANNESBURG",
    "",
    "In the matter between:",
    "THABO MOKOENA Plaintiff",
    "and",
    "PHUMANI LOGISTICS CC Defendant",
    "",
    "NOTICE OF INTENTION TO DEFEND",
    "1. The defendant intends to defend the action brought by the plaintiff, Thabo Mokoena.",
    "2. The claim is set down for hearing on 20 November 2026.",
])

# ---------------- 5. BLANK / IMAGE-ONLY doc (should FAIL - no extractable text) ----------------
def make_blank_pdf(name):
    c = canvas.Canvas(os.path.join(OUT, name), pagesize=A4)
    # page with an image-like drawing but NO text
    c.setFillColorRGB(0.9, 0.9, 0.9)
    c.rect(60, 500, 480, 200, fill=1, stroke=0)
    c.setStrokeColorRGB(0, 0, 0)
    c.line(60, 500, 540, 700)
    c.line(60, 700, 540, 500)
    c.showPage()
    c.save()
    print("pdf (blank):", name)

make_blank_pdf("5-scanned_no_text.pdf")

# ---------------- 6. SEEDED existing demo files (already in docs/) ----------------
print("\nDone. Files in docs/test-cases/:")
for f in sorted(os.listdir(OUT)):
    print("  ", f, f"({os.path.getsize(os.path.join(OUT, f))} bytes)")
