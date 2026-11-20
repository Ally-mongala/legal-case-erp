package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import za.co.mongala.legal.model.Role;
import za.co.mongala.legal.model.User;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.util.List;

/**
 * Admin portal - Users & roles. Revoking a user's access immediately
 * prevents them from logging in (the backend rejects inactive accounts).
 */
public class AdminUsersController {

    private final AppContext ctx;

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> nameCol;
    @FXML private TableColumn<User, String> emailCol;
    @FXML private TableColumn<User, Role> roleCol;
    @FXML private TableColumn<User, Boolean> statusCol;
    @FXML private TableColumn<User, User> actionCol;

    public AdminUsersController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        nameCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getName()));
        emailCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getEmail()));
        roleCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getRole()));
        roleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Role role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) { setGraphic(null); return; }
                Label chip = new Label(role.getDisplayName().toUpperCase());
                chip.getStyleClass().addAll("chip",
                        role == Role.LAWYER ? "chip-blue" : role == Role.CLIENT ? "chip-green" : "chip-amber");
                setGraphic(chip);
            }
        });
        statusCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().isActive()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean active, boolean empty) {
                super.updateItem(active, empty);
                if (empty || active == null) { setGraphic(null); return; }
                Label chip = new Label(active ? "ACTIVE" : "REVOKED");
                chip.getStyleClass().addAll("chip", active ? "chip-green" : "chip-red");
                setGraphic(chip);
            }
        });
        actionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                boolean isAdmin = u.getRole() == Role.ADMIN;
                btn.getStyleClass().setAll("btn-ghost");
                if (isAdmin) {
                    btn.setText("Protected");
                    btn.setDisable(true);
                } else if (u.isActive()) {
                    btn.setText("Revoke access");
                    btn.setOnAction(e -> toggle(u, false));
                } else {
                    btn.setText("Restore access");
                    btn.setOnAction(e -> toggle(u, true));
                }
                setGraphic(btn);
            }
        });
        refresh();
    }

    private void toggle(User u, boolean active) {
        try {
            ctx.dataStore().setUserActive(u.getId(), active);
            ctx.auditService().warning(ctx.currentUser().getName(),
                    active ? "ACCESS_RESTORED" : "ACCESS_REVOKED",
                    (active ? "Access restored for " : "Access revoked for ") + u.getName() + " (" + u.getEmail() + ")");
            Toast.success(ctx, active ? "Access restored for " + u.getName()
                    : "Access revoked for " + u.getName() + " - they can no longer sign in");
            refresh();
        } catch (Exception ex) {
            Toast.error(ctx, "Could not update user: " + ex.getMessage());
        }
    }

    private void refresh() {
        try {
            List<User> users = ctx.dataStore().listUsers();
            userTable.getItems().setAll(users);
        } catch (Exception e) {
            Toast.error(ctx, "Could not load users: " + e.getMessage());
        }
    }
}
