package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.LegalDocument;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client documents screen (FXML: fxml/views/client_documents.fxml).
 *
 * Clients can browse the documents shared with them (FR5). Downloading is
 * simulated in the prototype; in production files stream from secure cloud
 * storage (AWS S3 / Azure Blob) with role-based access (NFR1).
 */
public class ClientDocumentsController {

    private final AppContext ctx;

    @FXML private TableView<LegalDocument> docTable;
    @FXML private TableColumn<LegalDocument, String> nameCol;
    @FXML private TableColumn<LegalDocument, String> caseCol;
    @FXML private TableColumn<LegalDocument, String> sharedCol;
    @FXML private TableColumn<LegalDocument, String> sizeCol;
    @FXML private TableColumn<LegalDocument, LegalDocument> actionCol;
    @FXML private Button shareButton;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    public ClientDocumentsController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        caseCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(caseRef(d.getValue().getCaseId())));
        sharedCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getUploadedAt().format(DT_FMT)));
        sizeCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getSizeKb() + " KB"));
        actionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        actionCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LegalDocument doc, boolean empty) {
                super.updateItem(doc, empty);
                if (empty || doc == null) { setGraphic(null); return; }
                Button dl = new Button("Download");
                dl.getStyleClass().add("btn-gold-small");
                dl.setOnAction(e -> download(doc));
                Button del = new Button("Delete");
                del.getStyleClass().add("btn-ghost");
                del.setOnAction(e -> deleteDoc(doc));
                HBox box = new HBox(6, dl, del);
                setGraphic(box);
            }
        });

        shareButton.setOnAction(e -> onShare());

        refresh();
    }

    private void refresh() {
        List<String> myCaseIds = ctx.dataStore().findCasesForClient(ctx.currentUser().getId())
                .stream().map(Case::getId).toList();
        List<LegalDocument> docs = myCaseIds.stream()
                .flatMap(id -> ctx.dataStore().documentsForCase(id).stream())
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED)
                .toList();
        docTable.getItems().setAll(docs);
    }

    private String caseRef(String caseId) {
        return ctx.dataStore().findCaseById(caseId).map(Case::getReference).orElse("—");
    }

    /** Client deletes one of their uploaded documents. */
    private void deleteDoc(LegalDocument doc) {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete document");
        confirm.setHeaderText("Delete \"" + doc.getFileName() + "\"?");
        confirm.setContentText("This will remove the document from the case.");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == javafx.scene.control.ButtonType.OK) {
                try {
                    ctx.dataStore().deleteDocument(doc.getId());
                    ctx.auditService().warning(ctx.currentUser().getName(), "DOCUMENT_DELETED",
                            "Client deleted document " + doc.getFileName());
                    refresh();
                    Toast.success(ctx, "Document deleted.");
                } catch (Exception ex) {
                    Toast.error(ctx, "Could not delete document: " + ex.getMessage());
                }
            }
        });
    }

    private void download(LegalDocument doc) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save document");
        chooser.setInitialFileName(doc.getFileName());
        java.io.File target = chooser.showSaveDialog(docTable.getScene().getWindow());
        if (target == null) return;
        try {
            byte[] bytes;
            if (ctx.isRemote()) {
                bytes = ctx.dataStore().downloadDocument(doc.getId());
            } else {
                // demo mode: write a placeholder so the user still gets a file
                bytes = ("LegalCase ERP demo download - " + doc.getFileName() + "\n\n"
                        + "In CONNECTED mode the real document (decrypted from storage) is saved here.").getBytes();
            }
            java.nio.file.Files.write(target.toPath(), bytes);
            ctx.auditService().info(ctx.currentUser().getName(), "DOCUMENT_DOWNLOAD",
                    "Client downloaded " + doc.getFileName());
            Toast.success(ctx, "Saved to " + target.getName());
        } catch (Exception ex) {
            Toast.error(ctx, "Could not download: " + ex.getMessage());
        }
    }

    /** Client shares a document with the lawyer (FR: share documents with the lawyer). */
    private void onShare() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Share a document with your lawyer");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.doc"));
        java.io.File file = chooser.showOpenDialog(shareButton.getScene().getWindow());
        if (file == null) return;

        // POPIA consent
        Dialog<Boolean> consentDialog = new Dialog<>();
        consentDialog.setTitle("Consent (POPIA)");
        consentDialog.setHeaderText("Share \"" + file.getName() + "\" with your lawyer?");
        CheckBox consent = new CheckBox("I consent to my document being shared and processed.");
        consent.setSelected(true);
        consentDialog.getDialogPane().setContent(consent);
        consentDialog.getDialogPane().getButtonTypes().addAll(
                new javafx.scene.control.ButtonType("Share", javafx.scene.control.ButtonBar.ButtonData.OK_DONE),
                javafx.scene.control.ButtonType.CANCEL);
        consentDialog.setResultConverter(btn -> btn.getButtonData() == javafx.scene.control.ButtonBar.ButtonData.OK_DONE
                ? consent.isSelected() : null);
        Boolean ok = consentDialog.showAndWait().orElse(null);
        if (ok == null) return;
        if (!ok) { Toast.error(ctx, "Consent is required before sharing (POPIA)."); return; }

        try {
            String caseId = ctx.dataStore().findCasesForClient(ctx.currentUser().getId()).get(0).getId();
            ctx.dataStore().uploadClientDocument(caseId, file, true);
            ctx.auditService().info(ctx.currentUser().getName(), "DOCUMENT_SHARED",
                    "Client shared " + file.getName() + " with the firm");
            Toast.success(ctx, "\"" + file.getName() + "\" sent to your lawyer.");
        } catch (Exception ex) {
            Toast.error(ctx, "Could not share document: " + ex.getMessage());
        }
    }
}
