package com.primus.desktop.view;

import com.primus.core.knowledge.KbDocument;
import com.primus.core.knowledge.RetrievalService;
import com.primus.core.knowledge.ScoredDocument;
import com.primus.core.memory.IncidentMemory;
import com.primus.core.memory.IncidentMemoryService;
import com.primus.core.memory.ScoredMemory;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Old:    React RAG/Knowledge tabs -> REST endpoints -> KnowledgeBaseController
 * New:    This view -> RetrievalService / IncidentMemoryService (direct calls)
 *
 * Browsing (the default view in each tab) is free -- a plain DB read via
 * getAllDocuments()/getAllMemories(). Searching calls the real Gemini
 * embeddings API once per search (via searchWithScores()), so it's an
 * explicit button press, not something that fires on every keystroke, and
 * runs on a background thread so the UI doesn't freeze during the call.
 * The relevance percentage shown for search results is the REAL cosine
 * similarity score, not an invented number.
 */
public class KnowledgeExplorerView {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final RetrievalService retrievalService;
    private final IncidentMemoryService incidentMemoryService;
    private final Runnable onBack;
    private final int initialTabIndex;

    public KnowledgeExplorerView(RetrievalService retrievalService, IncidentMemoryService incidentMemoryService,
                                 Runnable onBack, int initialTabIndex) {
        this.retrievalService = retrievalService;
        this.incidentMemoryService = incidentMemoryService;
        this.onBack = onBack;
        this.initialTabIndex = initialTabIndex;
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

        TabPane tabPane = new TabPane();
        Tab kbTab = new Tab("Knowledge Base", buildKnowledgeBaseTab());
        kbTab.setClosable(false);
        Tab memoryTab = new Tab("Incident Memory", buildIncidentMemoryTab());
        memoryTab.setClosable(false);
        tabPane.getTabs().addAll(kbTab, memoryTab);
        tabPane.getSelectionModel().select(initialTabIndex);

        root.setCenter(tabPane);
        return root;
    }

    // ---------- Knowledge Base tab ----------

