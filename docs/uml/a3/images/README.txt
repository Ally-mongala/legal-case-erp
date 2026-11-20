ISJ107V ASSIGNMENT 3 - ALL DIAGRAMS
Ally Mongala | 219361629 | LegalCase ERP
===========================================================================

FIGURES (numbered exactly as they appear in the assignment document)

  Figure_01_System_Architecture              Q3.1  (2 marks)
  Figure_02_Use_Case_Diagram                 Q3.2  (3 marks)
  Figure_03_Class_Diagram                    Q3.3  (3 marks)
  Figure_04_Sequence_Diagram                 Q3.4  (3 marks)
  Figure_05_Activity_Diagram                 Q3.5  (2 marks)
  Figure_06_Component_Diagram                Q3.6  (3 marks, part 1 of 2)
  Figure_07_Deployment_Diagram               Q3.6  (3 marks, part 2 of 2)
  Figure_08_State_Machine_Diagram            Q3.6  supporting model
  Figure_09_Entity_Relationship_Diagram      Q4.2  (3 marks)

FORMATS

  .png   300 DPI raster - insert into Word / PowerPoint, or upload to EC
  .svg   vector - infinite zoom, smallest files, best for on-screen marking
  ISJ107V_A3_All_Diagrams.pdf
         all nine figures, one per A4 landscape page, with captions

VERIFICATION

  All nine renders were checked for clipping: none hit the PlantUML
  4096 px default output cap and all have clean (non-touching) margins.

EDITABLE SOURCES

  Assignment 5 requires "editable UML files". They are:
     docs/uml/a3/a3-architecture.puml
     docs/uml/a3/a3-component.puml
     docs/uml/a3/a3-deployment.puml
     docs/uml/a3/a3-erd.puml
     docs/uml/a3/a3-state.puml
     docs/uml/a2/a2-usecase.puml
     docs/uml/a2/a2-class.puml
     docs/uml/a2/a2-sequence.puml
     docs/uml/a2/a2-activity.puml

REGENERATE

  cd docs/uml
  plantuml -DPLANTUML_LIMIT_SIZE=32768 -tpng -Sdpi=300 a3/*.puml -o images
  plantuml -DPLANTUML_LIMIT_SIZE=32768 -tsvg           a3/*.puml -o images

  The -DPLANTUML_LIMIT_SIZE flag is required: without it PlantUML silently
  crops large diagrams at 4096 px (this previously truncated a swimlane
  from the activity diagram).
