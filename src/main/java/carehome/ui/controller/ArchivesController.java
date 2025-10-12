package carehome.ui.controller;

import carehome.model.*;
import carehome.service.CareHome;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Collectors;
import javafx.collections.ObservableList;


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

    public void setContext(CareHome ch, Staff current, MainController main) {
        this.careHome = ch;
        this.main = main;
        refresh();
    }

    @FXML
    public void initialize() {
        // Stays table
        colWhen.setCellValueFactory(d -> new SimpleStringProperty(TS.format(d.getValue().dischargedAt)));
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().residentName));
        colGender.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().gender.name()));
        colAge.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().age)));
        colBed.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().lastBedId));
        tblStays.setItems(stays);

        // Prescriptions table
        colPid.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().id));
        colDoc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().doctorId));
        colCreated.setCellValueFactory(d -> new SimpleStringProperty(TS.format((TemporalAccessor) d.getValue().timeCreated)));
        colMeds.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().meds == null ? "" :
                        d.getValue().meds.stream()
                                .map(m -> m.medicine + " (" + m.dosage + ", " + m.frequency + ")")
                                .collect(Collectors.joining("; "))
        ));

        // Admins table
        colATime.setCellValueFactory(d -> new SimpleStringProperty(TS.format((TemporalAccessor) d.getValue().time)));
        colANurse.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().nurseId));
        colAPresc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().prescriptionId));
        colAMed.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicine));
        colANotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().notes == null ? "" : d.getValue().notes));

        // Selection -> details
        tblStays.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> showStay(s));

        // Search filter
        txtSearch.textProperty().addListener((obs, o, q) -> {
            String qq = q == null ? "" : q.trim().toLowerCase();
            stays.setPredicate(st -> qq.isEmpty()
                    || st.residentName.toLowerCase().contains(qq)
                    || st.residentId.toLowerCase().contains(qq)
                    || st.lastBedId.toLowerCase().contains(qq));
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
        lblName.setText(s.residentName);   lblId.setText(s.residentId);
        lblGender.setText(s.gender.name()); lblAge.setText(String.valueOf(s.age));
        lblWhen.setText(TS.format(s.dischargedAt)); lblBed.setText(s.lastBedId);

        tblPresc.getItems().setAll(s.prescriptions);
        tblAdmins.getItems().setAll(s.administrations);
    }

    private void exportCsv() {
        try {
            var path = java.nio.file.Paths.get("archives_export.csv");
            try (var w = java.nio.file.Files.newBufferedWriter(path)) {
                w.write("discharged,residentId,name,gender,age,lastBed\n");
                for (var s : stays) {
                    w.write(String.join(",",
                            TS.format(s.dischargedAt),
                            s.residentId,
                            escape(s.residentName),
                            s.gender.name(),
                            Integer.toString(s.age),
                            s.lastBedId));
                    w.write("\n");
                }
            }
            info("Exported to " + path.toAbsolutePath());
        } catch (Exception ex) {
            error("Export failed: " + ex.getMessage());
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
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
