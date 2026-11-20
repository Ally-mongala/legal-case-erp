package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import za.co.mongala.legal.model.User;
import za.co.mongala.legal.ui.AppContext;

import java.io.IOException;

/**
 * Login screen (FXML: fxml/login.fxml) - manual login, no demo buttons.
 *
 * Fixes implemented per feedback:
 *  - Show/Hide password toggle now works reliably: the visible TextField
 *    overlays the PasswordField inside a StackPane, so toggling never
 *    disturbs the layout and the typed text is preserved.
 *  - Login is fully manual: the demo quick-fill buttons were removed.
 *
 * Credentials are validated through the AuthService (DEMO mode) or the
 * backend /api/auth/login (CONNECTED mode), which returns role-based JWTs.
 */
public class LoginController {

    private final AppContext ctx;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private Label passwordToggle;
    @FXML private Button signInButton;
    @FXML private Label errorLabel;

    public LoginController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        passwordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSignIn();
        });
        emailField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) onSignIn();
        });

        // Show/Hide toggle (both fields are wired, whichever is visible)
        passwordToggle.setOnMouseClicked(e -> togglePasswordVisibility());
        passwordToggle.setFocusTraversable(false);
    }

    /** Swaps the visible password input between hidden (PasswordField) and shown (TextField). */
    private void togglePasswordVisibility() {
        if (passwordVisibleField.isVisible()) {
            // switch back to hidden
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordField.requestFocus();
            passwordToggle.setText("Show");
        } else {
            // switch to shown
            passwordVisibleField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            passwordVisibleField.requestFocus();
            passwordToggle.setText("Hide");
        }
    }

    @FXML
    private void onSignIn() {
        String email = emailField.getText();
        String password = currentPassword();

        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            showError("Please enter your email address and password.");
            return;
        }

        try {
            User user = ctx.authService().authenticate(email.trim(), password);
            if (user == null) {
                showError("Invalid email or password. Check your details and try again.");
                return;
            }
            if (!user.isActive()) {
                showError("This account's access has been revoked by an administrator. Contact the firm.");
                return;
            }
            ctx.auditService().info(user.getName(), "LOGIN",
                    user.getRole().getDisplayName() + " signed in to the system");
            ctx.app().loginUser(user);
        } catch (Exception ex) {
            // CONNECTED mode: backend error (e.g. 403 account disabled) surfaces here
            String msg = ex.getMessage() == null ? "Sign-in failed." : ex.getMessage();
            showError(msg.replaceAll("^API error \\d+: ", ""));
        }
    }

    private String currentPassword() {
        return passwordVisibleField.isVisible() ? passwordVisibleField.getText() : passwordField.getText();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
