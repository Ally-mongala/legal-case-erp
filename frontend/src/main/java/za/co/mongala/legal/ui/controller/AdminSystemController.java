package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import za.co.mongala.legal.model.Dataset;
import za.co.mongala.legal.model.TrainingRun;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Admin portal - System &amp; model management (FR10): usage metrics,
 * approving model retraining runs, managing training datasets.
 */
public class AdminSystemController {

    private final AppContext ctx;

    @FXML private GridPane metricGrid;
    @FXML private TableView<TrainingRun> runTable;
    @FXML private TableColumn<TrainingRun, String> modelCol;
    @FXML private TableColumn<TrainingRun, String> datasetCol;
    @FXML private TableColumn<TrainingRun, TrainingRun.Status> statusCol;
    @FXML private TableColumn<TrainingRun, String> requestedCol;
    @FXML private TableColumn<TrainingRun, TrainingRun> approveCol;
    @FXML private TableView<Dataset> datasetTable;
    @FXML private TableColumn<Dataset, String> dsNameCol;
    @FXML private TableColumn<Dataset, Long> dsRecordsCol;
    @FXML private TableColumn<Dataset, Long> dsSizeCol;
    @FXML private TableColumn<Dataset, String> dsUploadedCol;
    @FXML private TableColumn<Dataset, Dataset> dsActionCol;
    @FXML private Button uploadDatasetButton;
    @FXML private Button downloadSystemButton;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public AdminSystemController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        setupRunsTable();
        setupDatasetsTable();
        uploadDatasetButton.setOnAction(e -> onUploadDataset());
        downloadSystemButton.setOnAction(e -> onDownloadSystemReport());
        refresh();
    }

    private void setupRunsTable() {
        modelCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getModelName()));
        datasetCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getDatasetId()));
        statusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(TrainingRun.Status s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setGraphic(null); return; }
                Label chip = new Label(s.name());
                chip.getStyleClass().addAll("chip",
                        s == TrainingRun.Status.APPROVED ? "chip-green"
                                : s == TrainingRun.Status.PENDING ? "chip-amber" : "chip-blue");
                setGraphic(chip);
            }
        });
        requestedCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getRequestedBy()));
        approveCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        approveCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Approve");
            @Override
            protected void updateItem(TrainingRun r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); return; }
                boolean pending = r.getStatus() == TrainingRun.Status.PENDING;
                btn.getStyleClass().setAll("btn-ghost");
                btn.setText(pending ? "Approve run" : r.getStatus().name());
                btn.setDisable(!pending);
                btn.setOnAction(e -> approve(r));
                setGraphic(btn);
            }
        });
    }

    private void setupDatasetsTable() {
        dsNameCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        dsRecordsCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getRecords()));
        dsSizeCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getSizeKb()));
        dsUploadedCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getUploadedAt().format(DT_FMT)));
        dsActionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        dsActionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Download");
            @Override
            protected void updateItem(Dataset ds, boolean empty) {
                super.updateItem(ds, empty);
                if (empty || ds == null) { setGraphic(null); return; }
                btn.getStyleClass().setAll("btn-ghost");
                btn.setOnAction(e -> downloadDataset(ds));
                setGraphic(btn);
            }
        });
    }

    /** Admin uploads a new training dataset (FR10). */
    private void onUploadDataset() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload a training dataset");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Dataset files", "*.csv", "*.txt", "*.jsonl"));
        java.io.File file = chooser.showOpenDialog(uploadDatasetButton.getScene().getWindow());
        if (file == null) return;
        TextInputDialog nameDialog = new TextInputDialog(file.getName());
        nameDialog.setTitle("Dataset name");
        nameDialog.setHeaderText("Name this training dataset");
        nameDialog.showAndWait().ifPresent(name -> {
            try {
                ctx.dataStore().uploadDataset(file, name);
                ctx.auditService().success(ctx.currentUser().getName(), "DATASET_UPLOADED",
                        "Admin uploaded training dataset \"" + name + "\"");
                Toast.success(ctx, "Dataset \"" + name + "\" uploaded.");
                refresh();
            } catch (Exception ex) {
                Toast.error(ctx, "Could not upload dataset: " + ex.getMessage());
            }
        });
    }

    /** Admin downloads a training dataset (FR10). */
    private void downloadDataset(Dataset ds) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save dataset");
        chooser.setInitialFileName(ds.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".csv");
        java.io.File target = chooser.showSaveDialog(uploadDatasetButton.getScene().getWindow());
        if (target == null) return;
        try {
            byte[] bytes = ctx.dataStore().downloadDataset(ds.getId());
            java.nio.file.Files.write(target.toPath(), bytes);
            ctx.auditService().info(ctx.currentUser().getName(), "DATASET_DOWNLOAD",
                    "Admin downloaded dataset \"" + ds.getName() + "\"");
            Toast.success(ctx, "Dataset saved to " + target.getName());
        } catch (Exception ex) {
            Toast.error(ctx, "Could not download dataset: " + ex.getMessage());
        }
    }

    /** Admin downloads the full system & model management report (FR10). */
    private void onDownloadSystemReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save system report");
        chooser.setInitialFileName("system-model-report.csv");
        java.io.File target = chooser.showSaveDialog(downloadSystemButton.getScene().getWindow());
        if (target == null) return;
        try {
            String report = ctx.dataStore().systemReport();
            java.nio.file.Files.writeString(target.toPath(), report);
            ctx.auditService().info(ctx.currentUser().getName(), "SYSTEM_REPORT_DOWNLOAD",
                    "Admin downloaded system & model management report");
            Toast.success(ctx, "System report saved to " + target.getName());
        } catch (Exception ex) {
            Toast.error(ctx, "Could not save report: " + ex.getMessage());
        }
    }

    private void approve(TrainingRun r) {
        try {
            ctx.dataStore().approveTrainingRun(r.getId());
            ctx.auditService().success(ctx.currentUser().getName(), "TRAINING_APPROVED",
                    "Model retraining run " + r.getId() + " approved (ETR3 human-in-the-loop)");
            Toast.success(ctx, "Retraining run approved - the worker will start it shortly.");
            refresh();
        } catch (Exception ex) {
            Toast.error(ctx, "Could not approve run: " + ex.getMessage());
        }
    }

    private void refresh() {
        try {
            Map<String, Object> m = ctx.dataStore().metrics();
            metricGrid.getChildren().clear();
            int row = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Label key = new Label(label(e.getKey()));
                key.getStyleClass().add("meta-key");
                Label val = new Label(String.valueOf(e.getValue()));
                val.getStyleClass().add("meta-value");
                metricGrid.add(key, 0, row);
                metricGrid.add(val, 1, row);
                GridPane.setMargin(key, new Insets(2, 0, 2, 0));
                row++;
            }
            runTable.getItems().setAll(ctx.dataStore().trainingRuns());
            datasetTable.getItems().setAll(ctx.dataStore().datasets());
        } catch (Exception e) {
            Toast.error(ctx, "Could not load admin metrics: " + e.getMessage());
        }
    }

    private static String label(String key) {
        StringBuilder sb = new StringBuilder();
        for (char c : key.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString().trim().toUpperCase();
    }
}
