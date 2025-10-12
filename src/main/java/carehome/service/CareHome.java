package carehome.service;

import carehome.exception.*;
import carehome.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    //  archived stays
    private final List<ArchivedStay> archives = new ArrayList<>();
    private CareHome careHome;

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

    // Manager adds a new resident to a vacant bed.
    public void addResidentToBed(String managerId, String bedId, Resident r) {
        requireManager(managerId);
        if (r == null) throw new ValidationException("Resident details required");

        //  Age validation
        if (r.age < 0 || r.age > 100) {
            throw new ValidationException("Resident age must be between 0 and 100.");
        }

        if (r.id == null || r.id.trim().isEmpty()) {
            r.id = nextResidentId();
        } else {
            if (isResidentIdActive(r.id)) {
                throw new ValidationException("Resident ID already in use: " + r.id);
            }
        }

        // gender rule - if room already has any occupants, new resident must match
        enforceRoomGender(bedId, r.gender);

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

//    Nurse can moves a resident from one bed to another.
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

        // destination room must be either empty or same gender as moving
        enforceRoomGender(toBedId, moving.gender);
        from.occupant = null;
        to.occupant = moving;

        log(nurseId, "MOVE RESIDENT " + moving.name + " from " + fromBedId + " to " + toBedId);
    }

    //  Prescription Operations
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

    // Shift Operations
    public void allocateShift(String actorId, Shift shift) {
        requireManager(actorId);

        Staff assignee = staffById.get(shift.getStaffId());
        if (assignee == null) throw new NotFoundException("Unknown staff: " + shift.getStaffId());

        // Must be same-day
        if (!shift.getStart().toLocalDate().equals(shift.getEnd().toLocalDate()))
            throw new ShiftRuleException("Shift must start and end on the same day.");

        long hours = shift.hours();

        if (assignee.getRole() == Role.NURSE) {
            // fixed 8h slots only
            var startT = shift.getStart().toLocalTime();
            var endT   = shift.getEnd().toLocalTime();
            boolean morning = startT.equals(java.time.LocalTime.of(8, 0))  && endT.equals(java.time.LocalTime.of(16, 0));
            boolean evening = startT.equals(java.time.LocalTime.of(14, 0)) && endT.equals(java.time.LocalTime.of(22, 0));
            if (!(morning || evening)) throw new ShiftRuleException("Nurse shifts must be 08:00–16:00 or 14:00–22:00.");
            if (hours != 8)            throw new ShiftRuleException("Nurse shift must be exactly 8 hours.");

            var day = shift.getStart().toLocalDate();

            // at most one shift per nurse per day
            boolean nurseAlreadyHasThatDay = shifts.stream().anyMatch(s ->
                    s.getStaffId().equals(assignee.getId()) &&
                            s.getStart().toLocalDate().equals(day)
            );
            if (nurseAlreadyHasThatDay)
                throw new ShiftRuleException("Nurse " + assignee.getId() + " already has a shift on " + day + ".");

            // only one nurse can occupy a given slot that day (same start/end)
            boolean slotTaken = shifts.stream().anyMatch(s -> {
                Staff st = staffById.get(s.getStaffId());
                return st != null && st.getRole() == Role.NURSE
                        && s.getStart().toLocalDate().equals(day)
                        && s.getStart().toLocalTime().equals(startT)
                        && s.getEnd().toLocalTime().equals(endT);
            });
            if (slotTaken)
                throw new ShiftRuleException("Nurse slot already assigned: " + day + " " + startT + "–" + endT + ".");
        } else if (assignee.getRole() == Role.DOCTOR) {
            if (hours != 1) throw new ShiftRuleException("Doctor shift must be exactly 1 hour.");
        }

        // self-overlap guard (uses your Shift.overlaps, i.e., same staff only)
        boolean overlapsSelf = shifts.stream().anyMatch(s -> s.overlaps(shift));
        if (overlapsSelf)
            throw new ShiftRuleException("Overlapping shift for " + shift.getStaffId());

        shifts.add(shift);
        log(actorId, "ALLOCATE SHIFT " + shift.getStaffId() + " " + shift.getStart() + " -> " + shift.getEnd());
    }


    public List<Shift> getShifts() {
        return Collections.unmodifiableList(shifts);
    }

    public Staff authenticate(String id, String password) {
        Staff s = staffById.get(id);
        if (s == null) throw new UnauthorizedException("Unknown staff ID");
        if (!s.checkPassword(password)) throw new UnauthorizedException("Invalid password");
        return s;
    }

    // List active administrations for a resident
    public java.util.List<Administration> getAdministrationsForResident(String residentId) {
        java.util.Set<String> presIds = getPrescriptionsForResident(residentId)
                .stream().map(p -> p.id).collect(java.util.stream.Collectors.toSet());
        java.util.List<Administration> out = new java.util.ArrayList<>();
        for (Administration a : administrations) {
            if (presIds.contains(a.prescriptionId)) out.add(a);
        }
        return java.util.Collections.unmodifiableList(out);
    }


    //  Compliance

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

        // Nurses  max 8h per day
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

    //  Auth & Roster Checks

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

    public void seedDefaultLayout() {
        createWard("W1", new int[]{1, 2, 4, 3, 4, 3});
        createWard("W2", new int[]{1, 2, 4, 3, 4, 3});
    }

    private void createWard(String wardId, int[] bedsPerRoom) {
        for (int room = 1; room <= bedsPerRoom.length; room++) {
            int count = bedsPerRoom[room - 1];
            if (count < 1 || count > 4)
                throw new IllegalArgumentException("Room bed count must be 1..4");
            for (int bed = 1; bed <= count; bed++) {
                String bedId = wardId + "-R" + room + "-B" + bed;
                beds.putIfAbsent(bedId, new Bed(bedId));
            }
        }
    }

    public boolean hasAnyBeds() {
        return !beds.isEmpty();
    }

    /**
     * Discharge a resident
     * Frees the bed and archives the full stay .
     */
    public ArchivedStay dischargeResident(String actorId, String bedId, LocalDateTime when) {
        Staff actor = staffById.get(actorId);
        if (actor == null) throw new UnauthorizedException("Unrecognized staff: " + actorId);
        if (actor.getRole() != Role.DOCTOR && actor.getRole() != Role.NURSE)
            throw new UnauthorizedException("Only doctor or nurse can discharge");
        requireRostered(actorId, when);

        Bed bed = beds.get(bedId);
        if (bed == null) throw new NotFoundException("Bed " + bedId + " does not exist");
        if (bed.isVacant()) throw new NotFoundException("Bed " + bedId + " is vacant");

        Resident r = bed.occupant;

        // gather history
        List<Prescription> pres = new ArrayList<>(getPrescriptionsForResident(r.id));
        Set<String> presIds = pres.stream().map(p -> p.id).collect(Collectors.toSet());
        List<Administration> admin = administrations.stream()
                .filter(a -> presIds.contains(a.prescriptionId))
                .collect(Collectors.toList());

        // archive snapshot
        ArchivedStay stay = new ArchivedStay(
                r.id, r.name, r.gender, r.age,
                bedId, when, pres, admin
        );
        archives.add(stay);

        // clean active state
        prescriptionsByResident.remove(r.id);
        administrations.removeIf(a -> presIds.contains(a.prescriptionId));
        bed.occupant = null;

        log(actorId, "DISCHARGE " + r.name + " from " + bedId + " (archived)");
        return stay;
    }

    // Helpers for archive/GUI access
    public List<Prescription> getPrescriptionsForResident(String residentId) {
        return Collections.unmodifiableList(prescriptionsByResident.getOrDefault(residentId, List.of()));
    }

    public List<ArchivedStay> getArchives() {
        return Collections.unmodifiableList(archives);
    }


    //Room/Gender helpers

    private String roomKeyOf(String bedId) {
        int i = bedId.lastIndexOf("-B");
        if (i > 0) return bedId.substring(0, i);
        // fallback: keep first two segments (ward + room)
        String[] parts = bedId.split("-");
        if (parts.length >= 2) return parts[0] + "-" + parts[1];
        return bedId; // unknown format; treat whole as room
    }

