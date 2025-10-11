package carehome.ui.controller;

import carehome.model.ActionLog;
import carehome.model.Role;
import carehome.model.Staff;
import carehome.service.CareHome;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class LogsController {

    @FXML private Label lblUser, lblInfo;
    @FXML private TextField txtSearch;
    @FXML private DatePicker dpFrom, dpTo;
    @FXML private Button btnCompliance;

    @FXML private TableView<ActionLog> tblLogs;
    @FXML private TableColumn<ActionLog,String> colTime, colStaff, colRole, colAction;

    private CareHome careHome;
    private Staff currentUser;

    private final ObservableList<ActionLog> data = FXCollections.observableArrayList();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void setContext(CareHome ch, Staff user) {
        this.careHome = ch;
        this.currentUser = user;

        lblUser.setText(user.getName() + " (" + user.getRole() + ")");
        btnCompliance.setDisable(user.getRole() != Role.MANAGER); // manager-only
        refresh();
    }

    @FXML
    public void initialize() {
        // Column bindings
        colTime.setCellValueFactory(d -> new SimpleStringProperty(formatDateTime(
                safeObject(d.getValue(), "time", "timestamp", "when", "at"))));
        colStaff.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "staffId", "actorId", "who")));
        colRole.setCellValueFactory(d -> new SimpleStringProperty(resolveRole(
                safeString(d.getValue(), "staffId", "actorId", "who"))));
        colAction.setCellValueFactory(d -> new SimpleStringProperty(
                safeString(d.getValue(), "action", "message", "what")));

        tblLogs.setItems(data);
    }

    // Actions

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleCompliance() {
        try {
            careHome.checkCompliance();
            info("Compliance OK.");
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    //Data & filtering

    private void refresh() {
        data.setAll(careHome.getLogs()); // base list

        String q = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase(Locale.ROOT);
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        // Apply filters in place
        data.removeIf(log -> {
            // search text check
            if (!q.isEmpty()) {
                String staff = safeString(log, "staffId", "actorId", "who").toLowerCase(Locale.ROOT);
                String action = safeString(log, "action", "message", "what").toLowerCase(Locale.ROOT);
                if (!staff.contains(q) && !action.contains(q)) return true;
            }
            // date range check
            LocalDateTime ldt = asDateTime(safeObject(log, "time", "timestamp", "when", "at"));
            if (ldt != null) {
                if (from != null && ldt.toLocalDate().isBefore(from)) return true;
                if (to != null && ldt.toLocalDate().isAfter(to)) return true;
            }
            return false;
        });

        info(data.size() + " log(s).");
    }

    //  Helpers

    private String resolveRole(String staffId) {
        if (staffId == null || staffId.isEmpty()) return "";
        Staff s = careHome.getStaffById().get(staffId);
        return (s == null || s.getRole() == null) ? "" : s.getRole().toString();
    }

    private LocalDateTime asDateTime(Object o) {
        if (o instanceof LocalDateTime ldt) return ldt;
        return null; // if ActionLog lacks a datetime, we just skip date filtering for that row
    }

    private String formatDateTime(Object o) {
        LocalDateTime ldt = asDateTime(o);
        return ldt == null ? "" : TS.format(ldt);
    }

    private void info(String m) { lblInfo.setText(m); }
    private void error(String m) {
        Alert a = new Alert(Alert.AlertType.ERROR, m, ButtonType.OK);
        a.setHeaderText("Compliance / Logs"); a.showAndWait();
    }

    private String safeString(Object obj, String... names) {
        Object val = safeObject(obj, names);
        return val == null ? "" : String.valueOf(val);
    }

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
        // getters
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
