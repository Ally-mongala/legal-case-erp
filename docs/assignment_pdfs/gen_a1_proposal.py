#!/usr/bin/env python3
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from pdfkit import *

story = []

# 1. Executive Summary
story += [heading("1", "Executive Summary"), 
P("Small law firms continue to experience issues with communication, efficiency and case management. "
  "Unlike large firms that can afford enterprise-level ERP systems, independent lawyers often rely on "
  "fragmented tools such as email, spreadsheets and manual filing. This leads to missed deadlines, poor "
  "client communication and reduced competitiveness. This project proposes a Legal Case Management ERP "
  "system that integrates a central legal database for structured case storage, Google Calendar "
  "synchronisation via API for deadline management, an AI summarisation module that scans uploaded legal "
  "PDFs to extract key dates and parties and to generate concise three-paragraph case summaries, and a "
  "client portal for simplified communication and updates."),
P("The project is feasible because it builds on existing, widely available technologies: AI and natural "
  "language processing libraries, cloud databases, calendar APIs and standard web and desktop UI "
  "frameworks. The consequences of not proceeding are continued missed deadlines, malpractice risk and "
  "clients choosing competitors that offer online case tracking.")]

# 2. Background
story += [heading("2", "Background"),
sub("2.1", "History"),
P("Traditionally, small law firms rely on manual filing systems and standalone tools. Larger firms adopted "
  "costly ERP systems, but smaller practices lacked affordable alternatives. The rise of LegalTech has "
  "created opportunities to democratise access to automation, particularly through AI and cloud-based "
  "solutions."),
sub("2.2", "Requirements"),
sub("2.2.1", "Business Problem"),
P("The business problem is poor communication between lawyers and clients, inefficient manual processes "
  "for document review and deadline tracking, and missed opportunities due to a lack of integrated "
  "systems. Symptoms include missed deadlines, client dissatisfaction and lost billable time."),
sub("2.2.2", "Business Opportunity"),
P("A gap exists in the market for affordable ERP-style systems tailored to small law firms. AI can "
  "automate repetitive tasks, reduce costs and improve client satisfaction. Regulatory changes "
  "increasingly require digital record-keeping, which creates demand for integrated solutions."),
sub("2.3", "Expected Solution"),
P("The primary goal of this project is to deliver a Legal Case Management ERP system that improves "
  "efficiency and communication in small law firms by automating document handling, synchronising "
  "deadlines and enhancing client interaction."),
sub("2.3.1", "Scope — inside"),
P("Automate extraction of case details (dates, parties, summaries) from uploaded legal PDFs; synchronise "
  "deadlines and reminders with Google Calendar via API; provide a secure, cloud-based legal database "
  "for storing structured case information; offer a client-facing portal for simplified communication "
  "and updates; and include an analysis dashboard to track efficiency and satisfaction metrics."),
sub("2.3.2", "Scope — outside"),
P("Advanced litigation analysis (for example, predicting case outcomes), large-scale enterprise ERP "
  "features not relevant to small firms, and integration with external government or legal systems "
  "beyond basic document handling."),
sub("2.3.3", "Approach"),
P("Use machine learning and NLP models to scan and summarise legal documents; integrate with Google "
  "Calendar APIs to automate deadline tracking and reminders; deploy a cloud-based database to securely "
  "store and manage case information; build a web and desktop client portal for simplified "
  "communication and real-time updates; and apply iterative testing and feedback loops to ensure "
  "accuracy, usability and adoption.")]