//     Ensure all occupied beds in the room are same gender as newGender
    private void enforceRoomGender(String bedId, Gender newGender) {
        String roomKey = roomKeyOf(bedId);

        Gender found = null;
        for (Map.Entry<String, Bed> e : beds.entrySet()) {
            String id = e.getKey();
            if (!id.startsWith(roomKey + "-")) continue;
            Bed b = e.getValue();
            if (!b.isVacant()) {
                Gender g = b.occupant.gender;
                if (found == null) found = g;
                else if (found != g)
                    throw new ComplianceException("Data integrity: room " + roomKey + " contains mixed genders.");
            }
        }
        if (found != null && found != newGender) {
            throw new RoomGenderConflictException(
                    "Room " + roomKey + " already has residents of gender " + found +
                            "; cannot assign/move a " + newGender + " resident.");
        }
    }

//    JDBC Integration
public void rawPutStaff(Staff s) {
        staffById.put(s.getId(), s);
        switch (s.getRole()) {
            case DOCTOR -> { if (!doctorIds.contains(s.getId())) doctorIds.add(s.getId()); }
            case NURSE  -> { if (!nurseIds.contains(s.getId()))  nurseIds.add(s.getId()); }
            case MANAGER -> managerId = s.getId();
        }
    }
    public void rawSetCredentials(String staffId, String username, String password) {
        Staff s = staffById.get(staffId);
        if (s != null) s.setCredentials(username, password);
    }
    public void rawSetResidentInBed(String bedId, Resident r) {
        Bed b = beds.computeIfAbsent(bedId, Bed::new);
        b.occupant = r;
    }
    public void rawAddPrescription(String residentId, Prescription p) {
        prescriptionsByResident.computeIfAbsent(residentId, k -> new ArrayList<>()).add(p);
    }
    public void rawAddAdministration(Administration a) { administrations.add(a); }
    public void rawAddShift(Shift s) { shifts.add(s); }
    public void rawAddArchive(ArchivedStay a) { archives.add(a); }
    public void rawAddLog(ActionLog l) { logs.add(l); }


    // Logging
    private void log(String staffId, String action) {
        logs.add(new ActionLog(staffId, action));
        System.out.println("[LOG] " + staffId + ": " + action);
    }

    public List<ActionLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    private static final java.util.regex.Pattern RID = java.util.regex.Pattern.compile("^R(\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private int maxResidentNumericId() {
        int max = 0;

        // Active occupants
        for (Bed b : beds.values()) {
            if (b != null && !b.isVacant() && b.occupant != null && b.occupant.id != null) {
                java.util.regex.Matcher m = RID.matcher(b.occupant.id.trim());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }

        // Archived stays
        for (ArchivedStay s : archives) {
            // adapt to your archived model fields
            String rid = null;
            if (s.residentName != null && s.residentId != null) rid = s.residentId;
            else if (s.residentId != null) rid = s.residentId;

            if (rid != null) {
                java.util.regex.Matcher m = RID.matcher(rid.trim());
                if (m.matches()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }

        return max;
    }

    private String nextResidentId() {
        return "R" + (maxResidentNumericId() + 1);
    }

    private boolean isResidentIdActive(String residentId) {
        if (residentId == null) return false;
        String probe = residentId.trim();
        for (Bed b : beds.values()) {
            if (b != null && !b.isVacant()
                    && b.occupant != null
                    && b.occupant.id != null
                    && b.occupant.id.trim().equalsIgnoreCase(probe)) {
                return true;
            }
        }
        return false;
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
