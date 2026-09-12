package com.primus.desktop.view;

import com.primus.desktop.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.springframework.security.authentication.BadCredentialsException;


/**
 * Old:  JavaFX/React -> POST /api/auth/login -> AuthController -> JWT
 * New:  This screen -> AuthService.login() -> DesktopSession (no token, no HTTP)
 */
public class LoginView {

    private final AuthService authService;
    private final Runnable onLoginSuccess;

    public LoginView(AuthService authService, Runnable onLoginSuccess) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
    }

    public VBox build() {
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setMaxWidth(220);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setMaxWidth(220);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c0392b;");

        Button loginButton = new Button("Log in");
        loginButton.getStyleClass().add("button-primary");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> {
            try {
                authService.login(usernameField.getText(), passwordField.getText());
                onLoginSuccess.run();
            } catch (BadCredentialsException ex) {
                errorLabel.setText("Invalid username or password.");
            } catch (Exception ex) {
                errorLabel.setText("Login failed: " + ex.getMessage());
            }
        });

        Label title = new Label("Primus Desktop -- Log in");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label hint = new Label("First run? Check the console output for a generated admin login.");
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

        VBox root = new VBox(10, title, usernameField, passwordField, loginButton, errorLabel, hint);
        root.getStyleClass().add("root");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        return root;
    }
}