# 3. Proposal
story += [heading("3", "Proposal"),
sub("3.1", "Vision and Goals"),
P("<b>Vision statement:</b> By December 2026, empower small law firms to improve efficiency by 25% through "
  "AI-driven automation of case management and communication, ensuring affordable access to LegalTech "
  "solutions that streamline workflows, reduce missed deadlines and enhance client satisfaction."),
P("The vision is Specific (targets small law firms), Measurable (25% efficiency improvement), "
  "Action-orientated (uses AI automation, calendar integration and client portals), Realistic (builds on "
  "existing technologies) and Time-based (December 2026)."),
P("<b>Goal 1:</b> Automate extraction of key case details from 90% of uploaded legal PDFs by September "
  "2026. Specific: build an AI module that scans legal PDFs to identify case dates, parties and "
  "summaries. Measurable: at least 90% extraction accuracy. Action-orientated: train NLP models on "
  "legal datasets. Realistic: existing NLP techniques. Time-based: September 2026."),
P("<b>Goal 2:</b> Reduce missed deadlines by 40% through Google Calendar synchronisation by December "
  "2026. Specific: automatically sync case deadlines and reminders. Measurable: 40% fewer missed "
  "deadlines. Action-orientated: automated scheduling and notifications. Realistic: calendar APIs are "
  "widely available. Time-based: December 2026."),
P("<b>Goal 3:</b> Improve client communication satisfaction scores by 25% through AI-generated case "
  "summaries by December 2026. Specific: provide clients with simplified case updates through a secure "
  "portal. Measurable: 25% higher satisfaction scores. Action-orientated: deploy AI summaries and "
  "real-time updates in the client portal. Realistic: builds on web technologies and AI summarisation. "
  "Time-based: December 2026."),
sub("3.2", "Project Deliverables"),
]
t = Table([
    ["Title", "Description", "Notes"],
    ["AI Summarisation Module", "Extracts and summarises legal documents into three paragraphs", "Uses NLP and ML models"],
    ["Calendar Integration", "Syncs deadlines with Google Calendar via API", "Ensures timely reminders"],
    ["Central Legal Database", "Stores structured case information", "Secure, cloud-based"],
    ["Client Portal", "Provides simplified case updates", "Accessible via web/mobile/desktop"],
    ["Analytics Dashboard", "Tracks efficiency improvements", "For firm management"],
], colWidths=[4.2*cm, 6.4*cm, 4.4*cm], repeatRows=1)
t.setStyle(TableStyle([
    ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 10),
    ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
    ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
    ("TOPPADDING", (0,0), (-1,-1), 4), ("BOTTOMPADDING", (0,0), (-1,-1), 4),
]))
story += [t, Spacer(1, 8),
sub("3.3", "Timeframe"),
P("The project follows the phases: Initiation (4 weeks) — develop the business case and feasibility "
  "study; Planning (8 weeks) — create resource, financial and risk plans and define scope; Execution "
  "(12 weeks) — train AI models, integrate APIs, set up the database, build the client portal and "
  "conduct stage-gate reviews; Closure (4 weeks) — testing, client feedback, final deployment and "
  "project completion review. Delivering within this timeline matters because the business benefit "
  "(reduced missed deadlines, higher satisfaction) compounds the sooner the system is in use."),
sub("3.4", "Resources"),
]
t2 = Table([
    ["Type", "Quantity", "Notes"],
    ["AI Engineers", "2", "Model training and NLP"],
    ["Legal Consultant", "1", "Domain expertise"],
    ["Software Developers", "2", "API and database integration"],
    ["Project Manager", "1", "Oversees coordination"],
    ["Tools", "—", "Cloud hosting, TensorFlow/PyTorch"],
    ["Supplies", "—", "Legal datasets for training AI models"],
], colWidths=[4.2*cm, 3.0*cm, 7.8*cm], repeatRows=1)
t2.setStyle(TableStyle([
    ("FONTNAME", (0,0), (-1,-1), FONT), ("FONTSIZE", (0,0), (-1,-1), 10),
    ("FONTNAME", (0,0), (-1,0), "Calibri-Bold"), ("BACKGROUND", (0,0), (-1,0), HexColor("#eef1f7")),
    ("GRID", (0,0), (-1,-1), 0.4, HexColor("#cccccc")),
    ("TOPPADDING", (0,0), (-1,-1), 4), ("BOTTOMPADDING", (0,0), (-1,-1), 4),
]))
story += [t2, Spacer(1, 8),
sub("3.5", "Success Criteria"),
P("The success of the project will be measured against five criteria: (1) AI extracts accurate case "
  "details with over 85% precision; (2) deadlines are synced automatically with less than 1% error rate; "
  "(3) case summaries reduce lawyer review time by 20%; (4) client satisfaction scores improve by at "
  "least 25%; and (5) the system is adopted by at least five pilot law firms within six months.")]

# 4. Conclusion
story += [heading("4", "Conclusion"),
P("This project directly addresses the business problem by improving communication and efficiency in "
  "small law firms. It seizes the business opportunity by developing affordable LegalTech solutions. By "
  "integrating AI with existing tools such as Google Calendar and a central legal database, the system "
  "will deliver measurable improvements in efficiency, client satisfaction and market competitiveness. "
  "The LegalTech ERP system is a scalable, innovative and practical solution that aligns with modern "
  "ICT trends and provides real-world impact for independent law firms.")]

refs = [
 "Eri, T. 2017. Service-Oriented Architecture: Analysis and Design for Services and Microservices. 2nd ed. Prentice Hall.",
 "Hall, G. 2017. Adaptive Code: Agile coding with design patterns and SOLID principles. 2nd ed. Microsoft Press.",
 "Schach, R. 2011. Object-Oriented and Classical Software Engineering. 8th ed. New York: McGraw-Hill.",
 "Tshwane University of Technology. 2026. ISJ107V Integrated Software Project study guide. Pretoria: Department of Computer Science.",
 "Sommerville, I. Software Engineering. 10th ed. Pearson. [Online] Available at: http://www.SoftwareEngineering-9.com [Accessed: 2026].",
]

build("Assignment1_Project_Proposal.pdf", "Assignment 1 — Project Proposal",
      "Legal Case Management ERP for Small Law Firms", story, refs,
      [("1", "Executive Summary"), ("2", "Background"),
       ("3", "Proposal"), ("4", "Conclusion"), ("", "References")])
