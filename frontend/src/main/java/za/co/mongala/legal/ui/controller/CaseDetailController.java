package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.DocumentWorkflow;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Case detail (FXML: fxml/views/case_detail.fxml) - the heart of the lawyer
 * workflow. This single screen implements the human-in-the-loop AI pipeline:
 *
 *  FR1  upload a legal PDF against the case
 *  FR2  view AI-extracted fields with confidence scores and source spans
 *  FR3  view/edit the three-paragraph AI summary
 *  FR4  sync deadlines to Google Calendar
 *  FR6  one-click review and editing of summaries (human-in-the-loop)
 *  FR7  retry failed document processing
 *  ETR2 explainability: the exact span used for each extraction is shown
 *  ETR5 a confidence score accompanies every extracted field
 */
public class CaseDetailController {

    private final AppContext ctx;
    private final Case legalCase;

    @FXML private Label caseRefLabel;
    @FXML private Label caseTitleLabel;
    @FXML private Label statusBadge;
    @FXML private Label metaClient;
    @FXML private Label metaOpposing;
    @FXML private Label metaCourt;
    @FXML private Label metaMatter;
    @FXML private Label metaOpened;
    @FXML private Label metaAmount;
    @FXML private Button backButton;
    @FXML private ComboBox<CaseStatus> statusBox;
    @FXML private Button applyStatusButton;
    @FXML private Button editCaseButton;
    @FXML private Button deleteCaseButton;

    @FXML private TableView<LegalDocument> docTable;
    @FXML private TableColumn<LegalDocument, String> docNameCol;
    @FXML private TableColumn<LegalDocument, String> docSizeCol;
    @FXML private TableColumn<LegalDocument, String> docUploadCol;
    @FXML private TableColumn<LegalDocument, LegalDocument.ProcessingStatus> docStatusCol;
    @FXML private TableColumn<LegalDocument, LegalDocument> docActionCol;
    @FXML private Button uploadButton;
    @FXML private ProgressBar uploadProgress;
    @FXML private Label processingLabel;

    @FXML private ComboBox<LegalDocument> docCombo;
    @FXML private Label extractionSourceLabel;
    @FXML private TableView<Extraction> extractionTable;
    @FXML private TableColumn<Extraction, String> fieldCol;
    @FXML private TableColumn<Extraction, String> valueCol;
    @FXML private TableColumn<Extraction, Extraction> confCol;
    @FXML private TableColumn<Extraction, String> spanCol;
    @FXML private TableColumn<Extraction, Integer> pageCol;

    @FXML private TextArea summaryP1;
    @FXML private TextArea summaryP2;
    @FXML private TextArea summaryP3;
    @FXML private Label summaryStatusChip;
    @FXML private Label summaryVersionLabel;
    @FXML private Label summaryMetaLabel;
    @FXML private Button saveDraftButton;
    @FXML private Button approveButton;

    @FXML private TableView<Deadline> deadlineTable;
    @FXML private TableColumn<Deadline, String> dlTitleCol;
    @FXML private TableColumn<Deadline, String> dlDateCol;
    @FXML private TableColumn<Deadline, Long> dlDaysCol;
    @FXML private TableColumn<Deadline, Integer> dlReminderCol;
    @FXML private TableColumn<Deadline, Boolean> dlSyncCol;
    @FXML private TableColumn<Deadline, Deadline> dlActionCol;
    @FXML private Button syncAllButton;
    @FXML private Button unsyncAllButton;
    @FXML private Button exportCaseIcsButton;
    @FXML private Button addDeadlineButton;

    @FXML private VBox messagesBox;
    @FXML private Label msgUnreadLabel;
    @FXML private TextField msgSubjectField;
    @FXML private TextArea msgBodyArea;
    @FXML private Button msgSendButton;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");
    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public CaseDetailController(AppContext ctx, Case legalCase) {
        this.ctx = ctx;
        this.legalCase = legalCase;
    }

