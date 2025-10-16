package carehome.ui.controller;


// controller for the Shift screen.
import carehome.model.Role;
import carehome.model.Shift;
import carehome.model.Staff;
import carehome.service.CareHome;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;

public class ShiftController {

    // Form fields
    @FXML private ComboBox<String> cmbStaffId;
    @FXML private DatePicker dpStartDate;
    @FXML private TextField txtStartTime;
    @FXML private DatePicker dpEndDate;
    @FXML private TextField txtEndTime;
    @FXML private Button btnAdd, btnClear;

    // Table
    @FXML private TableView<Shift> tblShifts;
    @FXML private TableColumn<Shift,String> colStaffId, colStart, colEnd;

    @FXML private Label lblInfo;

    // Context
    private CareHome careHome;
    private Staff currentUser;
    private MainController main;

    private final ObservableList<Shift> shiftData = FXCollections.observableArrayList();

    public void setContext(CareHome ch, Staff user, MainController main) {
        this.careHome = ch;
        this.currentUser = user;
        this.main = main;

        // manager-only UI guard
        boolean allow = (user != null && user.getRole() == Role.MANAGER);
        btnAdd.setDisable(!allow);

        populateStaffIds();
        refreshTable();
        info("Signed in as: " + user.getName() + " (" + user.getRole() + ")");
    }

    @FXML
    public void initialize() {
        // Table columns
        colStaffId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStaffId()));
        colStart.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getStart())));
        colEnd.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getEnd())));
        tblShifts.setItems(shiftData);

        // Defaults
        dpStartDate.setValue(LocalDate.now());
        dpEndDate.setValue(LocalDate.now());
        txtStartTime.setText("08:00");
        txtEndTime.setText("16:00");
    }

    private void populateStaffIds() {
        cmbStaffId.getItems().setAll(
                careHome.getStaffById().values().stream()
                        .sorted(Comparator.comparing(Staff::getId))
                        .map(Staff::getId)
                        .toList()
        );
    }

    @FXML
    private void handleAddShift() {
        // hard guard from MainController too
        if (main != null && !main.requireManager()) return;

        try {
            String sid = cmbStaffId.getValue();
            LocalDate sd = dpStartDate.getValue();
            LocalDate ed = dpEndDate.getValue();
            LocalTime st = parseTime(txtStartTime.getText());
            LocalTime et = parseTime(txtEndTime.getText());

            if (sid == null || sd == null || ed == null || st == null || et == null) {
                error("Please fill all fields (staff, start/end date & time).");
                return;
            }

            LocalDateTime start = LocalDateTime.of(sd, st);
            LocalDateTime end   = LocalDateTime.of(ed, et);
            if (!end.isAfter(start)) {
                error("End must be after Start.");
                return;
            }

            // call backend (validates nurse/doctor rules)
            Shift shift = new Shift(sid, start, end);
            careHome.allocateShift(currentUser.getId(), shift);

            // refresh UI
            refreshTable();
            handleClear();

            // success popup
            String msg = String.format(
                    "Shift for %s allocated:\n%s  →  %s",
                    sid,
                    shift.getStart().toString(),
                    shift.getEnd().toString()
            );

            if (main != null) {
                main.showInfo("Shift Allocated", msg);
            } else {
                new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK) {{
                    setHeaderText("Shift Allocated");
                }}.showAndWait();
            }

            info("Allocated shift for " + sid + ": " + start + " → " + end);

        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }
    @FXML
    private void handleClear() {
        cmbStaffId.getSelectionModel().clearSelection();
        dpStartDate.setValue(LocalDate.now());
        txtStartTime.setText("08:00");
        dpEndDate.setValue(LocalDate.now());
        txtEndTime.setText("16:00");
    }

    private void refreshTable() {
        shiftData.setAll(careHome.getShifts());
        // sort by start time
        shiftData.sort(Comparator.comparing(Shift::getStart));
    }

    private LocalTime parseTime(String s) {
        try {
            if (s == null) return null;
            String t = s.trim();
            if (!t.matches("\\d{2}:\\d{2}")) return null;
            String[] parts = t.split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            return null;
        }
    }

    private void info(String m) { lblInfo.setText(m); }
    private void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.setHeaderText("Shift Allocation Error");
        a.showAndWait();
    }
}
