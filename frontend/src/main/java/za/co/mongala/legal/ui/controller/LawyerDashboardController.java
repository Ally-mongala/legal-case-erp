package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.IDataStore;
import za.co.mongala.legal.ui.AppContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lawyer dashboard (FXML: fxml/views/dashboard.fxml).
 *
 * The "analysis dashboard to track efficiency and satisfaction metrics" from
 * Assignment 1. Shows KPI cards, a deadline chart, a case-status chart, the
 * next deadlines and a live audit/activity feed.
 */
public class LawyerDashboardController {

    private final AppContext ctx;

    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private GridPane statGrid;
    @FXML private HBox chartsRow;
    @FXML private VBox deadlineList;
    @FXML private VBox activityList;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public LawyerDashboardController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        LocalDate today = LocalDate.now();
        pageTitle.setText("Dashboard");
        pageSubtitle.setText("Firm overview - " + today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")));

        buildStatCards();
        buildCharts();
        buildDeadlineList();
        buildActivityList();
    }

    /* ------------------------------------------------------------------ */

    private void buildStatCards() {
        IDataStore ds = ctx.dataStore();
        List<Case> cases = ds.getAllCases();

        long activeMatters = cases.stream().filter(c -> c.getStatus() != CaseStatus.CLOSED).count();
        long deadlinesNext30 = ds.allDeadlines().stream()
                .filter(d -> d.daysLeft() >= 0 && d.daysLeft() <= 30).count();
        long overdue = ds.allDeadlines().stream().filter(d -> d.daysLeft() < 0).count();
        double avgConfidence = ds.allExtractions().stream()
                .mapToDouble(Extraction::getConfidence).average().orElse(0);

        statGrid.add(statCard("#1F3A5F", "ACTIVE MATTERS", String.valueOf(activeMatters),
                "across all practice areas"), 0, 0);
        statGrid.add(statCard("#C9A227", "DEADLINES IN 30 DAYS", String.valueOf(deadlinesNext30),
                "auto-synced to Google Calendar"), 1, 0);
        statGrid.add(statCard(overdue > 0 ? "#C62828" : "#2E7D32", "OVERDUE DEADLINES", String.valueOf(overdue),
                overdue > 0 ? "attention required" : "all on track"), 2, 0);
        statGrid.add(statCard("#2E7D32", "AVG EXTRACTION CONFIDENCE",
                String.format("%.0f%%", avgConfidence * 100),
                "target: >= 90% (Goal A)"), 3, 0);
    }

    private VBox statCard(String color, String title, String value, String caption) {
        VBox card = new VBox(2);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 12, 16));

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("stat-title");
        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("stat-value");
        valueLbl.setStyle("-fx-text-fill: " + color + ";");
        Label capLbl = new Label(caption);
        capLbl.getStyleClass().add("stat-caption");

        card.getChildren().addAll(titleLbl, valueLbl, capLbl);
        return card;
    }

    /* ------------------------------------------------------------------ */

    private void buildCharts() {
        // Deadlines per month (bar chart)
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 6, 1);
        xAxis.setLabel("Month (2026)");
        yAxis.setLabel("Deadlines");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setTitle(null);
        chart.getStyleClass().add("chart-deadlines");

        Map<String, Long> counts = new java.util.TreeMap<>();
        ctx.dataStore().allDeadlines().forEach(d -> {
            String key = d.getDate().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            counts.merge(key, 1L, Long::sum);
        });
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        counts.forEach((month, n) -> series.getData().add(new XYChart.Data<>(month, n)));
        chart.getData().add(series);

        // Cases by status (pie chart)
        PieChart pie = new PieChart();
        pie.setLabelsVisible(true);
        pie.setLegendVisible(true);
        pie.setAnimated(false);
        pie.setTitle(null);
        pie.getStyleClass().add("chart-status");
        Map<CaseStatus, Long> byStatus = new EnumMap<>(CaseStatus.class);
        ctx.dataStore().getAllCases().forEach(c -> byStatus.merge(c.getStatus(), 1L, Long::sum));
        byStatus.forEach((status, n) ->
                pie.getData().add(new PieChart.Data(status.getDisplayName() + " (" + n + ")", n)));

        VBox barCard = cardWithTitle("Deadlines per month - synced calendar events", chart);
        VBox pieCard = cardWithTitle("Case portfolio by status", pie);
        HBox.setHgrow(barCard, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(pieCard, javafx.scene.layout.Priority.ALWAYS);
        barCard.setMaxWidth(Double.MAX_VALUE);
        pieCard.setMaxWidth(Double.MAX_VALUE);
        chartsRow.getChildren().addAll(barCard, pieCard);
    }

    private VBox cardWithTitle(String title, javafx.scene.Node body) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        Label t = new Label(title);
        t.getStyleClass().add("section-title");
        card.getChildren().addAll(t, body);
        return card;
    }

    /* ------------------------------------------------------------------ */

    private void buildDeadlineList() {
        List<Deadline> next = ctx.dataStore().allDeadlines().stream()
                .filter(d -> d.daysLeft() >= 0)
                .limit(6)
                .toList();
        if (next.isEmpty()) {
            deadlineList.getChildren().add(new Label("No upcoming deadlines."));
            return;
        }
        for (Deadline d : next) {
            deadlineList.getChildren().add(deadlineRow(d));
        }
    }

    private HBox deadlineRow(Deadline d) {
        HBox row = new HBox(10);
        row.getStyleClass().add("list-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(5);
        dot.getStyleClass().add("dot-" + (d.daysLeft() <= 7 ? "warning" : "ok"));

        Label left = new Label(d.daysLeft() + "d");
        left.getStyleClass().add("count-chip");

        VBox mid = new VBox(1);
        Label title = new Label(d.getTitle());
        title.getStyleClass().add("row-title");
        Label sub = new Label(d.getCaseReference() + "  \u00B7  " + d.getDate().format(DATE_FMT));
        sub.getStyleClass().add("row-sub");
        mid.getChildren().addAll(title, sub);

        Label sync = new Label(d.isSynced() ? "SYNCED" : "NOT SYNCED");
        sync.getStyleClass().addAll("chip", d.isSynced() ? "chip-green" : "chip-gray");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(dot, left, mid, spacer, sync);
        return row;
    }

    /* ------------------------------------------------------------------ */

    private void buildActivityList() {
        List<ActivityLog> recent = ctx.dataStore().recentActivity(6);
        for (ActivityLog a : recent) {
            HBox row = new HBox(10);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label level = new Label(levelGlyph(a.getLevel()));
            level.getStyleClass().addAll("chip", chipForLevel(a.getLevel()));

            VBox mid = new VBox(1);
            Label action = new Label(a.getAction());
            action.getStyleClass().add("row-title");
            Label details = new Label(a.getDetails());
            details.getStyleClass().add("row-sub");
            details.setWrapText(true);
            mid.getChildren().addAll(action, details);
            HBox.setHgrow(mid, javafx.scene.layout.Priority.ALWAYS);

            Label time = new Label(a.getTimestamp().format(DateTimeFormatter.ofPattern("d MMM HH:mm")));
            time.getStyleClass().add("row-sub");

            row.getChildren().addAll(level, mid, time);
            activityList.getChildren().add(row);
        }
    }

    private String levelGlyph(ActivityLog.Level level) {
        return switch (level) {
            case SUCCESS -> "OK";
            case WARNING -> "!";
            default -> "i";
        };
    }

    private String chipForLevel(ActivityLog.Level level) {
        return switch (level) {
            case SUCCESS -> "chip-green";
            case WARNING -> "chip-amber";
            default -> "chip-blue";
        };
    }
}
