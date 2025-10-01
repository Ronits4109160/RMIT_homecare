package carehome.service;


import carehome.model.Role;
import java.time.LocalDateTime;

import carehome.exception.*;
import carehome.model.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import carehome.model.Gender;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import carehome.model.Gender;
import carehome.exception.ValidationException;


public final class CareHome implements Serializable {
    private static final long serialVersionUID = 1L;

    // === persistent state ===
    private final Map<String, Staff> staff = new HashMap<>();
    private final Map<String, Resident> residents = new HashMap<>();
    private final Map<String, Bed> beds = new HashMap<>();
    private final Map<String, Prescription> prescriptions = new HashMap<>();
    private final List<ActionLog> logs = new ArrayList<>();

    // separate lists per spec (kept in sync)
    private final List<Staff> nurses = new ArrayList<>();
    private final List<Staff> doctors = new ArrayList<>();

    private int presCounter = 1;

    // === non-persistent ===
    private transient Clock clock;

    public CareHome() { this(Clock.systemDefaultZone()); }
    public CareHome(Clock clock) { this.clock = clock; }

    private void ensureClock(){ if (clock == null) clock = Clock.systemDefaultZone(); }

    // --- helpers ---
    private Staff requireStaff(String staffId){
        Staff s = staff.get(staffId);
        if(s==null) throw new NotFoundException("No such staff: "+staffId);
        return s;
    }
    private Bed requireBed(String bedId){
        Bed b = beds.get(bedId);
        if(b==null) throw new NotFoundException("No such bed: "+bedId);
        return b;
    }
    private void requireRole(Staff s, Role role){
        if(s.role!=role) throw new UnauthorizedException("Staff "+s.id+" must be "+role);
    }
    private void requireRostered(String staffId, LocalDateTime t){
        Staff s = requireStaff(staffId);
        boolean ok = s.shifts.stream().anyMatch(sh -> sh.covers(t));
        if(!ok) throw new NotRosteredException("Staff "+s.id+" not rostered at "+t);
    }

    public void rebuildRoleLists() {
        nurses.clear();
        doctors.clear();
        for (Staff s : staff.values()) {
            if (s.role == Role.NURSE) nurses.add(s); else doctors.add(s);
        }
    }
    private void log(String staffId, String msg){
        ensureClock();
        logs.add(new ActionLog(LocalDateTime.now(clock), staffId, msg));
    }

    private void syncRoleListsOnAddOrUpdate(Staff s, Role newRole){
        nurses.remove(s);
        doctors.remove(s);
        if (newRole == Role.NURSE) {
            nurses.add(s);
        } else if (newRole == Role.DOCTOR) {
            doctors.add(s);
        }
    }
    private void requireAdminOrRostered(String staffId, LocalDateTime t){
        Staff s = requireStaff(staffId);
        if (s.role == Role.MANAGER) return;   // managers can act any time
        requireRostered(staffId, t);           // others must be rostered
    }


    // --- API: staff & shifts ---
    public void addOrUpdateStaff(String actorId, String id, String name, Role role, String password){
        requireAdminOrRostered(actorId, LocalDateTime.now());

        Staff s = staff.get(id);
        if(s==null){
            s = new Staff(id,name,role,password);
            staff.put(id,s);
            syncRoleListsOnAddOrUpdate(s, role);
            log(actorId, "ADD STAFF "+s);
        }else{
            s.name=name;
            if(s.role!=role){ syncRoleListsOnAddOrUpdate(s, role); }
            s.role=role;
            s.password=password;
            log(actorId, "UPDATE STAFF "+s);
        }
    }

    public void allocateOrModifyShift(String actorId, String staffId, LocalDateTime start, LocalDateTime end){
        requireAdminOrRostered(actorId, LocalDateTime.now());

        Staff s = requireStaff(staffId);
        Shift candidate = new Shift(start, end);

        // weekly 40h limit for everyone
        LocalDate week = candidate.weekStart();
        long weeklyHours = s.shifts.stream()
                .filter(sh -> sh.weekStart().equals(week))
                .mapToLong(Shift::hours).sum() + candidate.hours();
        if(weeklyHours > 40) throw new ShiftRuleException("Weekly hours would be "+weeklyHours+">40 for "+s.id);

        // extra nurse rules
        if(s.role == Role.NURSE){
            if(!isAllowedNurseShift(candidate)){
                throw new ShiftRuleException("Nurse shift must be exactly 08:00-16:00 or 14:00-22:00 (same day).");
            }
            LocalDate day = candidate.start.toLocalDate();
            long dayHours = s.shifts.stream()
                    .filter(sh -> sh.start.toLocalDate().equals(day))
                    .mapToLong(Shift::hours).sum() + candidate.hours();
            if(dayHours > 8){
                throw new ShiftRuleException("Nurse "+s.id+" would exceed 8 hours on "+day+" (total "+dayHours+"h).");
            }
        }

        s.shifts.add(candidate);
        log(actorId, "ALLOCATE SHIFT for "+s.id+" "+start+".."+end);
    }

