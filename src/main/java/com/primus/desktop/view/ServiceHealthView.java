package com.primus.desktop.view;

import com.primus.core.agent.DiagnosticTools;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * Old:    React Infrastructure Map -> REST endpoints per service
 * New:    This view -> DiagnosticTools.checkServiceHealth()/getMetrics() (direct calls)
 *
 * Honesty notes:
 * - There is no service-dependency graph model in Primus Core (just a flat
 *   service list), so this is a health GRID, not the dependency-graph
 *   diagram the original spec envisioned -- building a fake topology would
 *   mean drawing connections that don't exist in any real data.
 * - checkServiceHealth() is real (backed by FaultSimulator's actual
 *   injected-fault state). getMetrics() returns RANDOM numbers by the
 *   original codebase's own design (a fault-injection testbed, not real
 *   telemetry) -- labeled "(simulated)" here rather than presented as live
 *   production metrics.
 * - There is no single canonical "all services" list in Primus Core;
 *   IncidentDetectionJob polls 3, EvaluationService uses 4. This screen
 *   uses the union of both rather than inventing a new list.
 */
public class ServiceHealthView {

    private static final List<String> KNOWN_SERVICES =
            List.of("auth-api", "checkout-api", "payment-api", "orders-api");

    private final DiagnosticTools diagnosticTools;
    private final Runnable onBack;
    private FlowPane grid;

    public ServiceHealthView(DiagnosticTools diagnosticTools, Runnable onBack) {
        this.diagnosticTools = diagnosticTools;
        this.onBack = onBack;
    }

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        Button backButton = new Button("< Back to Dashboard");
        backButton.setOnAction(e -> onBack.run());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadHealth());

        HBox header = new HBox(16, backButton, refreshButton);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        root.setTop(header);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));

        Label title = new Label("Service Health");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label caption = new Label(
                "Health status is real (from the fault simulator's actual state). CPU/memory/error-rate " +
                        "numbers are simulated -- this is a fault-injection research testbed, not live production telemetry.");
        caption.getStyleClass().add("muted-text");
        caption.setWrapText(true);

        grid = new FlowPane(16, 16);
        content.getChildren().addAll(title, caption, grid);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("root");
        root.setCenter(scrollPane);

        loadHealth();
        return root;
    }

    private void loadHealth() {
        grid.getChildren().clear();
        for (String service : KNOWN_SERVICES) {
            grid.getChildren().add(serviceCard(service));
        }
    }

    private VBox serviceCard(String serviceName) {
        boolean healthy = diagnosticTools.checkServiceHealth(serviceName);
        Map<String, Object> metrics = diagnosticTools.getMetrics(serviceName);

        Label nameLabel = new Label(serviceName);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Label statusBadge = new Label(healthy ? "HEALTHY" : "UNHEALTHY");
        statusBadge.getStyleClass().addAll("status-badge", healthy ? "status-resolved" : "status-reopened");

        HBox topRow = new HBox(10, nameLabel, statusBadge);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label metricsHeading = new Label("Metrics (simulated):");
        metricsHeading.getStyleClass().add("muted-text");
        metricsHeading.setStyle("-fx-font-size: 10px;");

        Label cpuLabel = metricLine("CPU", metrics.get("cpuPercent") + "%");
        Label memLabel = metricLine("Memory", metrics.get("memoryPercent") + "%");
        Label errLabel = metricLine("Error Rate", metrics.get("errorRatePercent") + "%");
        Label poolLabel = metricLine("DB Pool", metrics.get("dbConnectionPoolUtilizationPercent") + "%");

        VBox card = new VBox(6, topRow, metricsHeading, cpuLabel, memLabel, errLabel, poolLabel);
        card.getStyleClass().add("content-card");
        card.setPrefWidth(220);
        return card;
    }

    private Label metricLine(String label, String value) {
        Label line = new Label(label + ": " + value);
        line.setStyle("-fx-font-size: 12px;");
        return line;
    }
}