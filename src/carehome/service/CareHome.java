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

    private final Map<String, Bed> beds = new HashMap<>();
    private final Map<String, List<Prescription>> prescriptionsByResident = new HashMap<>();
    private final List<Administration> administrations = new ArrayList<>();

    private final List<Shift> shifts = new ArrayList<>();
    private final List<ActionLog> logs = new ArrayList<>();

    //  Manager-only helpers
    public boolean isManager(String staffId) {
        Staff s = staffById.get(staffId);
        return s != null && s.getRole() == Role.MANAGER;
    }

    private void requireManager(String actorId) {
        if (!isManager(actorId))
            throw new UnauthorizedException("Only manager allowed for this action");
    }

    //  Staff Operations
    public void addOrUpdateStaff(String actorId, Staff staff, String username, String password) {
        boolean bootstrap = staffById.isEmpty() && staff.getRole() == Role.MANAGER;
        if (!bootstrap) requireManager(actorId);

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


    //Bed & Resident Operations


    /** Manager adds a new resident to a vacant bed. */
    public void addResidentToBed(String managerId, String bedId, Resident r) {
        requireManager(managerId);
        Bed b = beds.computeIfAbsent(bedId, Bed::new);
        if (!b.isVacant())
            throw new BedOccupiedException("Bed " + bedId + " is already occupied by " + b.occupant.name);

        b.occupant = r;
        log(managerId, "ADD RESIDENT " + r + " to bed " + bedId);
    }

    /** Returns the resident occupying a bed (if any). */
    public Resident getResidentInBed(String actorId, String bedId) {
        Bed b = beds.get(bedId);
        if (b == null)
            throw new NotFoundException("Bed " + bedId + " does not exist");
        if (b.isVacant())
            throw new NotFoundException("Bed " + bedId + " is vacant");

        requireAuthorizedStaff(actorId);
        log(actorId, "CHECK RESIDENT in bed " + bedId);
        return b.occupant;
    }

    /** Nurse moves a resident from one bed to another (with checks). */
    public void moveResident(String nurseId, String fromBedId, String toBedId, LocalDateTime when) {
        requireRole(nurseId, Role.NURSE);
        requireRostered(nurseId, when);

        Bed from = beds.get(fromBedId);
        Bed to = beds.computeIfAbsent(toBedId, Bed::new);
        if (from == null || from.isVacant())
            throw new NotFoundException("No resident in bed " + fromBedId);
        if (!to.isVacant())
            throw new BedOccupiedException("Bed " + toBedId + " already occupied by " + to.occupant.name);

        Resident moving = from.occupant;
        from.occupant = null;
        to.occupant = moving;

        log(nurseId, "MOVE RESIDENT " + moving.name + " from " + fromBedId + " to " + toBedId);
    }

    // =============================
    // === Prescription Operations
    // =============================
    public void addPrescription(String doctorId, String bedId, Prescription p, LocalDateTime when) {
        requireRole(doctorId, Role.DOCTOR);
        requireRostered(doctorId, when);

        Bed b = beds.get(bedId);
        if (b == null || b.isVacant())
            throw new NotFoundException("Cannot prescribe: bed " + bedId + " is vacant or missing");

        prescriptionsByResident.computeIfAbsent(b.occupant.id, k -> new ArrayList<>()).add(p);
        log(doctorId, "ADD PRESCRIPTION " + p.id + " for " + b.occupant.name + " in " + bedId);
    }

    public void administerMedication(String nurseId, String bedId, Administration admin, LocalDateTime when) {
        requireRole(nurseId, Role.NURSE);
        requireRostered(nurseId, when);

        Bed b = beds.get(bedId);
        if (b == null || b.isVacant())
            throw new NotFoundException("Cannot administer: bed " + bedId + " vacant or missing");

        administrations.add(admin);
        log(nurseId, "ADMINISTER " + admin.medicine + " to " + b.occupant.name + " (" + bedId + ")");
    }

    // =============================
    // === Shift Operations
    // =============================
    public void allocateShift(String actorId, Shift shift) {
        requireManager(actorId);

        for (Shift s : shifts) {
            if (s.overlaps(shift))
                throw new ShiftRuleException("Overlapping shift for " + shift.getStaffId());
        }

        shifts.add(shift);
        log(actorId, "ALLOCATE SHIFT " + shift.getStaffId() + " " + shift.getStart() + " -> " + shift.getEnd());
    }

    public List<Shift> getShifts() {
        return Collections.unmodifiableList(shifts);
    }

    // =============================
    // === Compliance
    // =============================
    public void checkCompliance() {
        // Group shifts by staffId then by LocalDate
        var byStaff = new HashMap<String, Map<java.time.LocalDate, List<Shift>>>();
        for (var s : shifts) {
            byStaff.computeIfAbsent(s.getStaffId(), k -> new HashMap<>())
                    .computeIfAbsent(s.getStart().toLocalDate(), k -> new ArrayList<>())
                    .add(s);
        }

        // Collect all dates that have any shifts
        var allDates = new HashSet<java.time.LocalDate>();
        for (var m : byStaff.values()) allDates.addAll(m.keySet());

        // Nurses: max 8h per day
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

        // Nurse coverage: at least one 08–16 and one 14–22 per day
        for (var date : allDates) {
            boolean hasMorning = false, hasEvening = false;
            for (String nid : nurseIds) {
                var list = byStaff.getOrDefault(nid, Map.of()).getOrDefault(date, List.of());
                for (Shift s : list) {
                    if (s.getStart().toLocalTime().equals(java.time.LocalTime.of(8,0)) &&
                            s.getEnd().toLocalTime().equals(java.time.LocalTime.of(16,0))) hasMorning = true;
                    if (s.getStart().toLocalTime().equals(java.time.LocalTime.of(14,0)) &&
                            s.getEnd().toLocalTime().equals(java.time.LocalTime.of(22,0))) hasEvening = true;
                }
            }
            if (!hasMorning || !hasEvening)
                throw new ComplianceException("Nurse coverage missing on " + date + " (08-16 / 14-22)");
        }

        // Doctor coverage: >=1h per day
        for (var date : allDates) {
            long totalDoctorHours = 0;
            for (String did : doctorIds) {
                var list = byStaff.getOrDefault(did, Map.of()).getOrDefault(date, List.of());
                totalDoctorHours += list.stream().mapToLong(Shift::hours).sum();
            }
            if (totalDoctorHours < 1)
                throw new ComplianceException("Doctor coverage <1h on " + date);
        }
    }

    // =============================
    // === Auth & Roster Checks
    // =============================
    private void requireAuthorizedStaff(String actorId) {
        if (!staffById.containsKey(actorId))
            throw new UnauthorizedException("Unrecognized staff: " + actorId);
    }

    private void requireRole(String actorId, Role expected) {
        var s = staffById.get(actorId);
        if (s == null || s.getRole() != expected)
            throw new UnauthorizedException("Requires role " + expected);
    }

    private void requireRostered(String actorId, LocalDateTime at) {
        boolean ok = shifts.stream().anyMatch(sh ->
                sh.getStaffId().equals(actorId) &&
                        !at.isBefore(sh.getStart()) &&
                        at.isBefore(sh.getEnd()));
        if (!ok) throw new NotRosteredException("Actor " + actorId + " not rostered at " + at);
    }

    // =============================
    // === Logging
    // =============================
    private void log(String staffId, String action) {
        logs.add(new ActionLog(staffId, action));
        System.out.println("[LOG] " + staffId + ": " + action);
    }

    public List<ActionLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    //  Serialization (Save/Load)
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

    //  Getters
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

    public Map<String, Bed> getBeds() {
        return Collections.unmodifiableMap(beds);
    }
}
