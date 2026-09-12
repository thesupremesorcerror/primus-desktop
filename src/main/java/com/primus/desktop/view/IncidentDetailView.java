package com.primus.desktop.view;

import com.primus.core.agent.AgentFinding;
import com.primus.core.incident.Incident;
import com.primus.core.incident.IncidentService;
import com.primus.core.remediation.RemediationCandidate;
import com.primus.desktop.session.DesktopSession;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Old:    React incident detail page -> several REST calls -> IncidentController
 * New:    This view -> IncidentService methods (direct calls, same JVM)
 *
 * Honesty notes (see /areas/primus-desktop.md for the full analysis):
 * - The "pipeline" only has as many distinct stages as Incident.status
 *   actually has (OPEN/DIAGNOSING/AWAITING_APPROVAL/RESOLVED/REOPENED) --
 *   evidence collection, hypothesis generation, and diagnosis synthesis all
 *   happen inside one orchestrator call and share the single "DIAGNOSING"
 *   status, so they're shown as one active stage, not three fake ones.
 * - Each agent's real finding (LogAgent/MetricsAgent/RagAgent) is shown with
 *   its own real confidence -- these stand in for "hypotheses" since the
 *   synthesized diagnosis itself has no separate confidence score stored.
 * - Remediation candidates are only ever created for HIGH-risk actions that
 *   needed approval -- an empty list here is normal, not a bug.
 */
public class IncidentDetailView {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> PIPELINE_STAGES = List.of("OPEN", "DIAGNOSING", "AWAITING_APPROVAL", "RESOLVED");

    private final IncidentService incidentService;
    private final DesktopSession session;
    private final UUID incidentId;
    private final Runnable onBack;

    private VBox contentBox;
    private Label statusMessage;

    public IncidentDetailView(IncidentService incidentService, DesktopSession session,
                              UUID incidentId, Runnable onBack) {
        this.incidentService = incidentService;
        this.session = session;
        this.incidentId = incidentId;
        this.onBack = onBack;
    }

    public BorderPane build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        Button backButton = new Button("< Back to Dashboard");
        backButton.setOnAction(e -> onBack.run());

        statusMessage = new Label();

        HBox header = new HBox(16, backButton, statusMessage);
        header.getStyleClass().add("app-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        root.setTop(header);

        contentBox = new VBox(16);
        contentBox.setPadding(new Insets(20, 24, 20, 24));

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("root");
        root.setCenter(scrollPane);

        refresh();
        return root;
    }

    private void refresh() {
        Incident incident = incidentService.getIncident(incidentId);
        List<AgentFinding> findings = incidentService.getEvidence(incidentId);
        List<RemediationCandidate> candidates = incidentService.getCandidates(incidentId);

        contentBox.getChildren().clear();
        contentBox.getChildren().add(buildSummaryCard(incident));
        contentBox.getChildren().add(buildPipelineCard(incident));
        contentBox.getChildren().add(buildActionsCard(incident));
        contentBox.getChildren().add(buildDiagnosisCard(incident));
        contentBox.getChildren().add(buildFindingsCard(findings));
        contentBox.getChildren().add(buildRemediationCard(incident, candidates));
    }

    // ---------- Summary ----------

    private VBox buildSummaryCard(Incident incident) {
        Label title = new Label(incident.getTitle());
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        title.setWrapText(true);

        Label severityBadge = badge(incident.getSeverity());
        Label statusBadge = badge(incident.getStatus());
        HBox badges = new HBox(8, severityBadge, statusBadge);

        Label serviceLabel = new Label("Service: " + incident.getAffectedService());
        serviceLabel.getStyleClass().add("muted-text");
        Label createdLabel = new Label("Created: " + fmt(incident.getCreatedAt()) +
                (incident.getResolvedAt() != null ? "   |   Resolved: " + fmt(incident.getResolvedAt()) : ""));
        createdLabel.getStyleClass().add("muted-text");

        VBox card = new VBox(8, title, badges, serviceLabel, createdLabel);
        card.getStyleClass().add("content-card");
        return card;
    }

    // ---------- Pipeline ----------

