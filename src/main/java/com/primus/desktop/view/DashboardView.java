package com.primus.desktop.view;

import com.primus.core.incident.Incident;
import com.primus.core.incident.IncidentService;
import com.primus.desktop.service.AuthService;
import com.primus.desktop.session.DesktopSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Old:    React Dashboard.tsx -> GET /api/incidents -> IncidentController
 * New:    This view -> IncidentService.getAllIncidents() (direct method call)
 *
 * Dark AI-command-center styling (see primus-dark.css). Every number shown
 * here is real: incident counts come from the actual DB rows, and the
 * "Recently Resolved" panel uses the real resolvedAt timestamp -- there is
 * no fabricated activity feed or made-up metric anywhere on this screen.
 */
public class DashboardView {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final IncidentService incidentService;
    private final DesktopSession session;
    private final AuthService authService;
    private final Runnable onLogout;
    private final Consumer<java.util.UUID> onIncidentSelected;
    private final Runnable onEvaluationSelected;
    private final Consumer<Integer> onKnowledgeExplorerSelected;
    private final Runnable onServiceHealthSelected;

    private TableView<Incident> table;
    private VBox recentResolvedList;
    private Label totalCard, openCard, awaitingCard, resolvedCard;
    private Label actionResultLabel;

    public DashboardView(IncidentService incidentService, DesktopSession session,
                         AuthService authService, Runnable onLogout,
                         Consumer<java.util.UUID> onIncidentSelected,
                         Runnable onEvaluationSelected,
                         Consumer<Integer> onKnowledgeExplorerSelected,
                         Runnable onServiceHealthSelected) {
        this.incidentService = incidentService;
        this.session = session;
        this.authService = authService;
        this.onLogout = onLogout;
        this.onIncidentSelected = onIncidentSelected;
        this.onEvaluationSelected = onEvaluationSelected;
        this.onKnowledgeExplorerSelected = onKnowledgeExplorerSelected;
        this.onServiceHealthSelected = onServiceHealthSelected;
    }

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setLeft(buildSidebar());
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        refresh();
        return root;
    }

    // ---------- Sidebar ----------

    private VBox buildSidebar() {
        Label logo = new Label("PRIMUS");
        logo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #22d3ee;");
        Label logoSub = new Label("AIOps Command Center");
        logoSub.getStyleClass().add("muted-text");
        logoSub.setStyle("-fx-font-size: 10px;");

        VBox logoBox = new VBox(2, logo, logoSub);
        logoBox.setPadding(new Insets(20, 16, 20, 16));

        Button dashboardNav = navItem("Dashboard", true);
        Button evalNav = navItem("Evaluation", false);
        evalNav.setOnAction(e -> onEvaluationSelected.run());
        Button knowledgeNav = navItem("Knowledge Base", false);
        knowledgeNav.setOnAction(e -> onKnowledgeExplorerSelected.accept(0));
        Button memoryNav = navItem("Incident Memory", false);
        memoryNav.setOnAction(e -> onKnowledgeExplorerSelected.accept(1));
        Button healthNav = navItem("Service Health", false);
        healthNav.setOnAction(e -> onServiceHealthSelected.run());

        VBox nav = new VBox(4, dashboardNav, evalNav, knowledgeNav, memoryNav, healthNav);
        nav.setPadding(new Insets(0, 8, 0, 8));

        VBox sidebar = new VBox(logoBox, nav);
        sidebar.getStyleClass().add("app-sidebar");
        sidebar.setPrefWidth(200);
        return sidebar;
    }

    private Button navItem(String label, boolean selected) {
        Button button = new Button(label);
        button.getStyleClass().add(selected ? "nav-item-selected" : "nav-item");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private void showComingSoon(String featureName) {
        actionResultLabel.setText(featureName + " -- coming in the next phase.");
        actionResultLabel.getStyleClass().setAll("muted-text");
    }

    // ---------- Header ----------

    private BorderPane buildHeader() {
        Label title = new Label("Primus Command Center");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Real-time AIOps incident investigation");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        Label userLabel = new Label(session.getCurrentUsername() + " (" + session.getCurrentRole() + ")");
        userLabel.getStyleClass().add("muted-text");

        Button logoutButton = new Button("Log out");
        logoutButton.getStyleClass().add("button-danger");
        logoutButton.setOnAction(e -> {
            authService.logout();
            onLogout.run();
        });

        HBox right = new HBox(16, userLabel, logoutButton);
        right.setAlignment(Pos.CENTER_RIGHT);

        BorderPane header = new BorderPane();
        header.getStyleClass().add("app-header");
        header.setLeft(titleBox);
        header.setRight(right);
        header.setPadding(new Insets(16, 24, 16, 24));
        BorderPane.setAlignment(titleBox, Pos.CENTER_LEFT);
        BorderPane.setAlignment(right, Pos.CENTER_RIGHT);
        return header;
    }

    // ---------- Center content ----------

    private VBox buildCenter() {
        HBox statCards = new HBox(16);
        totalCard = statValueLabel();
        openCard = statValueLabel();
        awaitingCard = statValueLabel();
        resolvedCard = statValueLabel();
        statCards.getChildren().addAll(
                statCard("Total Incidents", totalCard),
                statCard("Open", openCard),
                statCard("Awaiting Approval", awaitingCard),
                statCard("Resolved", resolvedCard)
        );

        table = buildTable();
        Label tableHint = new Label("Double-click a row to view its investigation details.");
        tableHint.getStyleClass().add("muted-text");
        tableHint.setStyle("-fx-font-size: 10px;");
        VBox tableWithHint = new VBox(6, table, tableHint);
        VBox incidentsCard = contentCard("Active Incidents", tableWithHint);
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox.setHgrow(incidentsCard, Priority.ALWAYS);

        recentResolvedList = new VBox(8);
        VBox recentCard = contentCard("Recently Resolved", recentResolvedList);
        recentCard.setPrefWidth(280);

        HBox mainRow = new HBox(16, incidentsCard, recentCard);
        VBox.setVgrow(mainRow, Priority.ALWAYS);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refresh());

        Button adminActionButton = new Button("Run Eval Cleanup (ADMIN only)");
        adminActionButton.getStyleClass().add("button-primary");
        adminActionButton.setOnAction(e -> runEvalCleanup());

        actionResultLabel = new Label();

        HBox actionsBar = new HBox(10, refreshButton, adminActionButton, actionResultLabel);
        actionsBar.setAlignment(Pos.CENTER_LEFT);

        VBox center = new VBox(16, statCards, mainRow, actionsBar);
        center.setPadding(new Insets(20, 24, 20, 24));
        VBox.setVgrow(mainRow, Priority.ALWAYS);
        return center;
    }

    private Label statValueLabel() {
        Label label = new Label("0");
        label.getStyleClass().add("stat-card-value");
        return label;
    }

    private VBox statCard(String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.getStyleClass().add("stat-card-label");
        VBox card = new VBox(6, label, valueLabel);
        card.getStyleClass().add("stat-card");
        card.setPrefWidth(180);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox contentCard(String heading, javafx.scene.Node content) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("card-heading");
        VBox card = new VBox(10, headingLabel, content);
        card.getStyleClass().add("content-card");
        return card;
    }

    private TableView<Incident> buildTable() {
        TableView<Incident> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tableView.getColumns().add(stringColumn("Title", 240, Incident::getTitle));
        tableView.getColumns().add(statusColumn("Severity", 90, Incident::getSeverity));
        tableView.getColumns().add(statusColumn("Status", 150, Incident::getStatus));
        tableView.getColumns().add(stringColumn("Affected Service", 150, Incident::getAffectedService));
        tableView.getColumns().add(stringColumn("Created At", 140,
                i -> i.getCreatedAt() != null ? TIMESTAMP_FORMAT.format(i.getCreatedAt()) : ""));

        tableView.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Incident> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onIncidentSelected.accept(row.getItem().getId());
                }
            });
            return row;
        });

        return tableView;
    }

    private TableColumn<Incident, String> stringColumn(String label, double width, Function<Incident, String> extractor) {
        TableColumn<Incident, String> column = new TableColumn<>(label);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(
                extractor.apply(data.getValue()) != null ? extractor.apply(data.getValue()) : ""));
        return column;
    }

    /** Same as stringColumn, but renders the value as a colored status-badge pill (see primus-dark.css). */
    private TableColumn<Incident, String> statusColumn(String label, double width, Function<Incident, String> extractor) {
        TableColumn<Incident, String> column = stringColumn(label, width, extractor);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null || value.isBlank()) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(value);
                    badge.getStyleClass().addAll("status-badge", "status-" + value.toLowerCase());
                    setGraphic(badge);
                }
            }
        });
        return column;
    }

    private void refresh() {
        List<Incident> incidents = incidentService.getAllIncidents();

        ObservableList<Incident> rows = FXCollections.observableArrayList(incidents);
        rows.sort(Comparator.comparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        table.setItems(rows);

        Map<String, Long> byStatus = incidents.stream()
                .collect(Collectors.groupingBy(Incident::getStatus, Collectors.counting()));
        totalCard.setText(String.valueOf(incidents.size()));
        openCard.setText(String.valueOf(byStatus.getOrDefault("OPEN", 0L)));
        awaitingCard.setText(String.valueOf(byStatus.getOrDefault("AWAITING_APPROVAL", 0L)));
        resolvedCard.setText(String.valueOf(byStatus.getOrDefault("RESOLVED", 0L)));

        recentResolvedList.getChildren().clear();
        incidents.stream()
                .filter(i -> i.getResolvedAt() != null)
                .sorted(Comparator.comparing(Incident::getResolvedAt).reversed())
                .limit(8)
                .forEach(i -> {
                    Label titleLabel = new Label(i.getTitle());
                    titleLabel.setWrapText(true);
                    titleLabel.setStyle("-fx-font-size: 12px;");
                    Label timeLabel = new Label(TIMESTAMP_FORMAT.format(i.getResolvedAt()));
                    timeLabel.getStyleClass().add("muted-text");
                    timeLabel.setStyle("-fx-font-size: 10px;");
                    VBox entry = new VBox(2, titleLabel, timeLabel);
                    recentResolvedList.getChildren().add(entry);
                });
        if (recentResolvedList.getChildren().isEmpty()) {
            Label none = new Label("No resolved incidents yet.");
            none.getStyleClass().add("muted-text");
            recentResolvedList.getChildren().add(none);
        }
    }

    private void runEvalCleanup() {
        try {
            var result = incidentService.cleanupEvalIncidents();
            actionResultLabel.setText("Allowed -- deleted " + result.get("deletedCount") + " eval incident(s).");
            actionResultLabel.setStyle("-fx-text-fill: #22c55e;");
            refresh();
        } catch (AccessDeniedException ex) {
            actionResultLabel.setText("Denied -- your role (" + session.getCurrentRole() + ") is not ADMIN.");
            actionResultLabel.setStyle("-fx-text-fill: #ef4444;");
        }
    }
}