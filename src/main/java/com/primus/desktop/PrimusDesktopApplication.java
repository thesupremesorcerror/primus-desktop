package com.primus.desktop;

import com.primus.core.incident.IncidentService;
import com.primus.desktop.service.AuthService;
import com.primus.desktop.session.DesktopSession;
import com.primus.desktop.view.DashboardView;
import com.primus.desktop.view.EvaluationView;
import com.primus.desktop.view.IncidentDetailView;
import com.primus.desktop.view.KnowledgeExplorerView;
import com.primus.desktop.view.LoginView;
import com.primus.desktop.view.ServiceHealthView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Primus Desktop -- single-process JavaFX + Spring application.
 *
 * There is no embedded servlet container, no REST layer, and no HTTP between
 * this UI and Primus Core: Spring is booted with WebApplicationType.NONE
 * (via PrimusApplicationConfig), and the JavaFX UI calls Spring-managed
 * services directly as Java method calls in the same JVM.
 *
 * This class just wires screens together (login <-> dashboard) and swaps
 * the Stage's Scene; the actual screen content lives in the view/ package.
 */
public class PrimusDesktopApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Stage primaryStage;

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(PrimusApplicationConfig.class)
                .web(WebApplicationType.NONE)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Primus Desktop");
        showLoginScreen();
        primaryStage.show();
    }

    private void showLoginScreen() {
        AuthService authService = springContext.getBean(AuthService.class);
        LoginView loginView = new LoginView(authService, this::showDashboardScreen);
        Scene scene = new Scene(loginView.build(), 420, 320);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void showDashboardScreen() {
        IncidentService incidentService = springContext.getBean(IncidentService.class);
        DesktopSession session = springContext.getBean(DesktopSession.class);
        AuthService authService = springContext.getBean(AuthService.class);

        DashboardView dashboardView = new DashboardView(incidentService, session, authService,
                this::showLoginScreen, this::showIncidentDetailScreen, this::showEvaluationScreen,
                this::showKnowledgeExplorerScreen, this::showServiceHealthScreen);
        Scene scene = new Scene(dashboardView.build(), 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void showIncidentDetailScreen(java.util.UUID incidentId) {
        IncidentService incidentService = springContext.getBean(IncidentService.class);
        DesktopSession session = springContext.getBean(DesktopSession.class);

        IncidentDetailView detailView = new IncidentDetailView(incidentService, session, incidentId, this::showDashboardScreen);
        Scene scene = new Scene(detailView.build(), 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void showEvaluationScreen() {
        com.primus.core.eval.EvaluationService evaluationService = springContext.getBean(com.primus.core.eval.EvaluationService.class);

        EvaluationView evaluationView = new EvaluationView(evaluationService, this::showDashboardScreen);
        Scene scene = new Scene(evaluationView.build(), 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void showKnowledgeExplorerScreen(int initialTabIndex) {
        com.primus.core.knowledge.RetrievalService retrievalService = springContext.getBean(com.primus.core.knowledge.RetrievalService.class);
        com.primus.core.memory.IncidentMemoryService incidentMemoryService = springContext.getBean(com.primus.core.memory.IncidentMemoryService.class);

        KnowledgeExplorerView explorerView = new KnowledgeExplorerView(retrievalService, incidentMemoryService,
                this::showDashboardScreen, initialTabIndex);
        Scene scene = new Scene(explorerView.build(), 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    private void showServiceHealthScreen() {
        com.primus.core.agent.DiagnosticTools diagnosticTools = springContext.getBean(com.primus.core.agent.DiagnosticTools.class);

        ServiceHealthView healthView = new ServiceHealthView(diagnosticTools, this::showDashboardScreen);
        Scene scene = new Scene(healthView.build(), 1100, 680);
        scene.getStylesheets().add(getClass().getResource("/css/primus-dark.css").toExternalForm());
        primaryStage.setScene(scene);
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
    }
}