    private ScrollPane buildKnowledgeBaseTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));

        Label title = new Label("Knowledge Base");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label caption = new Label("Runbooks and operational documentation the RagAgent draws on during investigation.");
        caption.getStyleClass().add("muted-text");

        TextField searchField = new TextField();
        searchField.setPromptText("Semantic search (calls Gemini embeddings once per search)...");
        searchField.setPrefWidth(400);
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("button-primary");
        Button clearButton = new Button("Clear");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(18, 18);
        HBox searchRow = new HBox(8, searchField, searchButton, clearButton, progress);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        VBox resultsBox = new VBox(10);
        content.getChildren().addAll(title, caption, searchRow, resultsBox);

        Runnable loadAll = () -> {
            resultsBox.getChildren().clear();
            List<KbDocument> docs = retrievalService.getAllDocuments();
            if (docs.isEmpty()) {
                resultsBox.getChildren().add(mutedLabel("No knowledge base documents found."));
            }
            for (KbDocument doc : docs) {
                resultsBox.getChildren().add(kbDocumentCard(doc, null));
            }
        };
        loadAll.run();

        clearButton.setOnAction(e -> {
            searchField.clear();
            loadAll.run();
        });

        searchButton.setOnAction(e -> {
            String query = searchField.getText();
            if (query == null || query.isBlank()) {
                loadAll.run();
                return;
            }
            searchButton.setDisable(true);
            progress.setVisible(true);
            resultsBox.getChildren().clear();

            Task<List<ScoredDocument>> task = new Task<>() {
                @Override
                protected List<ScoredDocument> call() {
                    return retrievalService.searchWithScores(query, 5);
                }
            };
            task.setOnSucceeded(ev -> Platform.runLater(() -> {
                searchButton.setDisable(false);
                progress.setVisible(false);
                resultsBox.getChildren().clear();
                if (task.getValue().isEmpty()) {
                    resultsBox.getChildren().add(mutedLabel("No matching documents."));
                }
                for (ScoredDocument scored : task.getValue()) {
                    resultsBox.getChildren().add(kbDocumentCard(scored.document(), scored.score()));
                }
            }));
            task.setOnFailed(ev -> Platform.runLater(() -> {
                searchButton.setDisable(false);
                progress.setVisible(false);
                resultsBox.getChildren().clear();
                resultsBox.getChildren().add(errorLabel("Search failed: " +
                        (task.getException() != null ? task.getException().getMessage() : "unknown error")));
            }));
            new Thread(task, "kb-search").start();
        });

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("root");
        return scrollPane;
    }

    private VBox kbDocumentCard(KbDocument doc, Double score) {
        Label typeBadge = new Label(doc.getDocType());
        typeBadge.getStyleClass().addAll("status-badge", "status-resolved");
        Label titleLabel = new Label(doc.getTitle());
        titleLabel.setStyle("-fx-font-weight: bold;");
        HBox topRow = new HBox(8, typeBadge, titleLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);
        if (score != null) {
            Label scoreLabel = new Label(String.format(Locale.US, "%.1f%% relevant", score * 100));
            scoreLabel.getStyleClass().add("muted-text");
            topRow.getChildren().add(scoreLabel);
        }

        String preview = doc.getContent() != null && doc.getContent().length() > 300
                ? doc.getContent().substring(0, 300) + "..."
                : doc.getContent();
        Label contentLabel = new Label(preview);
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-font-size: 12px;");

        Label dateLabel = new Label(doc.getCreatedAt() != null ? TIMESTAMP_FORMAT.format(doc.getCreatedAt()) : "");
        dateLabel.getStyleClass().add("muted-text");
        dateLabel.setStyle("-fx-font-size: 10px;");

        VBox card = new VBox(6, topRow, contentLabel, dateLabel);
        card.getStyleClass().add("content-card");
        return card;
    }

    // ---------- Incident Memory tab ----------

    private ScrollPane buildIncidentMemoryTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 24, 20, 24));

        Label title = new Label("Incident Memory");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label caption = new Label("Real resolved incidents Primus has stored, with root cause and remediation taken.");
        caption.getStyleClass().add("muted-text");

        TextField searchField = new TextField();
        searchField.setPromptText("Find similar past incidents (calls Gemini embeddings once per search)...");
        searchField.setPrefWidth(400);
        Button searchButton = new Button("Search");
        searchButton.getStyleClass().add("button-primary");
        Button clearButton = new Button("Clear");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(18, 18);
        HBox searchRow = new HBox(8, searchField, searchButton, clearButton, progress);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        VBox resultsBox = new VBox(10);
        content.getChildren().addAll(title, caption, searchRow, resultsBox);

        Runnable loadAll = () -> {
            resultsBox.getChildren().clear();
            List<IncidentMemory> memories = incidentMemoryService.getAllMemories();
            if (memories.isEmpty()) {
                resultsBox.getChildren().add(mutedLabel("No incident memory entries yet -- resolve an incident to create one."));
            }
            for (IncidentMemory memory : memories) {
                resultsBox.getChildren().add(memoryCard(memory, null));
            }
        };
        loadAll.run();

        clearButton.setOnAction(e -> {
            searchField.clear();
            loadAll.run();
        });

        searchButton.setOnAction(e -> {
            String query = searchField.getText();
            if (query == null || query.isBlank()) {
                loadAll.run();
                return;
            }
            searchButton.setDisable(true);
            progress.setVisible(true);
            resultsBox.getChildren().clear();

            Task<List<ScoredMemory>> task = new Task<>() {
                @Override
                protected List<ScoredMemory> call() {
                    return incidentMemoryService.searchWithScores(query, 5);
                }
            };
            task.setOnSucceeded(ev -> Platform.runLater(() -> {
                searchButton.setDisable(false);
                progress.setVisible(false);
                resultsBox.getChildren().clear();
                if (task.getValue().isEmpty()) {
                    resultsBox.getChildren().add(mutedLabel("No matching incidents."));
                }
                for (ScoredMemory scored : task.getValue()) {
                    resultsBox.getChildren().add(memoryCard(scored.memory(), scored.score()));
                }
            }));
            task.setOnFailed(ev -> Platform.runLater(() -> {
                searchButton.setDisable(false);
                progress.setVisible(false);
                resultsBox.getChildren().clear();
                resultsBox.getChildren().add(errorLabel("Search failed: " +
                        (task.getException() != null ? task.getException().getMessage() : "unknown error")));
            }));
            new Thread(task, "memory-search").start();
        });

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("root");
        return scrollPane;
    }

    private VBox memoryCard(IncidentMemory memory, Double score) {
        Label summaryLabel = new Label(memory.getSummary());
        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        HBox topRow = new HBox(8, summaryLabel);
        if (score != null) {
            Label scoreLabel = new Label(String.format(Locale.US, "%.1f%% similar", score * 100));
            scoreLabel.getStyleClass().add("muted-text");
            topRow.getChildren().add(scoreLabel);
        }

        Label rootCauseLabel = new Label("Root cause: " + nullSafe(memory.getRootCause()));
        rootCauseLabel.setWrapText(true);
        rootCauseLabel.setStyle("-fx-font-size: 12px;");

        Label remediationLabel = new Label("Remediation: " + nullSafe(memory.getRemediationTaken()));
        remediationLabel.setWrapText(true);
        remediationLabel.setStyle("-fx-font-size: 12px;");

        Label outcomeBadge = new Label(nullSafe(memory.getOutcome()));
        outcomeBadge.getStyleClass().addAll("status-badge",
                "SUCCESS".equals(memory.getOutcome()) ? "status-resolved" : "status-reopened");

        Label dateLabel = new Label(memory.getCreatedAt() != null ? TIMESTAMP_FORMAT.format(memory.getCreatedAt()) : "");
        dateLabel.getStyleClass().add("muted-text");
        dateLabel.setStyle("-fx-font-size: 10px;");

        HBox bottomRow = new HBox(8, outcomeBadge, dateLabel);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, topRow, rootCauseLabel, remediationLabel, bottomRow);
        card.getStyleClass().add("content-card");
        return card;
    }

    // ---------- Helpers ----------

    private String nullSafe(String value) {
        return value != null ? value : "(none)";
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        return label;
    }

    private Label errorLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #ef4444;");
        return label;
    }
}