package za.co.mongala.legal;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import za.co.mongala.legal.client.*;
import za.co.mongala.legal.model.User;
import za.co.mongala.legal.service.*;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.controller.LoginController;
import za.co.mongala.legal.ui.controller.ShellController;

import java.io.IOException;

/**
 * Entry point of the JavaFX client.
 *
 * Two modes:
 *  - DEMO mode (default):      in-memory simulated services, zero setup.
 *  - CONNECTED mode:           talks to the LegalCase ERP backend over REST.
 *    Activate with  -Dconnect=http://localhost:8080  or env LCE_BACKEND_URL.
 *
 * In CONNECTED mode the shell also shows a status bar hint so the reviewer
 * can see which backend the app is talking to.
 */
public class MainApp extends Application {

    public static final String STYLESHEET = "/css/styles.css";

    private Stage primaryStage;
    private AppContext ctx;
    private ShellController shellController;

    @Override
    public void start(Stage stage) throws IOException {
        this.primaryStage = stage;

        String connectUrl = System.getProperty("connect");
        if (connectUrl == null || connectUrl.isBlank()) {
            connectUrl = System.getenv("LCE_BACKEND_URL");
        }
        boolean remote = connectUrl != null && !connectUrl.isBlank();

        if (remote) {
            ApiClient api = new ApiClient(connectUrl.trim());
            ctx = new AppContext(this, api,
                    new RemoteAuthService(api),
                    new RemoteDataStore(api),
                    new RemoteCalendarSync(api),
                    new RemoteAuditService(api),
                    new RemoteDocumentWorkflow(api));
            if (!api.ping()) {
                System.err.println("[WARN] Backend not reachable at " + api.baseUrl()
                        + " - start it first (mvn -pl backend exec:java)");
            }
        } else {
            DataStore dataStore = new DataStore();
            LegalNlpSimulator nlp = new LegalNlpSimulator();
            AuditService audit = new AuditService(dataStore);
            IAuthService auth = new AuthService();
            ICalendarSyncService calendar = new CalendarSyncService();
            ctx = new AppContext(this, null,
                    auth, dataStore, calendar, audit,
                    new LocalDocumentWorkflow(dataStore, nlp, calendar, audit, () -> ctx.currentUser()));
        }

        configureStage();
        showLogin();

        // Demo / screenshot mode: auto-login and jump straight to a view.
        // Usage: -Ddemo.view=<view-key> -Ddemo.user=<email> [-Ddemo.password=<pw>]
        String demoView = System.getProperty("demo.view");
        if (demoView != null) {
            String demoUserEmail = System.getProperty("demo.user", "ally@mongalalaw.co.za");
            String demoPassword = System.getProperty("demo.password", demoPasswordFor(demoUserEmail));
            Platform.runLater(() -> {
                User u = null;
                if (remote) {
                    u = ctx.authService().authenticate(demoUserEmail, demoPassword);
                } else {
                    u = ctx.authService().findByEmail(demoUserEmail);
                }
                if (u != null) {
                    try {
                        loginUser(u);
                        Object param = null;
                        if ("case_detail".equals(demoView)) {
                            param = ctx.dataStore().getAllCases().get(0);
                        }
                        shellController.showView(demoView, param);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            });
        }
    }

    private static String demoPasswordFor(String email) {
        if (email != null && email.contains("admin")) return "admin123";
        if (email != null && email.contains("@example.com")) return "client123";
        return "lawyer123";
    }

    private void configureStage() {
        primaryStage.setTitle("LegalCase ERP - AI Legal Practice Management");
        primaryStage.setMinWidth(1080);
        primaryStage.setMinHeight(680);
    }

    /* ------------------------------------------------------------------
     * Scene switching
     * ------------------------------------------------------------------ */

    public void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        loader.setControllerFactory(c -> new LoginController(ctx));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1180, 740);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }

    /** Called by LoginController after a successful sign-in. */
    public void loginUser(User user) throws IOException {
        ctx.setCurrentUser(user);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/shell.fxml"));
        loader.setControllerFactory(c -> new ShellController(ctx));
        Parent root = loader.load();
        shellController = loader.getController();
        shellController.init(user);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
    }

    public void logout() {
        ctx.setCurrentUser(null);
        shellController = null;
        try {
            showLogin();
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload login screen", e);
        }
    }

    /** The shell delegates content switching here so views can open other views. */
    public void showView(String viewKey, Object param) {
        if (shellController != null) {
            shellController.showView(viewKey, param);
        }
    }

    public AppContext getContext() { return ctx; }

    public static void main(String[] args) {
        launch(args);
    }
}
