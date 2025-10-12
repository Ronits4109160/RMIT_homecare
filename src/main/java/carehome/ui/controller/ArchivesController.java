package carehome.ui.controller;

import carehome.model.*;
import carehome.service.CareHome;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ArchivesController {

    @FXML private TextField txtSearch;
    @FXML private Label lblInfo;

    @FXML private TableView<ArchivedStay> tblStays;
    @FXML private TableColumn<ArchivedStay, String> colWhen, colName, colGender, colAge, colBed;

    @FXML private Label lblName, lblId, lblGender, lblAge, lblWhen, lblBed;

    @FXML private TableView<Prescription> tblPresc;
    @FXML private TableColumn<Prescription, String> colPid, colDoc, colCreated, colMeds;

    @FXML private TableView<Administration> tblAdmins;
    @FXML private TableColumn<Administration, String> colATime, colANurse, colAPresc, colAMed, colANotes;

    @FXML private Button btnExport;

    private CareHome careHome;
    private MainController main;

    private final ObservableList<ArchivedStay> backing = FXCollections.observableArrayList();
    private final FilteredList<ArchivedStay> stays = new FilteredList<>(backing);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    //  helpers
    private static String fmt(LocalDateTime t) {
        return (t == null) ? "" : TS.format(t);
    }
    private static String safe(String s) { return s == null ? "" : s; }

    public void setContext(CareHome ch, Staff current, MainController main) {
        this.careHome = ch;
        this.main = main;
        refresh();
    }

    @FXML
    public void initialize() {
        // Stays table
        colWhen.setCellValueFactory(d -> new SimpleStringProperty(fmt(d.getValue().dischargedAt)));
        colName.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().residentName)));
        colGender.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().gender == null ? "" : d.getValue().gender.name()));
        colAge.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().age)));
        colBed.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().lastBedId)));
        tblStays.setItems(stays);

        // Prescriptions table
        colPid.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().id)));
        colDoc.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().doctorId)));
        colCreated.setCellValueFactory(d -> {
            ActionLog al = d.getValue().timeCreated; // unwrap ActionLog -> LocalDateTime
            LocalDateTime t = (al == null) ? null : al.getTime();
            return new SimpleStringProperty(fmt(t));
        });
        colMeds.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().meds == null ? "" :
                        d.getValue().meds.stream()
                                .map(m -> safe(m.medicine)
                                        + (m.dosage == null && m.frequency == null ? "" :
                                        " (" + safe(m.dosage)
                                                + ((m.dosage != null && m.frequency != null) ? ", " : "")
                                                + safe(m.frequency) + ")"))
                                .collect(Collectors.joining("; "))
        ));

        // Admins table
        colATime.setCellValueFactory(d -> {
            ActionLog al = d.getValue().time;
            LocalDateTime t = (al == null) ? null : al.getTime();
            return new SimpleStringProperty(fmt(t));
        });
        colANurse.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().nurseId)));
        colAPresc.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().prescriptionId)));
        colAMed.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().medicine)));
        colANotes.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().notes)));

        // Selection -> details
        tblStays.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> showStay(s));

        // Search filter
        txtSearch.textProperty().addListener((obs, o, q) -> {
            String qq = q == null ? "" : q.trim().toLowerCase();
            stays.setPredicate(st -> qq.isEmpty()
                    || safe(st.residentName).toLowerCase().contains(qq)
                    || safe(st.residentId).toLowerCase().contains(qq)
                    || safe(st.lastBedId).toLowerCase().contains(qq));
            lblInfo.setText(stays.size() + " record(s)");
        });

        // Export CSV of current filtered rows
        btnExport.disableProperty().bind(Bindings.isEmpty(stays));
        btnExport.setOnAction(e -> exportCsv());
    }

    private void refresh() {
        if (careHome == null) return;
        List<ArchivedStay> list = careHome.getArchives();
        backing.setAll(list);
        stays.setPredicate(st -> true);
        lblInfo.setText(list.size() + " record(s)");
        if (!list.isEmpty()) {
            tblStays.getSelectionModel().select(0);
        }
    }

    private void showStay(ArchivedStay s) {
        if (s == null) {
            lblName.setText(""); lblId.setText(""); lblGender.setText("");
            lblAge.setText(""); lblWhen.setText(""); lblBed.setText("");
            tblPresc.getItems().clear(); tblAdmins.getItems().clear();
            return;
        }
        lblName.setText(safe(s.residentName));
        lblId.setText(safe(s.residentId));
        lblGender.setText(s.gender == null ? "" : s.gender.name());
        lblAge.setText(String.valueOf(s.age));
        lblWhen.setText(fmt(s.dischargedAt));
        lblBed.setText(safe(s.lastBedId));

        tblPresc.getItems().setAll(s.prescriptions == null ? List.of() : s.prescriptions);
        tblAdmins.getItems().setAll(s.administrations == null ? List.of() : s.administrations);
    }

    private void exportCsv() {
        try {
            var path = java.nio.file.Paths.get("archives_export.csv");
            try (var w = java.nio.file.Files.newBufferedWriter(path)) {
                w.write("discharged,residentId,name,gender,age,lastBed\n");
                for (var s : stays) {
                    w.write(String.join(",",
                            fmt(s.dischargedAt),
                            safe(s.residentId),
                            escape(safe(s.residentName)),
                            s.gender == null ? "" : s.gender.name(),
                            Integer.toString(s.age),
                            safe(s.lastBedId)));
                    w.write("\n");
                }
            }
            info("Exported to " + path.toAbsolutePath());
        } catch (Exception ex) {
            error("Export failed: " + ex.getMessage());
        }
    }

    private static String escape(String s) {
        if (s.contains(",") || s.contains("\"")) return '"' + s.replace("\"", "\"\"") + '"';
        return s;
    }

    private void info(String m) { lblInfo.setText(m); }
    private void error(String m) {
        new Alert(Alert.AlertType.ERROR, m, ButtonType.OK) {{
            setHeaderText("Archive Error");
        }}.showAndWait();
    }
}