    private VBox buildPipelineCard(Incident incident) {
        Label heading = new Label("Investigation Pipeline");
        heading.getStyleClass().add("card-heading");

        boolean reopened = "REOPENED".equals(incident.getStatus());
        int currentIndex = reopened ? PIPELINE_STAGES.indexOf("DIAGNOSING") : PIPELINE_STAGES.indexOf(incident.getStatus());

        HBox stagesRow = new HBox(4);
        for (int i = 0; i < PIPELINE_STAGES.size(); i++) {
            String stage = PIPELINE_STAGES.get(i);
            Label stageLabel = new Label(friendlyStageName(stage));
            stageLabel.getStyleClass().add("status-badge");
            if (currentIndex < 0) {
                stageLabel.getStyleClass().add("status-open");
            } else if (i < currentIndex) {
                stageLabel.getStyleClass().add("status-resolved"); // completed = green
            } else if (i == currentIndex) {
                stageLabel.getStyleClass().add("status-awaiting_approval"); // active = amber
            } else {
                stageLabel.getStyleClass().add("muted-text");
            }
            stagesRow.getChildren().add(stageLabel);
            if (i < PIPELINE_STAGES.size() - 1) {
                Label arrow = new Label("\u2192");
                arrow.getStyleClass().add("muted-text");
                stagesRow.getChildren().add(arrow);
            }
        }

        VBox card = new VBox(10, heading, stagesRow);
        card.getStyleClass().add("content-card");

        if (reopened) {
            Label reopenedNote = new Label("REOPENED -- the last remediation attempt did not verify as healthy; back in diagnosis.");
            reopenedNote.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
            card.getChildren().add(reopenedNote);
        }
        Label caption = new Label("\"Diagnosing\" covers evidence collection, agent findings, and diagnosis synthesis in one step -- see below for the real breakdown.");
        caption.getStyleClass().add("muted-text");
        caption.setStyle("-fx-font-size: 10px;");
        caption.setWrapText(true);
        card.getChildren().add(caption);

        return card;
    }

    private String friendlyStageName(String stage) {
        return switch (stage) {
            case "OPEN" -> "Detected";
            case "DIAGNOSING" -> "Investigating";
            case "AWAITING_APPROVAL" -> "Awaiting Approval";
            case "RESOLVED" -> "Resolved";
            default -> stage;
        };
    }

    // ---------- Actions ----------

    private VBox buildActionsCard(Incident incident) {
        Button investigateButton = new Button("Investigate (Multi-Agent)");
        investigateButton.getStyleClass().add("button-primary");
        investigateButton.setOnAction(e -> {
            incidentService.investigateIncident(incidentId);
            statusMessage.setText("Investigation complete.");
            refresh();
        });

        Button diagnoseButton = new Button("Quick Diagnose (LLM + RAG only)");
        diagnoseButton.setOnAction(e -> {
            incidentService.diagnoseIncident(incidentId);
            statusMessage.setText("Quick diagnosis complete.");
            refresh();
        });

        Label caption = new Label("Investigate runs the full multi-agent pipeline (Log/Metrics/Rag agents + diagnosis + remediation proposal). Quick Diagnose only asks the LLM for a root-cause guess using RAG context, with no agent findings or remediation.");
        caption.getStyleClass().add("muted-text");
        caption.setStyle("-fx-font-size: 10px;");
        caption.setWrapText(true);

        HBox buttons = new HBox(10, investigateButton, diagnoseButton);
        VBox card = new VBox(8, buttons, caption);
        card.getStyleClass().add("content-card");
        return card;
    }

    // ---------- Diagnosis ----------

    private VBox buildDiagnosisCard(Incident incident) {
        Label heading = new Label("Root Cause Diagnosis");
        heading.getStyleClass().add("card-heading");

        String text = incident.getRootCauseGuess() != null && !incident.getRootCauseGuess().isBlank()
                ? incident.getRootCauseGuess()
                : "Not yet diagnosed -- run Investigate or Quick Diagnose above.";
        Label content = new Label(text);
        content.setWrapText(true);
        content.getStyleClass().addAll(incident.getRootCauseGuess() == null ? List.of("muted-text") : List.of());

        VBox card = new VBox(8, heading, content);
        card.getStyleClass().add("content-card");
        return card;
    }

    // ---------- Agent findings / evidence ----------