    public String pickManager(){
        return staff.values().stream()
                .filter(s -> s.role == Role.MANAGER)
                .map(s -> s.id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No manager exists"));
    }


    private boolean isAllowedNurseShift(Shift sh){
        LocalDate d = sh.start.toLocalDate();
        // same-day & exact 8h
        if(!sh.end.toLocalDate().equals(d)) return false;
        if(sh.hours()!=8) return false;
        LocalTime st = sh.start.toLocalTime();
        LocalTime en = sh.end.toLocalTime();
        return (st.equals(LocalTime.of(8,0)) && en.equals(LocalTime.of(16,0))) ||
                (st.equals(LocalTime.of(14,0)) && en.equals(LocalTime.of(22,0)));
    }

    public void nurseMoveResident(String nurseId, String fromBedId, String toBedId){
        requireRostered(nurseId, LocalDateTime.now());
        Staff n = requireStaff(nurseId); requireRole(n, Role.NURSE);
        Bed from = requireBed(fromBedId); Bed to = requireBed(toBedId);
        if(from.isVacant()) throw new NotFoundException("From bed vacant");
        if(!to.isVacant()) throw new BedOccupiedException("To bed occupied");
        to.occupant = from.occupant; from.occupant = null;
        log(nurseId, "MOVE RESIDENT "+to.occupant+" from "+fromBedId+" to "+toBedId);
    }


    public Resident checkResidentInBed(String actorId, String bedId){
        requireRostered(actorId, LocalDateTime.now());
        Staff s = requireStaff(actorId);
        if(!(s.role==Role.DOCTOR || s.role==Role.NURSE)) throw new UnauthorizedException("Medical staff only");
        Bed b = requireBed(bedId);
        if(b.isVacant()) throw new NotFoundException("Bed "+bedId+" is vacant");
        log(actorId, "CHECK RESIDENT in bed "+bedId+" -> "+b.occupant);
        return b.occupant;
    }

    public Prescription doctorAttachPrescription(String doctorId, String bedId, List<MedicationDose> doses){
        requireRostered(doctorId, LocalDateTime.now());
        Staff d = requireStaff(doctorId); requireRole(d, Role.DOCTOR);
        Bed b = requireBed(bedId); if(b.isVacant()) throw new NotFoundException("Bed "+bedId+" is vacant");
        String pid = "P"+(presCounter++);
        Prescription p = new Prescription(pid, b.occupant.id, d.id);
        p.schedule.addAll(doses);
        prescriptions.put(pid, p);
        log(doctorId, "ADD PRESCRIPTION "+p+" for bed "+bedId);
        return p;
    }

    public void addResidentToVacantBed(String actorId, String residentId, String name, String bedId){
        // default if caller didn’t provide gender/age (keeps old code working)
        addResidentToVacantBed(actorId, residentId, name, Gender.MALE, 0, bedId);
    }

    public void addResidentToVacantBed(String actorId,
                                       String residentId,
                                       String name,
                                       Gender gender,
                                       int age,
                                       String bedId) {
        requireRostered(actorId, LocalDateTime.now());

        if (age < 0 || age > 130) {
            throw new ValidationException("Age out of range (0–130): " + age);
        }

        Resident r = residents.get(residentId);
        if (r == null) {
            r = new Resident(residentId, name, gender, age);
            residents.put(residentId, r);
        } else {
            // Update existing resident’s details
            r.name = name;
            r.gender = gender;
            r.age = age;
        }

        Bed b = requireBed(bedId);
        if (!b.isVacant()) {
            throw new BedOccupiedException("Bed " + bedId + " is occupied by " + b.occupant);
        }

        b.occupant = r;
        log(actorId, "ADD RESIDENT " + r + " to bed " + bedId);
    }


    public Administration administerPrescription(String nurseId, String prescriptionId, String medicine, String dose, LocalDateTime time){
        requireRostered(nurseId, time);
        Staff n = requireStaff(nurseId); requireRole(n, Role.NURSE);
        Prescription p = prescriptions.get(prescriptionId);
        if(p==null) throw new NotFoundException("No such prescription: "+prescriptionId);
        Administration admin = new Administration(p.residentId, prescriptionId, medicine, dose, time, nurseId);
        p.administrations.add(admin);
        log(nurseId, "ADMINISTER "+medicine+" "+dose+" for resident "+p.residentId+" under "+prescriptionId);
        return admin;
    }

    public void updateAdministrationNotes(String actorId, Administration admin, String newNotes){
        requireRostered(actorId, LocalDateTime.now());
        admin.notes = newNotes;
        log(actorId, "UPDATE ADMIN NOTES pres="+admin.prescriptionId+" -> "+newNotes);
    }

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("EEE dd-MMM", Locale.ENGLISH);

    public void checkCompliance(LocalDate weekStart){
        // 7 days from weekStart
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) days.add(weekStart.plusDays(i));

        List<LocalDate> missingMorning = new ArrayList<>();
        List<LocalDate> missingEvening = new ArrayList<>();
        List<LocalDate> missingDoctor  = new ArrayList<>();
        List<String>   nurseOverCap    = new ArrayList<>();

        for (LocalDate day : days) {
            boolean morningCovered = anyNurseShift(day, LocalTime.of(8,0),  LocalTime.of(16,0));
            boolean eveningCovered = anyNurseShift(day, LocalTime.of(14,0), LocalTime.of(22,0));
            if (!morningCovered) missingMorning.add(day);
            if (!eveningCovered) missingEvening.add(day);

            boolean docCovered = doctors.stream().anyMatch(doc ->
                    doc.shifts.stream().anyMatch(sh ->
                            sh.start.toLocalDate().equals(day) && sh.hours() >= 1));
            if (!docCovered) missingDoctor.add(day);

            // per-nurse daily cap
            for (Staff n : nurses) {
                long hours = n.shifts.stream()
                        .filter(sh -> sh.start.toLocalDate().equals(day))
                        .mapToLong(Shift::hours).sum();
                if (hours > 8) nurseOverCap.add(n.id + " (" + hours + "h) on " + DAY_FMT.format(day));
            }
        }

        if (!missingMorning.isEmpty() || !missingEvening.isEmpty()
                || !missingDoctor.isEmpty() || !nurseOverCap.isEmpty()) {

            StringBuilder sb = new StringBuilder();
            sb.append("Compliance check FAILED for week starting ")
                    .append(DAY_FMT.format(weekStart)).append('\n');

            if (!missingMorning.isEmpty())
                sb.append(" • Missing nurse shift 08:00–16:00 on: ")
                        .append(fmtDays(missingMorning)).append('\n');

            if (!missingEvening.isEmpty())
                sb.append(" • Missing nurse shift 14:00–22:00 on: ")
                        .append(fmtDays(missingEvening)).append('\n');

            if (!missingDoctor.isEmpty())
                sb.append(" • Missing doctor coverage (≥1h) on: ")
                        .append(fmtDays(missingDoctor)).append('\n');

            if (!nurseOverCap.isEmpty())
                sb.append(" • Nurse assigned >8h in a day: ")
                        .append(String.join(", ", nurseOverCap)).append('\n');

            sb.append("Tips: add nurse shifts exactly 08:00–16:00 and 14:00–22:00; add ≥1h doctor per day.");
            throw new ComplianceException(sb.toString());
        }
    }

