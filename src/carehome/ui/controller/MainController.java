package carehome.ui.controller;

import carehome.model.Role;
import carehome.model.Staff;
import carehome.service.CareHome;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainController {

    @FXML private Button btnStaff;
    @FXML private Button btnShifts;      // NEW
    @FXML private Button btnResidents;
    @FXML private Button btnLogs;
    @FXML private Button btnArchive;

    @FXML private Button btnLogout;
    @FXML private Button btnMeds;
    @FXML private Label lblUser;
    @FXML private StackPane contentArea;

    private CareHome careHome;
    private Staff current;

    /** Called by LoginController right after login */
    public void setContext(CareHome careHome, Staff current) {
        this.careHome = careHome;
        this.current = current;

        if (lblUser != null && current != null) {
            lblUser.setText(current.getName() + " (" + current.getRole() + ")");
        }

        // Hook up sidebar buttons
        if (btnStaff != null)     btnStaff.setOnAction(e -> switchView("staff"));
        if (btnShifts != null)    btnShifts.setOnAction(e -> switchView("shifts"));  // NEW
        if (btnResidents != null) btnResidents.setOnAction(e -> switchView("residents"));
        if (btnLogs != null)      btnLogs.setOnAction(e -> switchView("logs"));
        if (btnArchive != null)   btnArchive.setOnAction(e -> switchView("archive"));
        if (btnMeds != null) btnMeds.setOnAction(e -> switchView("meds"));


        applyRoleMenu();
    }

    private void applyRoleMenu() {
        Role r = current.getRole();
        boolean manager = r == Role.MANAGER;

        if (btnStaff  != null) btnStaff.setDisable(!manager);
        if (btnShifts != null) btnShifts.setDisable(!manager);  // manager-only
    }

    public boolean requireManager() {
        if (current.getRole() != Role.MANAGER) {
            showError("Unauthorized", "Only manager can perform this action.");
            return false;
        }
        return true;
    }

    public Staff getCurrent()     { return current; }
    public CareHome getCareHome() { return careHome; }

    public void showInfo(String header, String msg) {
        var a = new Alert(Alert.AlertType.INFORMATION, msg);
        a.setHeaderText(header);
        a.showAndWait();
    }
    public void showError(String header, String msg) {
        var a = new Alert(Alert.AlertType.ERROR, msg);
        a.setHeaderText(header);
        a.showAndWait();
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/LoginView.fxml"));
            Scene scene = new Scene(loader.load());

            carehome.ui.controller.LoginController lc = loader.getController();
            lc.setCareHome(careHome);

            Stage stage = (Stage) btnLogout.getScene().getWindow();
            stage.setTitle("RMIT Care Home — Login");
            stage.setScene(scene);
            stage.show();

        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Error", ex.getMessage());
        }
    }

    private void switchView(String target) {
        try {
            switch (target) {
                case "staff" -> {
                    if (!requireManager()) return;
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/StaffView.fxml"));
                    Node root = loader.load();
                    var sc = loader.getController();
                    if (sc instanceof carehome.ui.controller.StaffController c) {
                        c.setContext(careHome, current, this);
                    }
                    contentArea.getChildren().setAll(root);
                }
                case "shifts" -> {  // NEW
                    if (!requireManager()) return;
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/ShiftView.fxml"));
                    Node root = loader.load();
                    var sc = loader.getController();
                    if (sc instanceof carehome.ui.controller.ShiftController c) {
                        c.setContext(careHome, current, this);
                    }
                    contentArea.getChildren().setAll(root);
                }
                case "residents" -> {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/BedsView.fxml"));
                    Node root = loader.load();
                    var bc = loader.getController();
                    if (bc instanceof carehome.ui.controller.BedsController c) {
                        c.setContext(careHome, current, this);
                    }
                    contentArea.getChildren().setAll(root);
                }
                case "meds" -> {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/MedsView.fxml"));
                    Node root = loader.load();
                    var c = loader.getController();
                    if (c instanceof carehome.ui.controller.MedsController mc) {
                        mc.setContext(careHome, current, this);
                    }
                    contentArea.getChildren().setAll(root);
                }
                case "logs" -> {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/carehome/ui/LogsView.fxml"));
                    Node root = loader.load();
                    var c = loader.getController();
                    if (c instanceof carehome.ui.controller.LogsController lc) {
                        lc.setContext(careHome, current);
                    }
                    contentArea.getChildren().setAll(root);
                }
                default -> {
                    Label l = new Label(target + " — (TODO)");
                    l.setStyle("-fx-font-size:20; -fx-font-weight:bold;");
                    contentArea.getChildren().setAll(l);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Load Error", ex.getMessage());
        }
    }
}
