package carehome.service;

import carehome.exception.*;
import carehome.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;


public class CareHome implements Serializable {

    private static final long serialVersionUID = 1L;

    //  Data Registries
    private final Map<String, Staff> staffById = new HashMap<>();
    private final List<String> doctorIds = new ArrayList<>();
    private final List<String> nurseIds = new ArrayList<>();
    private String managerId; // one manager in system

    private final List<Shift> shifts = new ArrayList<>();
    private final List<ActionLog> logs = new ArrayList<>();

    // =============================
    // === Manager-only helpers
    // =============================
    public boolean isManager(String staffId) {
        Staff s = staffById.get(staffId);
        return s != null && s.getRole() == Role.MANAGER;
    }

    private void requireManager(String actorId) {
        if (!isManager(actorId))
            throw new UnauthorizedException("Only manager allowed for this action");
    }

    //  Staff Operations
    /**
     * Manager-only add/update, EXCEPT bootstrap:
     * If there are no staff yet and the provided staff is a MANAGER,
     * allow creating that first manager without requiring an existing manager.
     */
    public void addOrUpdateStaff(String actorId, Staff staff, String username, String password) {
        boolean bootstrap = staffById.isEmpty() && staff.getRole() == Role.MANAGER;
        if (!bootstrap) {
            requireManager(actorId);
        }

        staff.setCredentials(username, password);
        staffById.put(staff.getId(), staff);

        switch (staff.getRole()) {
            case DOCTOR -> {
                if (!doctorIds.contains(staff.getId())) doctorIds.add(staff.getId());
            }
            case NURSE -> {
                if (!nurseIds.contains(staff.getId())) nurseIds.add(staff.getId());
            }
            case MANAGER -> managerId = staff.getId();
        }

        log(bootstrap ? "SYSTEM" : actorId, "ADD/UPDATE STAFF " + staff);
    }

    //  Shift Operations
    public void allocateShift(String actorId, Shift shift) {
        requireManager(actorId);

        for (Shift s : shifts) {
            if (s.overlaps(shift))
                throw new ShiftRuleException("Overlapping shift for " + shift.getStaffId());
        }

        shifts.add(shift);
        log(actorId, "ALLOCATE SHIFT " + shift.getStaffId() + " "
                + shift.getStart() + " -> " + shift.getEnd());
    }

    public List<Shift> getShifts() {
        return Collections.unmodifiableList(shifts);
    }

    //  Logging
    private void log(String staffId, String action) {
        logs.add(new ActionLog(staffId, action));
        System.out.println("[LOG] " + staffId + ": " + action);
    }

    public List<ActionLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    // === Serialization (Save/Load)
    public void saveToFile(Path file) {
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(file))) {
            out.writeObject(this);
            log(managerId != null ? managerId : "SYSTEM", "Saved data to file " + file);
        } catch (IOException e) {
            throw new ComplianceException("Save failed: " + e.getMessage());
        }
    }

    public static CareHome loadFromFile(Path file) {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(file))) {
            CareHome ch = (CareHome) in.readObject();
            System.out.println("Loaded CareHome data from " + file);
            return ch;
        } catch (Exception e) {
            throw new ComplianceException("Load failed: " + e.getMessage());
        }
    }

    //  Compliance
    public void checkCompliance() {
        // Index shifts by date
        Map<String, Map<java.time.LocalDate, java.util.List<Shift>>> byStaff =
                new HashMap<>();
        for (Shift s : shifts) {
            byStaff.computeIfAbsent(s.getStaffId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getStart().toLocalDate(), k -> new ArrayList<>())
                    .add(s);
        }

        // Collect all dates that have any shifts in the system
        var allDates = new java.util.HashSet<java.time.LocalDate>();
        for (var m : byStaff.values()) allDates.addAll(m.keySet());

        // Per-nurse daily hours rule: <= 8h per day
        for (String nid : nurseIds) {
            var daily = byStaff.getOrDefault(nid, Map.of());
            for (var e : daily.entrySet()) {
                var date = e.getKey();
                var list = e.getValue();
                long totalHours = list.stream().mapToLong(Shift::hours).sum();
                if (totalHours > 8)
                    throw new ComplianceException("Nurse " + nid + " exceeds 8h on " + date);
            }
        }

        for (var date : allDates) {
            boolean has08to16 = false;
            boolean has14to22 = false;

            // Search across all nurses on this date
            for (String nid : nurseIds) {
                var list = byStaff.getOrDefault(nid, Map.of()).getOrDefault(date, List.of());
                if (!has08to16) {
                    has08to16 = list.stream().anyMatch(s ->
                            s.getStart().toLocalTime().equals(java.time.LocalTime.of(8, 0)) &&
                                    s.getEnd().toLocalTime().equals(java.time.LocalTime.of(16, 0)));
                }
                if (!has14to22) {
                    has14to22 = list.stream().anyMatch(s ->
                            s.getStart().toLocalTime().equals(java.time.LocalTime.of(14, 0)) &&
                                    s.getEnd().toLocalTime().equals(java.time.LocalTime.of(22, 0)));
                }
                if (has08to16 && has14to22) break;
            }

            if (!has08to16 || !has14to22) {
                throw new ComplianceException("Nurse shift coverage missing on " + date +
                        " (needs 08-16 and 14-22).");
            }
        }

        // Doctor coverage per day: at least 1 hour
        for (var date : allDates) {
            long totalDoctorHours = 0;
            for (String did : doctorIds) {
                var list = byStaff.getOrDefault(did, Map.of()).getOrDefault(date, List.of());
                totalDoctorHours += list.stream().mapToLong(Shift::hours).sum();
                if (totalDoctorHours >= 1) break;
            }
            if (totalDoctorHours < 1)
                throw new ComplianceException("Doctor coverage < 1h on " + date);
        }
    }


    //  Role & Roster checks for clinical actions
    private void requireRole(String actorId, carehome.model.Role expected) {
        var s = staffById.get(actorId);
        if (s == null || s.getRole() != expected)
            throw new carehome.exception.UnauthorizedException("Requires role " + expected);
    }

    private void requireRostered(String actorId, java.time.LocalDateTime at) {
        boolean ok = shifts.stream().anyMatch(sh ->
                sh.getStaffId().equals(actorId) &&
                        !at.isBefore(sh.getStart()) &&
                        at.isBefore(sh.getEnd()));
        if (!ok) throw new carehome.exception.NotRosteredException("Actor " + actorId + " not rostered at " + at);
    }

    public void addPrescription(String doctorId, String bedId, carehome.model.Prescription p, java.time.LocalDateTime when) {
        requireRole(doctorId, carehome.model.Role.DOCTOR);
        requireRostered(doctorId, when);
        // TODO: attach prescription to resident currently in bedId
        log(doctorId, "ADD PRESCRIPTION to " + bedId + " @ " + when);
    }

    public void administerMedication(String nurseId, String bedId, carehome.model.Administration admin, java.time.LocalDateTime when) {
        requireRole(nurseId, carehome.model.Role.NURSE);
        requireRostered(nurseId, when);
        // TODO: record administration against resident/prescription
        log(nurseId, "ADMINISTER " + admin + " to " + bedId + " @ " + when);
    }

    // =============================
    // === Getters
    // =============================
    public Map<String, Staff> getStaffById() {
        return Collections.unmodifiableMap(staffById);
    }

    public List<String> getDoctorIds() {
        return Collections.unmodifiableList(doctorIds);
    }

    public List<String> getNurseIds() {
        return Collections.unmodifiableList(nurseIds);
    }

    public String getManagerId() {
        return managerId;
    }
}