    private static String fmtDays(Collection<LocalDate> ds){
        return ds.stream().sorted().map(DAY_FMT::format).collect(java.util.stream.Collectors.joining(", "));
    }

    private boolean anyNurseShift(LocalDate day, LocalTime start, LocalTime end){
        return nurses.stream().anyMatch(n ->
                n.shifts.stream().anyMatch(sh ->
                        sh.start.toLocalDate().equals(day)
                                && sh.start.toLocalTime().equals(start)
                                && sh.end.toLocalTime().equals(end)));
    }


    // --- Serialization ---
    public void saveToFile(String path){
        try(ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))){
            oos.writeObject(this);
        }catch(IOException e){
            throw new CareHomeException("Save failed: "+e.getMessage());
        }
    }
    public static CareHome loadFromFile(String path){
        try(ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))){
            CareHome ch = (CareHome) ois.readObject();
            ch.ensureClock(); // restore transient
            return ch;
        }catch(IOException | ClassNotFoundException e){
            throw new CareHomeException("Load failed: "+e.getMessage());
        }
    }

    // --- convenience ---
    public void addBed(String id){ beds.put(id, new Bed(id)); }
    public List<ActionLog> getLogs(){ return logs; }

    public Map<String, Staff> staff(){ return staff; }
    public Map<String, Resident> residents(){ return residents; }
    public Map<String, Bed> beds(){ return beds; }
    public Map<String, Prescription> prescriptions(){ return prescriptions; }

    public List<Staff> getNurses(){ return Collections.unmodifiableList(nurses); }
    public List<Staff> getDoctors(){ return Collections.unmodifiableList(doctors); }
}