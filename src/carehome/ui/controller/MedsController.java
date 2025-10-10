package carehome.ui.controller;

import carehome.exception.NotFoundException;
import carehome.model.*;
import carehome.service.CareHome;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class MedsController {

    @FXML private Label lblUser, lblResident, lblGender, lblAge, lblInfo;
    @FXML private TextField txtBedId;

    @FXML private TableView<Prescription> tblPrescriptions;
    @FXML private TableColumn<Prescription,String> colPId, colPCreated, colPDoctor, colPMeds;

    @FXML private TableView<Administration> tblAdmins;
    @FXML private TableColumn<Administration,String> colATime, colANurse, colAPresc, colAMed, colANotes;

    // Doctor form
    @FXML private TextField txtPrescId, txtMedName, txtDose, txtFreq;
    @FXML private Button btnAddPresc;

    // Nurse form
    @FXML private TextField txtAdminPrescId, txtAdminMed, txtAdminDose, txtAdminNotes;
    @FXML private Button btnAdminister;

    private CareHome careHome;
    private Staff currentUser;
    private MainController main;

    private Resident currentResident; // resolved from bed
    private String currentBedId;

    private final ObservableList<Prescription> prescData = FXCollections.observableArrayList();
    private final ObservableList<Administration> adminData = FXCollections.observableArrayList();

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void setContext(CareHome ch, Staff user, MainController main) {
        this.careHome = ch;
        this.currentUser = user;
        this.main = main;

        lblUser.setText(user.getName() + " (" + user.getRole() + ")");
        // role gating
        btnAddPresc.setDisable(user.getRole() != Role.DOCTOR);
        btnAdminister.setDisable(user.getRole() != Role.NURSE);
    }

    @FXML
    public void initialize() {
        //  Prescriptions table
        colPId.setCellValueFactory(d -> new SimpleStringProperty(safeString(d.getValue(), "id")));
        //  timeCreated / created / createdAt / timestamp / time
        colPCreated.setCellValueFactory(d -> new SimpleStringProperty(
                formatDateTime(safeObject(d.getValue(),
                        "timeCreated", "created", "createdAt", "timestamp", "time"))));
        colPDoctor.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "doctorId", "doctor", "doctorID")));
        // supports meds list with fields: medicine/name/drugName, dose/dosage, frequency/freq/schedule
        colPMeds.setCellValueFactory(d -> new SimpleStringProperty(
                medsSummary(safeObject(d.getValue(), "meds", "medications", "items"))
        ));
        tblPrescriptions.setItems(prescData);

        //  Administrations table
        colATime.setCellValueFactory(d -> new SimpleStringProperty(
                formatDateTime(safeObject(d.getValue(), "time", "timestamp", "when", "administeredAt"))));
        colANurse.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "nurseId", "nurse", "staffId")));
        colAPresc.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "prescriptionId", "prescription", "pid")));
        colAMed.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "medicine", "drug", "name")));
        colANotes.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "notes", "remark", "comment")));

        tblAdmins.setItems(adminData);

        // Clicking a prescription populates nurse form and filters admins
        tblPrescriptions.getSelectionModel().selectedItemProperty().addListener((obs, o, p) -> {
            if (p != null) {
                txtAdminPrescId.setText(safeString(p, "id"));
                filterAdminsForPrescription(safeString(p, "id"));
            } else {
                txtAdminPrescId.clear();
                refreshAdmins();
            }
        });
    }

    // Actions

    @FXML
    private void handleLoadBed() {
        clearInfo();
        String bedId = txtBedId.getText() == null ? "" : txtBedId.getText().trim();
        if (bedId.isEmpty()) { error("Enter a bed ID (e.g., W1-R3-B2)."); return; }

        try {
            currentResident = careHome.getResidentInBed(currentUser.getId(), bedId);
            currentBedId = bedId;

            lblResident.setText(currentResident.name + " (" + currentResident.id + ")");
            lblGender.setText(currentResident.gender.toString());
            lblAge.setText(String.valueOf(currentResident.age));

            refreshPrescriptions();
            refreshAdmins();
            info("Loaded bed " + bedId);
        } catch (NotFoundException nf) {
            // bed missing or vacant
            currentResident = null;
            currentBedId = bedId;
            lblResident.setText("—");
            lblGender.setText("—");
            lblAge.setText("—");
            prescData.clear();
            adminData.clear();
            error("No resident in bed " + bedId + ".");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    @FXML
    private void handleAddPrescription() {
        if (currentUser.getRole() != Role.DOCTOR) { unauthorized("Only DOCTOR can add prescriptions."); return; }
        if (!ensureResidentLoaded()) return;

        String pid  = val(txtPrescId, "Prescription ID");
        String med  = val(txtMedName, "Medicine");
        String dose = val(txtDose, "Dose");
        String freq = val(txtFreq, "Frequency");
        if (pid == null || med == null || dose == null || freq == null) return;

        try {
            MedicationDose md = new MedicationDose(med, dose, freq);
            java.util.List<MedicationDose> meds = java.util.List.of(md);
            Prescription p = new Prescription(pid, currentUser.getId(), currentResident.id, LocalDateTime.now(), meds);

            careHome.addPrescription(currentUser.getId(), currentBedId, p, LocalDateTime.now());
            info("Prescription added for " + currentResident.name + ".");
            clearDoctorForm();
            refreshPrescriptions();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    @FXML
    private void handleAdminister() {
        if (currentUser.getRole() != Role.NURSE) { unauthorized("Only NURSE can administer."); return; }
        if (!ensureResidentLoaded()) return;

        String prescId = val(txtAdminPrescId, "Prescription ID");
        String med     = val(txtAdminMed,    "Medicine");
        String dose    = val(txtAdminDose,   "Dose");
        if (prescId == null || med == null || dose == null) return;

        String notes = txtAdminNotes.getText() == null ? "" : txtAdminNotes.getText().trim();

        try {
            Administration a = new Administration(
                    currentUser.getId(), prescId, med, LocalDateTime.now(),
                    "dose=" + dose + (notes.isEmpty() ? "" : "; " + notes));
            careHome.administerMedication(currentUser.getId(), currentBedId, a, LocalDateTime.now());
            info("Administered " + med + " to " + currentResident.name + ".");
            clearNurseForm();
            refreshAdmins();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    // Data refresh

    private void refreshPrescriptions() {
        if (currentResident == null) { prescData.clear(); return; }
        List<Prescription> list = careHome.getPrescriptionsForResident(currentResident.id);
        prescData.setAll(list);
    }

    private void refreshAdmins() {
        if (currentResident == null) { adminData.clear(); return; }
        adminData.setAll(careHome.getAdministrationsForResident(currentResident.id));
    }

    private void filterAdminsForPrescription(String prescId) {
        if (currentResident == null) { adminData.clear(); return; }
        adminData.setAll(
                careHome.getAdministrationsForResident(currentResident.id).stream()
                        .filter(a -> prescId.equals(safeString(a, "prescriptionId", "prescription", "pid")))
                        .collect(Collectors.toList())
        );
    }

    // Helpers

    private boolean ensureResidentLoaded() {
        if (currentResident == null) {
            error("Load a bed with a resident first.");
            return false;
        }
        return true;
    }

    private String val(TextField tf, String label) {
        String s = tf.getText() == null ? "" : tf.getText().trim();
        if (s.isEmpty()) {
            error(label + " is required.");
            return null;
        }
        return s;
    }

    private void clearDoctorForm() {
        txtPrescId.clear();
        txtMedName.clear();
        txtDose.clear();
        txtFreq.clear();
    }

    private void clearNurseForm() {
        txtAdminPrescId.clear();
        txtAdminMed.clear();
        txtAdminDose.clear();
        txtAdminNotes.clear();
    }

    private void info(String m) { lblInfo.setText(m); }
    private void clearInfo() { lblInfo.setText(""); }
    private void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.setHeaderText("Medication Error");
        a.showAndWait();
    }
    private void unauthorized(String m) {
        if (main != null) main.showError("Unauthorized", m);
        else error(m);
    }

    // tolerant reflection utilities

    private String medsSummary(Object medsObj) {
        if (!(medsObj instanceof List<?> list) || list.isEmpty()) return "";
        return list.stream().map(this::oneMedSummary).collect(Collectors.joining("; "));
    }

    private String oneMedSummary(Object md) {
        String med = safeString(md, "medicine", "name", "drugName");
        String dose = safeString(md, "dose", "dosage", "amount");
        String freq = safeString(md, "frequency", "freq", "schedule");
        String s = med;
        if (!dose.isEmpty() || !freq.isEmpty()) {
            s += " (" + (dose.isEmpty() ? "" : dose)
                    + (dose.isEmpty() || freq.isEmpty() ? "" : ", ")
                    + (freq.isEmpty() ? "" : freq) + ")";
        }
        return s;
    }

    private String formatDateTime(Object o) {
        if (o instanceof LocalDateTime ldt) return TS.format(ldt);
        return o == null ? "" : String.valueOf(o);
    }

    private String safeString(Object obj, String... names) {
        Object val = safeObject(obj, names);
        return val == null ? "" : String.valueOf(val);
    }

    /** Try fields then getters for any of the candidate names. */
    private Object safeObject(Object obj, String... names) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();

        // fields
        for (String n : names) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return v;
            } catch (NoSuchFieldException ignored) { }
            catch (Exception ignored) { }
        }
        // getters: getX()/isX()
        for (String n : names) {
            String base = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            for (String p : new String[]{"get", "is"}) {
                try {
                    Method m = c.getMethod(p + base);
                    Object v = m.invoke(obj);
                    if (v != null) return v;
                } catch (NoSuchMethodException ignored) { }
                catch (Exception ignored) { }
            }
        }
        return null;
    }
}
