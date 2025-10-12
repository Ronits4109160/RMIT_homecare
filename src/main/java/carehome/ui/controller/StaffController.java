package carehome.ui.controller;

import carehome.model.Role;
import carehome.model.Staff;
import carehome.service.CareHome;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Comparator;

public class StaffController {

    // UI
    @FXML private TableView<Staff> staffTable;
    @FXML private TableColumn<Staff, String> colId;
    @FXML private TableColumn<Staff, String> colName;
    @FXML private TableColumn<Staff, String> colRole;
    @FXML private TableColumn<Staff, String> colUsername;

    @FXML private TextField txtId, txtName, txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private ChoiceBox<Role> choiceRole;
    @FXML private Button btnSave, btnClear;
    @FXML private Label lblInfo;

    // Context
    private CareHome careHome;
    private Staff currentUser;
    private MainController main;   // to use role guards + alert helpers

    private final ObservableList<Staff> staffData = FXCollections.observableArrayList();

    /** Preferred setter: called by MainController after load */
    public void setContext(CareHome ch, Staff user, MainController main) {
        this.careHome = ch;
        this.currentUser = user;
        this.main = main;

        refreshTable();
        lblInfo.setText(user != null ? "Signed in as: " + user.getName() + " (" + user.getRole() + ")" : "");

        // Only manager can add/update staff (UI guard)
        boolean allow = (user != null && user.getRole() == Role.MANAGER);
        btnSave.setDisable(!allow);
    }

    /** Backward compatible setter. */
    public void setContext(CareHome ch, Staff user) {
        setContext(ch, user, null);
    }

    @FXML
    public void initialize() {
        // table columns
        colId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colRole.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRole().toString()));
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getUsername() == null ? "" : d.getValue().getUsername()
        ));
        staffTable.setItems(staffData);

        // table row click -populate form
        staffTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, s) -> {
            if (s != null) {
                txtId.setText(s.getId());
                txtName.setText(s.getName());
                choiceRole.setValue(s.getRole());
                txtUsername.setText(s.getUsername() == null ? "" : s.getUsername());
                txtPassword.clear();
            }
        });

        // roles
        choiceRole.setItems(FXCollections.observableArrayList(Role.values()));

        // actions
        btnSave.setOnAction(e -> saveStaff());
        btnClear.setOnAction(e -> clearForm());
    }

    private void saveStaff() {
        // hard guard
        if (main != null && !main.requireManager()) return;

        try {
            if (careHome == null) throw new IllegalStateException("CareHome not set");

            String id = txtId.getText().trim();
            String name = txtName.getText().trim();
            Role role = choiceRole.getValue();
            String username = txtUsername.getText().trim();
            String password = txtPassword.getText();

            if (id.isEmpty() || name.isEmpty() || role == null)
                throw new IllegalArgumentException("ID, Name and Role are required.");

            if (username.isEmpty())
                throw new IllegalArgumentException("Username is required.");

            Staff s = new Staff(id, name, role);

            // actor is the current logged-in user
            String actorId = (currentUser != null) ? currentUser.getId() : null;
            if (actorId == null) throw new IllegalStateException("No logged-in user context.");

            careHome.addOrUpdateStaff(actorId, s, username, password);

            refreshTable();
            clearForm();
            info("Saved staff: " + s.getId());
        } catch (Exception ex) {
            error("Error: " + ex.getMessage());
        }
    }

    private void refreshTable() {
        if (careHome == null) return;
        staffData.setAll(careHome.getStaffById().values().stream()
                .sorted(Comparator.comparing(Staff::getId))
                .toList());
    }

    private void clearForm() {
        txtId.clear();
        txtName.clear();
        txtUsername.clear();
        txtPassword.clear();
        choiceRole.setValue(null);
        staffTable.getSelectionModel().clearSelection();
    }

    private void info(String m) { lblInfo.setText(m); }
    private void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.showAndWait();
    }
}
