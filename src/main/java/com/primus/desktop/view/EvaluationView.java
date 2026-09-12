package com.primus.desktop.view;

import com.primus.core.eval.EvalSummary;
import com.primus.core.eval.EvaluationService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/**
 * Old:    React Evaluation dashboard -> GET /api/eval/run -> EvaluationController
 * New:    This view -> EvaluationService.runFullEvaluation() (direct call, on a
 *         background thread since it is a genuinely slow, real experiment --
 *         5 systems x 10 runs = 50 real incident-diagnosis cycles, several of
 *         which call the actual Gemini API and can retry with a 5s sleep on
 *         failure. Running it on the JavaFX Application Thread would freeze
 *         the whole UI for minutes, so it runs via a javafx.concurrent.Task.
 *
 * Every number on this screen comes directly from EvalSummary/EvalResult --
 * nothing here is a hard-coded or placeholder metric. This is the screen
 * that demonstrates the actual research contribution: rule-based, LLM-only,
 * LLM+RAG, single-agent Primus, and multi-agent Primus are all run against
 * the SAME injected faults, so the comparison is apples-to-apples.
 */
public class EvaluationView {

    private final EvaluationService evaluationService;
    private final Runnable onBack;

    private VBox resultsBox;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    private Button runButton;

    public EvaluationView(EvaluationService evaluationService, Runnable onBack) {
        this.evaluationService = evaluationService;
        this.onBack = onBack;
    }

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        Button backButton = new Button("< Back to Dashboard");
        backButton.setOnAction(e -> onBack.run());