    @FXML
    private void initialize() {
        renderHeader();
        setupDocTable();
        setupExtractionTable();
        setupDeadlineTable();

        backButton.setOnAction(e -> ctx.app().showView("cases", null));
        uploadButton.setOnAction(e -> onUpload());

        statusBox.getItems().addAll(CaseStatus.values());
        statusBox.setValue(legalCase.getStatus());
        applyStatusButton.setOnAction(e -> onApplyStatus());
        editCaseButton.setOnAction(e -> onEditCase());
        deleteCaseButton.setOnAction(e -> onDeleteCase());

        docCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LegalDocument d) { return d == null ? "" : d.getFileName(); }
            @Override public LegalDocument fromString(String s) { return null; }
        });
        docCombo.valueProperty().addListener((obs, o, n) -> showExtractionFor(n));

        saveDraftButton.setOnAction(e -> onSaveDraft());
        approveButton.setOnAction(e -> onApprove());
        syncAllButton.setOnAction(e -> onSyncAll());
        addDeadlineButton.setOnAction(e -> onAddDeadline());
        unsyncAllButton.setOnAction(e -> onUnsyncAll());
        exportCaseIcsButton.setOnAction(e -> onExportCaseIcs());
        msgSendButton.setOnAction(e -> onSendLawyerMessage());
        msgBodyArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) onSendLawyerMessage();
        });

        refreshDocuments();
        refreshDeadlines();
        refreshMessages();
        // mark client->lawyer messages as read once the lawyer opens the case chat
        try {
            ctx.dataStore().markCaseReadByLawyer(legalCase.getId());
            refreshMessages();
        } catch (Exception ignored) {}
    }

    /* ------------------------------------------------------------------
     * Header
     * ------------------------------------------------------------------ */

    private void renderHeader() {
        caseRefLabel.setText(legalCase.getReference());
        caseTitleLabel.setText(legalCase.getTitle());
        statusBadge.setText(legalCase.getStatus().getDisplayName().toUpperCase());
        statusBadge.setStyle("-fx-background-color: " + legalCase.getStatus().getHexColor() + "; -fx-text-fill: white;");
        metaClient.setText(legalCase.getClientName());
        metaOpposing.setText(legalCase.getOpposingParty());
        metaCourt.setText(legalCase.getCourt());
        metaMatter.setText(legalCase.getMatterType());
        metaOpened.setText(legalCase.getOpenedDate().format(D_FMT));
        metaAmount.setText(legalCase.getAmountInvolved() > 0 ? legalCase.getAmountFormatted() : "Unquantified");
    }

    /* ------------------------------------------------------------------
     * Documents (FR1 / FR7)
     * ------------------------------------------------------------------ */

    private void setupDocTable() {
        docNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        docSizeCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getSizeKb() + " KB"));
        docUploadCol.setCellValueFactory(d ->
                new ReadOnlyStringWrapper(d.getValue().getUploadedAt().format(DT_FMT) + " by " + d.getValue().getUploadedBy()));
        docStatusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getProcessingStatus()));
        docStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LegalDocument.ProcessingStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                Label chip = new Label(switch (s) {
                    case PROCESSED -> "PROCESSED";
                    case PROCESSING -> "PROCESSING";
                    case FAILED -> "FAILED";
                    case REJECTED -> "BLOCKED";
                });
                chip.getStyleClass().addAll("chip",
                        s == LegalDocument.ProcessingStatus.PROCESSED ? "chip-green"
                                : s == LegalDocument.ProcessingStatus.PROCESSING ? "chip-blue" : "chip-red");
                setGraphic(chip);
            }
        });
        docActionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        docActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            @Override
            protected void updateItem(LegalDocument doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) { setGraphic(null); return; }
                if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.FAILED) {
                    btn.setText("Retry AI");
                    btn.getStyleClass().add("btn-ghost");
                    btn.setOnAction(e -> onRetry(doc));
                } else if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED) {
                    btn.setText("Read full");
                    btn.getStyleClass().add("btn-ghost");
                    btn.setOnAction(e -> onReadFullText(doc));
                } else if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.REJECTED) {
                    btn.setText("Review AI");
                    btn.getStyleClass().add("btn-ghost");
                    btn.setOnAction(e -> {
                        docCombo.setValue(doc);
                        showExtractionFor(doc);
                        Toast.info(ctx, "Blocked by AI validation: "
                                + (doc.getRejectionReason() == null ? "document does not match the case"
                                        : doc.getRejectionReason()));
                    });
                } else {
                    btn.setText("View");
                    btn.getStyleClass().add("btn-ghost");
                    btn.setOnAction(e -> Toast.info(ctx, "Processing in progress..."));
                }
                HBox box = new HBox(6);
                Button del = new Button("Delete");
                del.getStyleClass().add("btn-ghost");
                del.setOnAction(e -> onDeleteDocument(doc));
                box.getChildren().addAll(btn, del);
                setGraphic(box);
            }
        });
    }

    private void refreshDocuments() {
        List<LegalDocument> docs = ctx.dataStore().documentsForCase(legalCase.getId());
        docTable.getItems().setAll(docs);

        // refresh the processed-document selector used by the extraction panel
        LegalDocument previouslySelected = docCombo.getValue();
        List<LegalDocument> processed = docs.stream()
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED
                        || d.getProcessingStatus() == LegalDocument.ProcessingStatus.REJECTED)
                .sorted(Comparator.comparing(LegalDocument::getUploadedAt).reversed())
                .toList();
        docCombo.getItems().setAll(processed);
        if (previouslySelected != null && processed.contains(previouslySelected)) {
            docCombo.setValue(previouslySelected);
        } else if (!processed.isEmpty()) {
            docCombo.setValue(processed.get(0));
        } else {
            showExtractionFor(null);
        }
    }

    /** Reads the full extracted text of a document and shows it in a dialog. */
    private void onReadFullText(LegalDocument doc) {
        try {
            String text = ctx.dataStore().documentContent(doc.getId());
            if (text == null || text.isBlank()) {
                Toast.error(ctx, "No extracted text available for this document.");
                return;
            }
            TextArea area = new TextArea(text);
            area.setEditable(false);
            area.setWrapText(false);
            area.setPrefSize(760, 520);
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Full document text - " + doc.getFileName());
            dialog.getDialogPane().setContent(area);
            dialog.getDialogPane().setPrefSize(800, 580);
            dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (Exception ex) {
            Toast.error(ctx, "Could not read document: " + ex.getMessage());
        }
    }

    /** Deletes an uploaded document (and its AI extraction/summary). */
    private void onDeleteDocument(LegalDocument doc) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete document");
        confirm.setHeaderText("Delete \"" + doc.getFileName() + "\"?");
        confirm.setContentText("The file and its AI extraction will be permanently removed.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                try {
                    ctx.dataStore().deleteDocument(doc.getId());
                    ctx.auditService().warning(ctx.currentUser().getName(), "DOCUMENT_DELETED",
                            "Deleted document " + doc.getFileName());
                    refreshDocuments();
                    Toast.success(ctx, "Document deleted.");
                } catch (Exception ex) {
                    Toast.error(ctx, "Could not delete document: " + ex.getMessage());
                }
            }
        });
    }

    private void onUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload legal document against " + legalCase.getReference());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Legal documents (PDF / Word)",
                "*.pdf", "*.docx", "*.doc"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word documents", "*.docx", "*.doc"));
        File file = chooser.showOpenDialog(uploadButton.getScene().getWindow());
        if (file == null) return;

        // FR11: POPIA consent control before processing
        Dialog<Boolean> consentDialog = new Dialog<>();
        consentDialog.setTitle("Client consent (POPIA)");
        consentDialog.setHeaderText("Confirm consent before processing \"" + file.getName() + "\"");
        CheckBox consentBox = new CheckBox(
                "I confirm the client has consented to their data being processed (POPIA / FR11).");
        consentBox.setSelected(true);
        consentDialog.getDialogPane().setContent(consentBox);
        consentDialog.getDialogPane().getButtonTypes().addAll(
                new javafx.scene.control.ButtonType("Upload & process", javafx.scene.control.ButtonBar.ButtonData.OK_DONE),
                javafx.scene.control.ButtonType.CANCEL);
        consentDialog.setResultConverter(btn -> btn.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE
                ? consentBox.isSelected() : null);
        Boolean consent = consentDialog.showAndWait().orElse(null);
        if (consent == null) return; // cancelled
        if (!consent) {
            Toast.error(ctx, "Consent is required before the document can be processed (POPIA).");
            return;
        }
        runAiProcessing(file, null, consent);
    }

    private void onRetry(LegalDocument doc) {
        runAiProcessing(null, doc, doc.isConsentGranted());
    }

    /**
     * Runs the document workflow (FR1 -> FR2/FR3/FR4). In DEMO mode the
     * simulated NLP worker runs locally; in CONNECTED mode the file is
     * uploaded and the backend worker pipeline is polled until PROCESSED.
     */
    private void runAiProcessing(File file, LegalDocument existingDoc, boolean consent) {
        uploadProgress.setVisible(true);
        uploadProgress.setProgress(0);
        processingLabel.setVisible(true);
        processingLabel.setManaged(true);

        DocumentWorkflow.ProgressListener progress = (fraction, message) -> {
            uploadProgress.setProgress(fraction);
            processingLabel.setText(message);
        };
        DocumentWorkflow.ResultListener done = new DocumentWorkflow.ResultListener() {
            @Override
            public void onProcessed(LegalDocument doc) {
                uploadProgress.setProgress(1);
                uploadProgress.setVisible(false);
                processingLabel.setVisible(false);
                processingLabel.setManaged(false);
                refreshDocuments();
                // AI read and processed the document immediately - select it so the
                // extraction and summary appear right away
                docCombo.setValue(doc);
                showExtractionFor(doc);
                Toast.success(ctx, "AI processed \"" + doc.getFileName() + "\" - see the extraction and summary below.");
            }

            @Override
            public void onRejected(LegalDocument doc, String reason) {
                uploadProgress.setProgress(1);
                uploadProgress.setVisible(false);
                processingLabel.setVisible(false);
                processingLabel.setManaged(false);
                refreshDocuments();
                docCombo.setValue(doc);
                showExtractionFor(doc);
                Toast.error(ctx, "Document BLOCKED: " + reason + " - see the highlighted extraction below.");
            }

            @Override
            public void onFailed(LegalDocument doc, String reason) {
                uploadProgress.setVisible(false);
                processingLabel.setVisible(false);
                processingLabel.setManaged(false);
                refreshDocuments();
                Toast.error(ctx, "AI could not process \"" + doc.getFileName() + "\" (" + reason
                        + "). Use Retry AI to queue it again.");
            }
        };

        if (file != null) {
            ctx.workflow().processUpload(file, legalCase, consent, progress, done);
        } else if (existingDoc != null) {
            ctx.workflow().retryProcessing(existingDoc, legalCase, progress, done);
        }
        refreshDocuments();
    }

    /* ------------------------------------------------------------------
     * AI extraction (FR2 / ETR2 / ETR5)
     * ------------------------------------------------------------------ */

    private void setupExtractionTable() {
        fieldCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getFieldName()));
        valueCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getFieldValue()));
        confCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        confCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Extraction ex, boolean empty) {
                super.updateItem(ex, empty);
                if (empty || ex == null) { setGraphic(null); return; }
                ProgressBar bar = new ProgressBar(ex.getConfidence());
                bar.setPrefWidth(120);
                String color = ex.getConfidence() >= 0.90 ? "#2e7d32" : ex.getConfidence() >= 0.75 ? "#e65100" : "#c62828";
                bar.setStyle("-fx-accent: " + color + ";");
                Label pct = new Label(ex.getConfidencePercent());
                pct.getStyleClass().add("row-sub");
                HBox box = new HBox(8, bar, pct);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        spanCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(truncate(d.getValue().getSpanText(), 64)));
        pageCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getPageNumber()));
    }

    private void showExtractionFor(LegalDocument doc) {
        if (doc == null) {
            extractionSourceLabel.setText("No processed document selected yet - upload a PDF to run AI extraction.");
            extractionTable.getItems().clear();
            showSummary(null);
            return;
        }
        extractionSourceLabel.setText("Source: " + doc.getFileName()
                + "  \u00B7  each field shows its confidence and the text the model used");
        extractionTable.getItems().setAll(ctx.dataStore().extractionsForDocument(doc.getId()));

        Summary summary = ctx.dataStore().summariesForCase(legalCase.getId()).stream()
                .filter(s -> s.getDocumentId().equals(doc.getId()))
                .findFirst().orElse(null);
        showSummary(summary);
    }

    /* ------------------------------------------------------------------
     * Summary (FR3 / FR6 / ETR4)
     * ------------------------------------------------------------------ */

    private Summary activeSummary;

    private void showSummary(Summary summary) {
        activeSummary = summary;
        if (summary == null) {
            summaryP1.setText(""); summaryP2.setText(""); summaryP3.setText("");
            summaryStatusChip.setText("NO SUMMARY");
            summaryStatusChip.getStyleClass().setAll("chip", "chip-gray");
            summaryVersionLabel.setText("");
            summaryMetaLabel.setText("Generate a summary by processing a document above.");
            approveButton.setDisable(true);
            saveDraftButton.setDisable(true);
            return;
        }
        summaryP1.setText(summary.getParagraph1());
        summaryP2.setText(summary.getParagraph2());
        summaryP3.setText(summary.getParagraph3());
        summaryVersionLabel.setText("Version " + summary.getVersion()
                + "  \u00B7  generated by AI NLP Worker on " + summary.getCreatedAt().format(D_FMT));
        summaryMetaLabel.setText("Edit the draft, then approve to publish it to the client portal.");
        boolean approved = summary.getStatus() == Summary.SummaryStatus.APPROVED;
        summaryStatusChip.setText(approved ? "APPROVED" : "DRAFT");
        summaryStatusChip.getStyleClass().setAll("chip", approved ? "chip-green" : "chip-amber");
        approveButton.setDisable(approved);
        approveButton.setText(approved ? "Approved \u2713" : "Approve & notify client");
        saveDraftButton.setDisable(false);
    }

    private void onSaveDraft() {
        if (activeSummary == null) return;
        activeSummary.setParagraph1(summaryP1.getText());
        activeSummary.setParagraph2(summaryP2.getText());
        activeSummary.setParagraph3(summaryP3.getText());
        Summary refreshed = ctx.workflow().saveSummaryDraft(activeSummary);
        if (refreshed != null) activeSummary = refreshed;
        Toast.success(ctx, "Draft saved. Approve it when you are happy for the client to see it.");
    }

    private void onApprove() {
        if (activeSummary == null) return;
        activeSummary.setParagraph1(summaryP1.getText());
        activeSummary.setParagraph2(summaryP2.getText());
        activeSummary.setParagraph3(summaryP3.getText());
        Summary refreshed = ctx.workflow().approveSummary(activeSummary, legalCase);
        if (refreshed != null) activeSummary = refreshed;
        showSummary(activeSummary);
        refreshMessages();
        Toast.success(ctx, "Summary approved and published to the client portal.");
    }

    /* ------------------------------------------------------------------
     * Deadlines / Google Calendar (FR4 / US5)
     * ------------------------------------------------------------------ */

    private void setupDeadlineTable() {
        dlTitleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        dlDateCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getDate().format(D_FMT)));
        dlDaysCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().daysLeft()));
        dlDaysCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) { setGraphic(null); return; }
                Label l = new Label(days < 0 ? (days + " days overdue") : days + " days");
                l.getStyleClass().addAll("chip", days < 0 ? "chip-red" : days <= 7 ? "chip-amber" : "chip-blue");
                setGraphic(l);
            }
        });
        dlReminderCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getReminderDays()));
        dlSyncCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().isSynced()));
        dlSyncCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean synced, boolean empty) {
                super.updateItem(synced, empty);
                if (empty || synced == null) { setGraphic(null); return; }
                Label l = new Label(synced ? "SYNCED \u2713" : "PENDING");
                l.getStyleClass().addAll("chip", synced ? "chip-green" : "chip-gray");
                setGraphic(l);
            }
        });
        dlActionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        dlActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Sync");
            @Override
            protected void updateItem(Deadline d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) { setGraphic(null); return; }
                btn.getStyleClass().setAll("btn-ghost");
                btn.setDisable(false);
                btn.setText(d.isSynced() ? "Unsync" : "Sync now");
                btn.setOnAction(e -> {
                    if (d.isSynced()) onUnsyncOne(d); else onSyncOne(d);
                });
                setGraphic(btn);
            }
        });
    }

    private void refreshDeadlines() {
        List<Deadline> ds = ctx.dataStore().deadlinesForCase(legalCase.getId());
        deadlineTable.getItems().setAll(ds);
    }

    /** Change the case status (lawyer) - the client is alerted. */
    private void onApplyStatus() {
        CaseStatus newStatus = statusBox.getValue();
        if (newStatus == null || newStatus == legalCase.getStatus()) return;
        try {
            Case updated = ctx.dataStore().updateCaseStatus(legalCase.getId(), newStatus);
            if (updated != null) {
                renderHeader();
                ctx.auditService().success(ctx.currentUser().getName(), "CASE_STATUS_CHANGED",
                        "Case " + legalCase.getReference() + " status -> " + newStatus.getDisplayName()
                                + " - client notified");
                refreshMessages();
                Toast.success(ctx, "Status changed to " + newStatus.getDisplayName() + " - client notified.");
            }
        } catch (Exception ex) {
            Toast.error(ctx, "Could not change status: " + ex.getMessage());
        }
    }

    /** Edit case details (lawyer) - the client is alerted. */
    private void onEditCase() {
        Dialog<Case> dialog = new Dialog<>();
        dialog.setTitle("Update case");
        dialog.setHeaderText("Update " + legalCase.getReference());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new javafx.geometry.Insets(16));
        TextField title = new TextField(legalCase.getTitle());
        TextField opposing = new TextField(legalCase.getOpposingParty());
        TextField matter = new TextField(legalCase.getMatterType());
        TextField court = new TextField(legalCase.getCourt());
        TextArea desc = new TextArea(legalCase.getDescription());
        desc.setPrefRowCount(3);
        TextField amount = new TextField(String.valueOf(legalCase.getAmountInvolved()));
        grid.addRow(0, new Label("Case title"), title);
        grid.addRow(1, new Label("Opposing party"), opposing);
        grid.addRow(2, new Label("Matter type"), matter);
        grid.addRow(3, new Label("Court"), court);
        grid.addRow(4, new Label("Description"), desc);
        grid.addRow(5, new Label("Amount involved"), amount);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new javafx.scene.control.ButtonType("Save changes", javafx.scene.control.ButtonBar.ButtonData.OK_DONE),
                javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn.getButtonData() != javafx.scene.control.ButtonBar.ButtonData.OK_DONE) return null;
            double amt;
            try { amt = Double.parseDouble(amount.getText()); } catch (Exception ex) { amt = legalCase.getAmountInvolved(); }
            return new Case(legalCase.getId(), legalCase.getReference(), title.getText(), legalCase.getClientId(),
                    legalCase.getClientName(), opposing.getText(), legalCase.getStatus(), matter.getText(),
                    court.getText(), desc.getText(), legalCase.getOpenedDate(), amt);
        });
        dialog.showAndWait().ifPresent(updated -> {
            try {
                ctx.dataStore().updateCase(updated);
                ctx.auditService().success(ctx.currentUser().getName(), "CASE_UPDATED",
                        "Case " + legalCase.getReference() + " updated - client notified");
                ctx.app().showView("case_detail", updated);
                Toast.success(ctx, "Case updated - the client has been notified.");
            } catch (Exception ex) {
                Toast.error(ctx, "Could not update case: " + ex.getMessage());
            }
        });
    }

    /** Delete the case (lawyer) with confirmation. */
    private void onDeleteCase() {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete case");
        confirm.setHeaderText("Delete " + legalCase.getReference() + "?");
        confirm.setContentText("This permanently removes the case and all its documents, deadlines and messages. "
                + "This action cannot be undone.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                try {
                    ctx.dataStore().deleteCase(legalCase.getId());
                    ctx.auditService().warning(ctx.currentUser().getName(), "CASE_DELETED",
                            "Case " + legalCase.getReference() + " deleted with all records");
                    Toast.success(ctx, "Case " + legalCase.getReference() + " deleted.");
                    ctx.app().showView("cases", null);
                } catch (Exception ex) {
                    Toast.error(ctx, "Could not delete case: " + ex.getMessage());
                }
            }
        });
    }

    /** Export this case's synced deadlines as .ics for the user's phone/computer calendar. */
    private void onExportCaseIcs() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save case calendar");
        chooser.setInitialFileName(legalCase.getReference() + "-calendar.ics");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Calendar files", "*.ics"));
        java.io.File target = chooser.showSaveDialog(exportCaseIcsButton.getScene().getWindow());
        if (target == null) return;
        try {
            String ics;
            if (ctx.isRemote()) {
                byte[] bytes = ctx.api().downloadBytes("/api/cases/" + legalCase.getId() + "/calendar.ics");
                ics = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                StringBuilder sb = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//LegalCase ERP//EN\r\n");
                ctx.dataStore().deadlinesForCase(legalCase.getId()).stream().filter(Deadline::isSynced).forEach(d ->
                        sb.append("BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:").append(d.getDate())
                          .append("\r\nSUMMARY:").append(d.getTitle()).append("\r\nEND:VEVENT\r\n"));
                sb.append("END:VCALENDAR\r\n");
                ics = sb.toString();
            }
            java.nio.file.Files.writeString(target.toPath(), ics);
            ctx.auditService().info(ctx.currentUser().getName(), "CALENDAR_EXPORT",
                    "Exported case calendar for " + legalCase.getReference());
            Toast.success(ctx, "Case calendar saved - open it with your phone/computer calendar app.");
        } catch (Exception ex) {
            Toast.error(ctx, "Could not export calendar: " + ex.getMessage());
        }
    }

    /** Unsync all deadlines on this case. */
    private void onUnsyncAll() {
        List<Deadline> ds = ctx.dataStore().deadlinesForCase(legalCase.getId());
        String receipt = ctx.workflow().unsyncAllDeadlines(ds);
        refreshDeadlines();
        Toast.success(ctx, receipt);
    }

    /** Lawyer sends a message to the client from the case chat. */
    private void onSendLawyerMessage() {
        String body = msgBodyArea.getText();
        if (body == null || body.isBlank()) return;
        try {
            ctx.workflow().sendLawyerMessage(legalCase, msgSubjectField.getText(), body.trim());
            msgSubjectField.clear();
            msgBodyArea.clear();
            refreshMessages();
            Toast.success(ctx, "Message sent to the client.");
        } catch (Exception ex) {
            Toast.error(ctx, "Could not send message: " + ex.getMessage());
        }
    }

    /** FR4: manually capture a deadline with a configurable reminder (7/3/1 days). */
    private void onAddDeadline() {
        Dialog<Deadline> dialog = new Dialog<>();
        dialog.setTitle("Add deadline");
        dialog.setHeaderText("Capture a deadline for " + legalCase.getReference());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new javafx.geometry.Insets(16));

        TextField title = new TextField();
        title.setPromptText("e.g. File discovery affidavit");
        DatePicker datePicker = new DatePicker(java.time.LocalDate.now().plusDays(7));
        ComboBox<Deadline.DeadlineType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(Deadline.DeadlineType.values());
        typeBox.setValue(Deadline.DeadlineType.OTHER);
        ComboBox<Integer> reminderBox = new ComboBox<>();
        reminderBox.getItems().addAll(1, 3, 7, 14);
        reminderBox.setValue(7);

        grid.addRow(0, new Label("Title *"), title);
        grid.addRow(1, new Label("Date *"), datePicker);
        grid.addRow(2, new Label("Type"), typeBox);
        grid.addRow(3, new Label("Reminder (days before)"), reminderBox);
        dialog.getDialogPane().setContent(grid);

        javafx.scene.control.ButtonType add =
                new javafx.scene.control.ButtonType("Add deadline", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(add, javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(btn -> {
            if (btn != add || title.getText() == null || title.getText().isBlank()
                    || datePicker.getValue() == null) return null;
            return new Deadline("dl" + System.currentTimeMillis(), legalCase.getId(), legalCase.getReference(),
                    title.getText().trim(), datePicker.getValue(), typeBox.getValue(),
                    reminderBox.getValue() == null ? 7 : reminderBox.getValue(), false);
        });

        dialog.showAndWait().ifPresent(d -> {
            try {
                ctx.dataStore().addDeadline(d);
                ctx.auditService().info(ctx.currentUser().getName(), "DEADLINE_CREATED",
                        "Manual deadline \"" + d.getTitle() + "\" (" + d.getDate() + ") added - reminder "
                                + d.getReminderDays() + " days before");
                refreshDeadlines();
                Toast.success(ctx, "Deadline added: " + d.getTitle() + " (" + d.getDate() + ")");
            } catch (Exception ex) {
                Toast.error(ctx, "Could not add deadline: " + ex.getMessage());
            }
        });
    }

    private void onSyncOne(Deadline d) {
        String receipt = ctx.workflow().syncDeadline(d);
        refreshDeadlines();
        Toast.success(ctx, receipt);
    }

    private void onSyncAll() {
        List<Deadline> ds = ctx.dataStore().deadlinesForCase(legalCase.getId());
        String receipt = ctx.workflow().syncAllDeadlines(ds);
        refreshDeadlines();
        Toast.success(ctx, receipt);
    }

    private void onUnsyncOne(Deadline d) {
        String receipt = ctx.workflow().unsyncDeadline(d);
        refreshDeadlines();
        Toast.success(ctx, receipt);
    }

    /* ------------------------------------------------------------------
     * Client communication (FR5)
     * ------------------------------------------------------------------ */

    private void refreshMessages() {
        messagesBox.getChildren().clear();
        List<ClientMessage> msgs = ctx.dataStore().messagesForCase(legalCase.getId());
        if (msgs.isEmpty()) {
            messagesBox.getChildren().add(new Label("No messages yet on this matter."));
        } else {
            msgs.forEach(m -> {
                HBox row = new HBox(10);
                row.getStyleClass().add("list-row");

                Label from = new Label(m.isFromLawyer() ? "Firm" : "Client");
                from.getStyleClass().addAll("chip", m.isFromLawyer() ? "chip-blue" : "chip-gray");
                if (!m.isFromLawyer() && !m.isReadByLawyer()) {
                    Label unread = new Label("NEW");
                    unread.getStyleClass().addAll("chip", "chip-red");
                    row.getChildren().add(unread);
                }

                VBox mid = new VBox(2);
                Label subj = new Label(m.getSubject());
                subj.getStyleClass().add("row-title");
                Label body = new Label(m.getBody());
                body.getStyleClass().add("row-sub");
                body.setWrapText(true);
                mid.getChildren().addAll(subj, body);
                HBox.setHgrow(mid, javafx.scene.layout.Priority.ALWAYS);

                Label time = new Label(m.getSentAt().format(DT_FMT));
                time.getStyleClass().add("row-sub");

                row.getChildren().addAll(from, mid, time);
                messagesBox.getChildren().add(row);
            });
        }
        // unread badge in the messages header (alert for a new client message)
        long unread = msgs.stream().filter(m -> !m.isFromLawyer() && !m.isReadByLawyer()).count();
        if (unread > 0) {
            msgUnreadLabel.setText("\uD83D\uDCE8 " + unread + " new client message(s)");
            msgUnreadLabel.getStyleClass().addAll("chip", "chip-red");
            msgUnreadLabel.setVisible(true);
        } else {
            msgUnreadLabel.setText("");
            msgUnreadLabel.setVisible(false);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }
}
