ISJ107V Assignment 4 - Question 3 diagrams (LegalCase ERP)
Ally Mongala - 219361629

Files
-----
Figure_A4_01_Use_Case_Diagram            Q3.1  actors and demonstrated use cases (Fig. 3 in the report)
Figure_A4_02_Activity_Diagram            Q3.2  demonstrated workflow incl. emerging-tech step (Fig. 4)
Figure_A4_03_Class_Diagram_Domain        Q3.3  domain classes, associations, multiplicities (Fig. 5)
Figure_A4_04_Class_Diagram_Services_and_AI  Q3.3  services, security and AI classes (Fig. 6)
Figure_A4_05_Sequence_Diagram            Q3.4  UI -> logic -> data service -> AI component (Fig. 7)
Figure_A4_06_Component_Diagram           Q3.5  prototype modules (Fig. 8)
Figure_A4_07_Deployment_Diagram          Q3.5  execution environment (Fig. 9)
ISJ107V_A4_All_Diagrams.pdf              all seven diagrams in one PDF, for enlarged viewing

Formats: each figure is supplied as PNG (print), SVG (scalable, for the report or re-use) and appears
in the combined PDF.

Sources: ../*.puml (PlantUML text). Regenerate from this folder with:
    java -DPLANTUML_LIMIT_SIZE=32768 -jar plantuml.jar -tpng -charset UTF-8 ../a4-usecase.puml
(repeat for a4-activity, a4-class-domain, a4-class-services, a4-sequence, a4-component, a4-deployment)

Note on the design: these models describe the PROTOTYPE AS DEMONSTRATED, not the larger Assignment 3
design. The three differences are documented in section 3.5 of the report (AI component in-process
instead of a separate service; H2 file database and a local folder instead of managed PostgreSQL and
object storage; one JAR on localhost instead of three containers behind nginx with TLS).