    private VBox buildFindingsCard(List<AgentFinding> findings) {
        Label heading = new Label("Agent Findings & Evidence");
        heading.getStyleClass().add("card-heading");

        VBox card = new VBox(10, heading);
        card.getStyleClass().add("content-card");

        if (findings == null || findings.isEmpty()) {
            Label none = new Label("No agent findings yet -- run Investigate to generate them.");
            none.getStyleClass().add("muted-text");
            card.getChildren().add(none);
            return card;
        }

        for (AgentFinding finding : findings) {
            Label agentName = new Label(finding.agent());
            agentName.setStyle("-fx-font-weight: bold; -fx-text-fill: #22d3ee;");

            Label confidence = new Label(Math.round(finding.confidence() * 100) + "% confidence");
            confidence.getStyleClass().add("muted-text");
            confidence.setStyle("-fx-font-size: 11px;");

            HBox findingHeader = new HBox(10, agentName, confidence);
            findingHeader.setAlignment(Pos.CENTER_LEFT);

            Label summary = new Label(finding.summary());
            summary.setWrapText(true);
            summary.setStyle("-fx-font-size: 12px;");

            VBox findingBox = new VBox(4, findingHeader, summary);

            if (finding.evidence() != null && !finding.evidence().isEmpty()) {
                VBox evidenceList = new VBox(2);
                for (AgentFinding.Evidence ev : finding.evidence()) {
                    Label evLabel = new Label("\u2022 [" + ev.type() + "] " + ev.detail() + " (source: " + ev.source() + ")");
                    evLabel.getStyleClass().add("muted-text");
                    evLabel.setStyle("-fx-font-size: 11px;");
                    evLabel.setWrapText(true);
                    evidenceList.getChildren().add(evLabel);
                }
                findingBox.getChildren().add(evidenceList);
            }

            findingBox.setPadding(new Insets(8, 0, 8, 0));
            card.getChildren().add(findingBox);
        }

        return card;
    }

    // ---------- Remediation ----------

    private VBox buildRemediationCard(Incident incident, List<RemediationCandidate> candidates) {
        Label heading = new Label("Remediation");
        heading.getStyleClass().add("card-heading");

        VBox card = new VBox(10, heading);
        card.getStyleClass().add("content-card");

        if (candidates == null || candidates.isEmpty()) {
            Label none = new Label("No remediation candidates -- either not yet investigated, or the last action was LOW/MEDIUM risk and auto-executed without needing a candidate record.");
            none.getStyleClass().add("muted-text");
            none.setWrapText(true);
            card.getChildren().add(none);
            return card;
        }

        for (RemediationCandidate candidate : candidates) {
            Label actionLabel = new Label(candidate.getActionType() + " -> " + candidate.getServiceName());
            actionLabel.setStyle("-fx-font-weight: bold;");

            Label riskBadge = badge(candidate.getRiskLevel());
            Label statusBadge = badge(candidate.getStatus());
            HBox topRow = new HBox(8, actionLabel, riskBadge, statusBadge);
            topRow.setAlignment(Pos.CENTER_LEFT);

            Label recoveryLabel = new Label(candidate.getExpectedRecoveryPct() != null
                    ? "Expected recovery: " + candidate.getExpectedRecoveryPct() + "%"
                    : "");
            recoveryLabel.getStyleClass().add("muted-text");
            recoveryLabel.setStyle("-fx-font-size: 11px;");

            Label whyHeading = new Label("Why Primus proposed this:");
            whyHeading.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            Label rationale = new Label(candidate.getRationale() != null ? candidate.getRationale() : "(no rationale recorded)");
            rationale.setWrapText(true);
            rationale.setStyle("-fx-font-size: 12px;");

            VBox candidateBox = new VBox(4, topRow, recoveryLabel, whyHeading, rationale);
            candidateBox.setPadding(new Insets(8, 0, 8, 0));

            if ("PENDING".equals(candidate.getStatus())) {
                Button approveButton = new Button("Approve");
                approveButton.getStyleClass().add("button-primary");
                approveButton.setOnAction(e -> {
                    try {
                        incidentService.approveCandidate(incidentId, candidate.getId());
                        statusMessage.setText("Approved and executed.");
                        refresh();
                    } catch (AccessDeniedException ex) {
                        statusMessage.setText("Denied -- your role (" + session.getCurrentRole() + ") cannot approve remediation.");
                    }
                });

                Button rejectButton = new Button("Reject");
                rejectButton.getStyleClass().add("button-danger");
                rejectButton.setOnAction(e -> {
                    try {
                        incidentService.rejectCandidate(incidentId, candidate.getId());
                        statusMessage.setText("Rejected.");
                        refresh();
                    } catch (AccessDeniedException ex) {
                        statusMessage.setText("Denied -- your role (" + session.getCurrentRole() + ") cannot reject remediation.");
                    }
                });

                HBox actionButtons = new HBox(10, approveButton, rejectButton);
                candidateBox.getChildren().add(actionButtons);
            }

            card.getChildren().add(candidateBox);
        }

        return card;
    }

    // ---------- Helpers ----------

    private Label badge(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("status-badge", "status-" + text.toLowerCase());
        return label;
    }

    private String fmt(java.time.Instant instant) {
        return instant != null ? TIMESTAMP_FORMAT.format(instant) : "";
    }
}