        HBox header = new HBox(16, backButton);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        root.setTop(header);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));

        Label title = new Label("Evaluation / Research Dashboard");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label explanation = new Label(
                "Research question: do coordinated specialist agents (multi-agent Primus) produce " +
                        "safer and more reliable operational decisions than a single generalist agent with tool access? " +
                        "This runs 5 diagnosis systems -- rule-based, LLM-only, LLM+RAG, single-agent Primus, and " +
                        "multi-agent Primus -- against the SAME set of injected faults, 10 runs each, so results are " +
                        "directly comparable. This calls the real Gemini API up to 50 times and can take several minutes.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted-text");

        runButton = new Button("Run Full Evaluation (50 real runs, may take minutes)");
        runButton.getStyleClass().add("button-primary");
        runButton.setOnAction(e -> runEvaluation());

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        statusLabel = new Label();

        HBox runRow = new HBox(12, runButton, progressIndicator, statusLabel);
        runRow.setAlignment(Pos.CENTER_LEFT);

        resultsBox = new VBox(12);
        Label placeholder = new Label("Click \"Run Full Evaluation\" to generate real results -- nothing is shown until it actually runs.");
        placeholder.getStyleClass().add("muted-text");
        resultsBox.getChildren().add(placeholder);

        content.getChildren().addAll(title, explanation, runRow, resultsBox);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("root");
        root.setCenter(scrollPane);

        return root;
    }

    private void runEvaluation() {
        runButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("Running 50 real incident-diagnosis cycles against live services (this calls Gemini repeatedly)...");
        resultsBox.getChildren().clear();

        Task<List<EvalSummary>> task = new Task<>() {
            @Override
            protected List<EvalSummary> call() {
                return evaluationService.runFullEvaluation();
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            runButton.setDisable(false);
            progressIndicator.setVisible(false);
            statusLabel.setText("Done.");
            renderResults(task.getValue());
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            runButton.setDisable(false);
            progressIndicator.setVisible(false);
            Throwable ex = task.getException();
            statusLabel.setText("Evaluation failed: " + (ex != null ? ex.getMessage() : "unknown error"));
            statusLabel.setStyle("-fx-text-fill: #ef4444;");
        }));

        Thread thread = new Thread(task, "primus-eval-run");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderResults(List<EvalSummary> summaries) {
        resultsBox.getChildren().clear();

        VBox comparisonCard = buildComparisonTable(summaries);
        resultsBox.getChildren().add(comparisonCard);

        VBox chartCard = buildAccuracyBars(summaries);
        resultsBox.getChildren().add(chartCard);
    }

    private VBox buildComparisonTable(List<EvalSummary> summaries) {
        Label heading = new Label("Results by System (same incidents, same injected faults)");
        heading.getStyleClass().add("card-heading");

        HBox headerRow = new HBox(0,
                cell("System", 180, true), cell("Runs", 60, true),
                cell("Diagnosis Acc.", 120, true), cell("Remediation Acc.", 130, true),
                cell("Resolved %", 100, true), cell("Avg LLM Calls", 110, true), cell("Avg Latency", 100, true));

        VBox table = new VBox(headerRow);
        for (EvalSummary summary : summaries) {
            double avgLlmCalls = summary.runs().stream().mapToInt(r -> r.llmCallCount()).average().orElse(0);
            double avgLatencyMs = summary.runs().stream().mapToLong(r -> r.latencyMs()).average().orElse(0);

            HBox row = new HBox(0,
                    cell(friendlySystemName(summary.systemName()), 180, false),
                    cell(String.valueOf(summary.totalRuns()), 60, false),
                    cell(pct(summary.diagnosisAccuracyPct()), 120, false),
                    cell(pct(summary.remediationAccuracyPct()), 130, false),
                    cell(pct(summary.resolvedPct()), 100, false),
                    cell(String.format(Locale.US, "%.1f", avgLlmCalls), 110, false),
                    cell(String.format(Locale.US, "%.0fms", avgLatencyMs), 100, false));
            row.setStyle("-fx-border-color: transparent transparent #232838 transparent; -fx-border-width: 0 0 1 0;");
            table.getChildren().add(row);
        }

        VBox card = new VBox(10, heading, table);
        card.getStyleClass().add("content-card");
        return card;
    }

    private VBox buildAccuracyBars(List<EvalSummary> summaries) {
        Label heading = new Label("Diagnosis Accuracy by System");
        heading.getStyleClass().add("card-heading");

        VBox card = new VBox(10, heading);
        card.getStyleClass().add("content-card");

        for (EvalSummary summary : summaries) {
            Label nameLabel = new Label(friendlySystemName(summary.systemName()));
            nameLabel.setPrefWidth(160);
            nameLabel.setStyle("-fx-font-size: 11px;");

            Region bar = new Region();
            double pct = Math.max(0, Math.min(100, summary.diagnosisAccuracyPct()));
            bar.setPrefWidth(pct * 4); // 400px = 100%
            bar.setPrefHeight(16);
            String color = summary.systemName().contains("multi_agent") ? "#22d3ee" : "#3b82f6";
            bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");

            Label pctLabel = new Label(pct(summary.diagnosisAccuracyPct()));
            pctLabel.getStyleClass().add("muted-text");
            pctLabel.setStyle("-fx-font-size: 10px;");

            HBox row = new HBox(8, nameLabel, bar, pctLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }

        return card;
    }

    private Label cell(String text, double width, boolean isHeader) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setWrapText(true);
        if (isHeader) {
            label.getStyleClass().add("muted-text");
            label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            label.setStyle("-fx-font-size: 12px;");
        }
        label.setPadding(new Insets(6, 4, 6, 4));
        return label;
    }

    private String pct(double value) {
        return String.format(Locale.US, "%.1f%%", value);
    }

    private String friendlySystemName(String systemName) {
        return switch (systemName) {
            case "rule_based" -> "Rule-Based";
            case "llm_no_rag" -> "LLM Only";
            case "llm_rag" -> "LLM + RAG";
            case "single_agent_primus" -> "Single-Agent Primus";
            case "multi_agent_primus" -> "Multi-Agent Primus";
            default -> systemName;
        };
    }
}