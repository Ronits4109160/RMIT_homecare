package carehome.ui.controller;


// Controller for the Beds screen : shows all beds, selection details,
// and allows role-based actions (add/move/discharge).
import carehome.exception.NotFoundException;
import carehome.model.*;
import carehome.service.CareHome;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class BedsController {

    @FXML private FlowPane ward1Pane;
    @FXML private FlowPane ward2Pane;
    // Bed detail panel
    @FXML private Label lblBedId, lblStatus, lblResident, lblGender, lblAge;

    @FXML private Button btnAddResident, btnMoveResident, btnDischarge;
    @FXML private TextField txtTargetBed;
    @FXML private Label lblInfo;

    private CareHome careHome;
    private Staff currentUser;
    private MainController main;

    private String selectedBedId;

//     Injects application context and applies role-based UI permissions.

    public void setContext(CareHome ch, Staff user, MainController main) {
        this.careHome = ch;
        this.currentUser = user;
        this.main = main;
        // Role flags for readability
        boolean isManager = user.getRole() == Role.MANAGER;
        boolean isNurse   = user.getRole() == Role.NURSE;
        boolean isDoctor  = user.getRole() == Role.DOCTOR;

        btnAddResident.setDisable(!isManager);
        btnMoveResident.setDisable(!isNurse);
        btnDischarge.setDisable(!(isNurse || isDoctor));

        lblInfo.setText("Signed in as: " + user.getName() + " (" + user.getRole() + ")");
        reloadBeds();
    }

//    * Rebuilds the bed lists and repaints both ward panes.
    private void reloadBeds() {
        Map<String,Bed> beds = careHome.getBeds();
        List<String> allIds = new ArrayList<>(beds.keySet());
        allIds.sort(Comparator.comparingInt(this::extractBedNumberSafely));

        int split = (int)Math.ceil(allIds.size()/2.0);
        List<String> w1 = allIds.subList(0, split);
        List<String> w2 = allIds.subList(split, allIds.size());

        ward1Pane.getChildren().setAll(w1.stream().map(this::createBedNode).collect(Collectors.toList()));
        ward2Pane.getChildren().setAll(w2.stream().map(this::createBedNode).collect(Collectors.toList()));

        if (!allIds.isEmpty()) selectBed(allIds.get(0)); else clearDetails();
    }

//    * Creates a clickable Button for a bed. Button color indicates occupancy/gender.
    private Button createBedNode(String bedId) {
        Button b = new Button(bedId);
        b.setMinSize(64, 40);
        b.setPrefSize(80, 48);
        b.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Resident occ = safeResident(bedId);
        b.setStyle(colorStyleFor(occ));
        b.setTooltip(new Tooltip(occ == null ? "Vacant" :
                occ.name + " (" + occ.gender + ", " + occ.age + ")"));
        b.setOnAction(e -> selectBed(bedId));
        return b;
    }
   //      Selects a bed and updates the detail panel (status + occupant info)
    private void selectBed(String bedId) {
        this.selectedBedId = bedId;
        Resident occ = safeResident(bedId);
        lblBedId.setText(bedId);
        if (occ == null) {
            lblStatus.setText("Vacant");
            lblResident.setText("-");
            lblGender.setText("-");
            lblAge.setText("-");
        } else {
            lblStatus.setText("Occupied");
            lblResident.setText(occ.name + " (" + occ.id + ")");
            lblGender.setText(String.valueOf(occ.gender));
            lblAge.setText(String.valueOf(occ.age));
        }
    }
    /** Clears the detail panel when nothing is selected. */
    private void clearDetails() {
        selectedBedId = null;
        lblBedId.setText("-");
        lblStatus.setText("-");
        lblResident.setText("-");
        lblGender.setText("-");
        lblAge.setText("-");
    }

    //  actions
    @FXML
    private void handleAddResident() {
        if (currentUser.getRole() != Role.MANAGER) { showUnauthorized("Only MANAGER can add residents."); return; }
        if (selectedBedId == null) { err("Select a bed first."); return; }
        if (safeResident(selectedBedId) != null) { err("Bed is occupied."); return; }

        Optional<String> name = prompt("Resident Name");
        if (name.isEmpty() || name.get().trim().isEmpty()) { info("Cancelled."); return; }

        ChoiceDialog<String> chGender = new ChoiceDialog<>("MALE", List.of("MALE","FEMALE"));
        chGender.setHeaderText("Gender");
        Optional<String> g = chGender.showAndWait();
        if (g.isEmpty()) { info("Cancelled."); return; }

        Optional<String> ageS = prompt("Age (0–100)");
        if (ageS.isEmpty()) { info("Cancelled."); return; }

        try {
            int age = Integer.parseInt(ageS.get().trim());
            if (age < 0 || age > 100) {
                err("Age must be between 0 and 100.");
                return;
            }

            Gender gender = Gender.valueOf(g.get().trim().toUpperCase());

            // Pass null ID so CareHome auto-generates R{N+1}
            Resident r = new Resident(null, name.get().trim(), gender, age);

            careHome.addResidentToBed(currentUser.getId(), selectedBedId, r);

            info("Resident added to " + selectedBedId + " with ID " + r.id);
            reloadBeds();
            selectBed(selectedBedId);
        } catch (NumberFormatException nfe) {
            err("Please enter a valid numeric age.");
        } catch (Exception ex) {
            err(ex.getMessage());
        }
    }

    @FXML
    private void handleMoveResident() {
        if (currentUser.getRole() != Role.NURSE) { showUnauthorized("Only NURSE can move residents."); return; }
        if (selectedBedId == null) { err("Select a source bed."); return; }
        if (safeResident(selectedBedId) == null) { err("Source bed is vacant."); return; }

        String target = txtTargetBed.getText() == null ? "" : txtTargetBed.getText().trim();
        if (target.isEmpty()) { err("Enter a target bed ID."); return; }
        if (!careHome.getBeds().containsKey(target)) { err("Unknown target bed: " + target); return; }
        if (target.equals(selectedBedId)) { err("Target must be different from source."); return; }
        if (safeResident(target) != null) { err("Target bed is occupied."); return; }

        try {
            careHome.moveResident(currentUser.getId(), selectedBedId, target, LocalDateTime.now());
            info("Moved from " + selectedBedId + " to " + target);
            reloadBeds();
            selectBed(target);
            txtTargetBed.clear();
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    @FXML
    private void handleDischarge() {
        if (currentUser.getRole() != Role.NURSE && currentUser.getRole() != Role.DOCTOR) {
            showUnauthorized("Only NURSE or DOCTOR can discharge.");
            return;
        }
        if (selectedBedId == null) { err("Select a bed first."); return; }
        if (safeResident(selectedBedId) == null) { err("Bed is already vacant."); return; }

        try {
            careHome.dischargeResident(currentUser.getId(), selectedBedId, LocalDateTime.now());
            info("Discharged resident from " + selectedBedId);
            reloadBeds();
            selectBed(selectedBedId);
        } catch (Exception ex) { err(ex.getMessage()); }
    }

    //  helpers
    private Resident safeResident(String bedId) {
        try { return careHome.getResidentInBed(currentUser.getId(), bedId); }
        catch (NotFoundException nf) { return null; }
        catch (Exception e) { return null; }
    }

    private String colorStyleFor(Resident occ) {
        if (occ == null) return "-fx-background-color: white; -fx-border-color: #95a5a6;";
        if (occ.gender == Gender.MALE)   return "-fx-background-color: #3498db; -fx-text-fill: white;";
        if (occ.gender == Gender.FEMALE) return "-fx-background-color: #e74c3c; -fx-text-fill: white;";
        return "-fx-background-color: #bdc3c7;";
    }

    private int extractBedNumberSafely(String id) {
        try {
            String d = id.replaceAll("\\D+", "");
            return d.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(d);
        } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private Optional<String> prompt(String header) {
        TextInputDialog d = new TextInputDialog();
        d.setHeaderText(header);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private void info(String m) { lblInfo.setText(m); }
    private void err(String m)  {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.setHeaderText("Beds Action Error");
        a.showAndWait();
    }
    private void showUnauthorized(String m) {
        if (main != null) main.showError("Unauthorized", m);
        else err(m);
    }
}
