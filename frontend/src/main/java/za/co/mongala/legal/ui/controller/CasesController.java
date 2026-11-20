package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.CaseStatus;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Case list (FXML: fxml/views/cases.fxml) - the central legal database browser.
 * Supports live search and status filtering; double-clicking a case opens its
 * full detail view (documents, AI extraction, summary, calendar, messages).
 */
public class CasesController {

    private final AppContext ctx;
    private final Consumer<Case> onOpenCase;

    @FXML private TableView<Case> caseTable;
    @FXML private TableColumn<Case, String> refCol;
    @FXML private TableColumn<Case, String> titleCol;
    @FXML private TableColumn<Case, String> clientCol;
    @FXML private TableColumn<Case, String> opposingCol;
    @FXML private TableColumn<Case, CaseStatus> statusCol;
    @FXML private TableColumn<Case, String> hearingCol;
    @FXML private TableColumn<Case, String> openedCol;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newCaseButton;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public CasesController(AppContext ctx, Consumer<Case> onOpenCase) {
        this.ctx = ctx;
        this.onOpenCase = onOpenCase;
    }

    @FXML
    private void initialize() {
        statusFilter.getItems().add("All statuses");
        for (CaseStatus s : CaseStatus.values()) {
            statusFilter.getItems().add(s.getDisplayName());
        }
        statusFilter.getSelectionModel().select(0);

        searchField.textProperty().addListener((obs, o, n) -> refresh());
        statusFilter.valueProperty().addListener((obs, o, n) -> refresh());

        newCaseButton.setOnAction(e -> openNewCaseDialog());

        setupTable();
        refresh();
    }

    /** US1: create a new matter through the backend (POST /api/cases). */
    private void openNewCaseDialog() {
        Dialog<Case> dialog = new Dialog<>();
        dialog.setTitle("New case");
        dialog.setHeaderText("Create a new matter");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new javafx.geometry.Insets(16));

        TextField title = new TextField();
        title.setPromptText("e.g. Mokoena v Siyaya Transport");
        TextField client = new TextField();
        client.setPromptText("Client name");
        TextField opposing = new TextField();
        opposing.setPromptText("Opposing party");
        ComboBox<String> matter = new ComboBox<>();
        matter.getItems().addAll(za.co.mongala.legal.model.MatterTypes.LIST);
        matter.setValue(za.co.mongala.legal.model.MatterTypes.LIST.get(0));
        TextField court = new TextField();
        court.setPromptText("Court");
        TextArea description = new TextArea();
        description.setPromptText("Short description");
        description.setPrefRowCount(3);

        grid.addRow(0, new Label("Case title *"), title);
        grid.addRow(1, new Label("Client"), client);
        grid.addRow(2, new Label("Opposing party"), opposing);
        grid.addRow(3, new Label("Matter type"), matter);
        grid.addRow(4, new Label("Court"), court);
        grid.addRow(5, new Label("Description"), description);
        dialog.getDialogPane().setContent(grid);

        javafx.scene.control.ButtonType create =
                new javafx.scene.control.ButtonType("Create case", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, javafx.scene.control.ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn != create || title.getText() == null || title.getText().isBlank()) return null;
            return new Case(null, null, title.getText().trim(),
                    null, client.getText() == null ? "" : client.getText().trim(),
                    opposing.getText() == null ? "Unknown" : opposing.getText().trim(),
                    CaseStatus.OPEN,
                    matter.getValue() == null ? "General" : matter.getValue(),
                    court.getText() == null || court.getText().isBlank() ? "High Court" : court.getText().trim(),
                    description.getText() == null ? "" : description.getText().trim(),
                    null, 0);
        });

        dialog.showAndWait().ifPresent(c -> {
            try {
                Case created = ctx.dataStore().createCase(c);
                Toast.success(ctx, "Case created: " + created.getReference());
                refresh();
            } catch (Exception ex) {
                Toast.error(ctx, "Could not create case: " + ex.getMessage());
            }
        });
    }

    private void setupTable() {
        refCol.setCellValueFactory(new PropertyValueFactory<>("reference"));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        clientCol.setCellValueFactory(new PropertyValueFactory<>("clientName"));
        opposingCol.setCellValueFactory(new PropertyValueFactory<>("opposingParty"));
        statusCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getStatus()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(CaseStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(s.getDisplayName());
                badge.getStyleClass().add("chip");
                badge.setStyle("-fx-background-color: " + s.getHexColor() + "; -fx-text-fill: white;");
                setGraphic(badge);
            }
        });
        openedCol.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(data.getValue().getOpenedDate().format(DATE_FMT)));
        hearingCol.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(nextHearingDate(data.getValue())));

        caseTable.setRowFactory(tv -> {
            TableRow<Case> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    onOpenCase.accept(row.getItem());
                }
            });
            return row;
        });
    }

    private String nextHearingDate(Case c) {
        Optional<Deadline> h = ctx.dataStore().deadlinesForCase(c.getId()).stream()
                .filter(d -> d.getType() == Deadline.DeadlineType.HEARING)
                .max(Comparator.comparing(Deadline::getDate));
        return h.map(d -> d.getDate().format(DATE_FMT)).orElse("—");
    }

    private void refresh() {
        List<Case> results = ctx.dataStore().findCases(searchField.getText());
        String filter = statusFilter.getValue();
        if (filter != null && !"All statuses".equals(filter)) {
            results = results.stream()
                    .filter(c -> c.getStatus().getDisplayName().equals(filter))
                    .toList();
        }
        caseTable.getItems().setAll(results);
    }
}
