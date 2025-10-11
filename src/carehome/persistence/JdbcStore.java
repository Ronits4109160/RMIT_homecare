package carehome.persistence;

import carehome.model.*;
import carehome.service.CareHome;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class JdbcStore {
    private final String url;

    public JdbcStore() {
        this(makeUrl());
        ensureDriverLoaded();
    }

    private static String makeUrl() {
        java.nio.file.Path dir = java.nio.file.Paths.get("data");
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        java.nio.file.Path db = dir.resolve("carehome.db");
        return "jdbc:sqlite:" + db.toString();
    }
    public void init() {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.executeUpdate("""
                PRAGMA foreign_keys=ON;
                CREATE TABLE IF NOT EXISTS staff(
                  id TEXT PRIMARY KEY, name TEXT NOT NULL, role TEXT NOT NULL,
                  username TEXT, password TEXT
                );
                CREATE TABLE IF NOT EXISTS shifts(
                  staff_id TEXT NOT NULL, start_ts TEXT NOT NULL, end_ts TEXT NOT NULL,
                  FOREIGN KEY(staff_id) REFERENCES staff(id)
                );
                CREATE TABLE IF NOT EXISTS beds(
                  bed_id TEXT PRIMARY KEY
                );
                CREATE TABLE IF NOT EXISTS residents(
                  id TEXT PRIMARY KEY, name TEXT, gender TEXT, age INTEGER
                );
                CREATE TABLE IF NOT EXISTS bed_occupancy(
                  bed_id TEXT PRIMARY KEY, resident_id TEXT,
                  FOREIGN KEY(bed_id) REFERENCES beds(bed_id),
                  FOREIGN KEY(resident_id) REFERENCES residents(id)
                );
                CREATE TABLE IF NOT EXISTS prescriptions(
                  id TEXT PRIMARY KEY, doctor_id TEXT, resident_id TEXT, created_ts TEXT
                );
                CREATE TABLE IF NOT EXISTS medication_doses(
                  presc_id TEXT, medicine TEXT, dose TEXT, freq TEXT,
                  FOREIGN KEY(presc_id) REFERENCES prescriptions(id)
                );
                CREATE TABLE IF NOT EXISTS administrations(
                  nurse_id TEXT, presc_id TEXT, medicine TEXT, time_ts TEXT, notes TEXT
                );
                CREATE TABLE IF NOT EXISTS archives(
                  discharged_ts TEXT, resident_id TEXT, resident_name TEXT,
                  gender TEXT, age INTEGER, bed_id TEXT
                );
                CREATE TABLE IF NOT EXISTS archive_prescriptions(
                  stay_rowid INTEGER, id TEXT, doctor_id TEXT, resident_id TEXT, created_ts TEXT
                );
                CREATE TABLE IF NOT EXISTS archive_medication_doses(
                  presc_id TEXT, medicine TEXT, dose TEXT, freq TEXT
                );
                CREATE TABLE IF NOT EXISTS archive_administrations(
                  stay_rowid INTEGER, nurse_id TEXT, presc_id TEXT, medicine TEXT, time_ts TEXT, notes TEXT
                );
                CREATE TABLE IF NOT EXISTS logs(
                  time_ts TEXT, staff_id TEXT, action TEXT
                );
                CREATE TABLE IF NOT EXISTS meta(
                  k TEXT PRIMARY KEY, v TEXT
                );
            """);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // -------- SAVE (snapshot) --------
    public void saveAll(CareHome ch) {
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);

            // clear tables
            for (String t : List.of("logs","archive_administrations","archive_medication_doses",
                    "archive_prescriptions","archives","administrations","medication_doses",
                    "prescriptions","bed_occupancy","residents","beds","shifts","staff","meta")) {
                try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM " + t); }
            }

            // staff
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff(id,name,role,username,password) VALUES(?,?,?,?,?)")) {
                for (Staff s : ch.getStaffById().values()) {
                    ps.setString(1, s.getId());
                    ps.setString(2, s.getName());
                    ps.setString(3, s.getRole().name());
                    ps.setString(4, s.getUsername());
                    ps.setString(5, s.getPassword()); // assuming stored as plain or hashed in model
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            // meta: managerId
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO meta(k,v) VALUES('managerId', ?)")) {
                ps.setString(1, ch.getManagerId()); ps.executeUpdate();
            }

            // shifts
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO shifts(staff_id,start_ts,end_ts) VALUES(?,?,?)")) {
                for (Shift s : ch.getShifts()) {
                    ps.setString(1, s.getStaffId());
                    ps.setString(2, s.getStart().toString());
                    ps.setString(3, s.getEnd().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // beds + occupancy
            try (PreparedStatement pb = c.prepareStatement("INSERT OR IGNORE INTO beds(bed_id) VALUES(?)");
                 PreparedStatement po = c.prepareStatement("INSERT OR REPLACE INTO bed_occupancy(bed_id,resident_id) VALUES(?,?)");
                 PreparedStatement pr = c.prepareStatement("INSERT OR REPLACE INTO residents(id,name,gender,age) VALUES(?,?,?,?)")) {
                for (var e : ch.getBeds().entrySet()) {
                    String bedId = e.getKey();
                    Bed b = e.getValue();
                    pb.setString(1, bedId); pb.addBatch();
                    if (!b.isVacant()) {
                        Resident r = b.occupant;
                        pr.setString(1, r.id);
                        pr.setString(2, r.name);
                        pr.setString(3, r.gender.name());
                        pr.setInt(4, r.age);
                        pr.addBatch();
                        po.setString(1, bedId);
                        po.setString(2, r.id);
                        po.addBatch();
                    }
                }
                pb.executeBatch(); pr.executeBatch(); po.executeBatch();
            }

            // prescriptions + doses
            try (PreparedStatement pp = c.prepareStatement("INSERT INTO prescriptions(id,doctor_id,resident_id,created_ts) VALUES(?,?,?,?)");
                 PreparedStatement pm = c.prepareStatement("INSERT INTO medication_doses(presc_id,medicine,dose,freq) VALUES(?,?,?,?)")) {
                for (var entry : ch.getBeds().entrySet()) {
                    Bed b = entry.getValue();
                    if (b.isVacant()) continue;
                    for (Prescription p : ch.getPrescriptionsForResident(b.occupant.id)) {
                        pp.setString(1, p.id);
                        pp.setString(2, p.doctorId);
                        pp.setString(3, p.residentId);
                        pp.setString(4, p.timeCreated.toString());
                        pp.addBatch();
                        if (p.meds != null) for (MedicationDose md : p.meds) {
                            pm.setString(1, p.id);
                            pm.setString(2, md.medicine);
                            pm.setString(3, md.dosage);
                            pm.setString(4, md.frequency);
                            pm.addBatch();
                        }
                    }
                }
                pp.executeBatch(); pm.executeBatch();
            }

            // administrations
            try (PreparedStatement pa = c.prepareStatement("INSERT INTO administrations(nurse_id,presc_id,medicine,time_ts,notes) VALUES(?,?,?,?,?)")) {
                for (Administration a : getAllCurrentAdministrations(ch)) {
                    pa.setString(1, a.nurseId);
                    pa.setString(2, a.prescriptionId);
                    pa.setString(3, a.medicine);
                    pa.setString(4, a.time.toString());
                    pa.setString(5, a.notes);
                    pa.addBatch();
                }
                pa.executeBatch();
            }

            // archives (flatten)
            try (PreparedStatement sa = c.prepareStatement(
                    "INSERT INTO archives(discharged_ts,resident_id,resident_name,gender,age,bed_id) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement sp = c.prepareStatement("INSERT INTO archive_prescriptions(stay_rowid,id,doctor_id,resident_id,created_ts) VALUES(?,?,?,?,?)");
                 PreparedStatement sm = c.prepareStatement("INSERT INTO archive_medication_doses(presc_id,medicine,dose,freq) VALUES(?,?,?,?)");
                 PreparedStatement sn = c.prepareStatement("INSERT INTO archive_administrations(stay_rowid,nurse_id,presc_id,medicine,time_ts,notes) VALUES(?,?,?,?,?,?)")) {
                for (ArchivedStay s : ch.getArchives()) {
                    sa.setString(1, s.dischargedAt.toString());
                    sa.setString(2, s.residentId);
                    sa.setString(3, s.residentName);
                    sa.setString(4, s.gender.name());
                    sa.setInt(5, s.age);
                    sa.setString(6, s.lastBedId);
                    sa.executeUpdate();
                    long stayRowId;
                    try (ResultSet rs = sa.getGeneratedKeys()) { rs.next(); stayRowId = rs.getLong(1); }

                    for (Prescription p : s.prescriptions) {
                        sp.setLong(1, stayRowId);
                        sp.setString(2, p.id);
                        sp.setString(3, p.doctorId);
                        sp.setString(4, p.residentId);
                        sp.setString(5, p.timeCreated.toString());
                        sp.addBatch();
                        if (p.meds != null) for (MedicationDose md : p.meds) {
                            sm.setString(1, p.id);
                            sm.setString(2, md.medicine);
                            sm.setString(3, md.dosage);
                            sm.setString(4, md.frequency);
                            sm.addBatch();
                        }
                    }
                    for (Administration a : s.administrations) {
                        sn.setLong(1, stayRowId);
                        sn.setString(2, a.nurseId);
                        sn.setString(3, a.prescriptionId);
                        sn.setString(4, a.medicine);
                        sn.setString(5, a.time.toString());
                        sn.setString(6, a.notes);
                        sn.addBatch();
                    }
                    sp.executeBatch(); sm.executeBatch(); sn.executeBatch();
                }
            }

            // logs
            try (PreparedStatement pl = c.prepareStatement("INSERT INTO logs(time_ts,staff_id,action) VALUES(?,?,?)")) {
                for (ActionLog l : ch.getLogs()) {
                    pl.setString(1, l.time.toString());
                    pl.setString(2, l.staffId);
                    pl.setString(3, l.action);
                    pl.addBatch();
                }
                pl.executeBatch();
            }

            c.commit();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    // LOAD (rebuild in-memory)
    public CareHome loadAll() {
        CareHome ch = new CareHome();
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);

            // staff
            Map<String, Staff> staff = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT id,name,role,username,password FROM staff");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Staff s = new Staff(rs.getString(1), rs.getString(2), Role.valueOf(rs.getString(3)));
                    ch.rawPutStaff(s);
                    ch.rawSetCredentials(s.getId(), rs.getString(4), rs.getString(5));
                    staff.put(s.getId(), s);
                }
            }
            // manager
            try (PreparedStatement ps = c.prepareStatement("SELECT v FROM meta WHERE k='managerId'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { /* already handled by rawPutStaff when role=MANAGER */ }
            }

            // beds
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT b.bed_id, o.resident_id, r.name, r.gender, r.age
                    FROM beds b
                    LEFT JOIN bed_occupancy o ON b.bed_id=o.bed_id
                    LEFT JOIN residents r ON r.id=o.resident_id
                """); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bedId = rs.getString(1);
                    ch.getBeds();
                    if (rs.getString(2) != null) {
                        Resident r = new Resident(
                                rs.getString(2), rs.getString(3),
                                Gender.valueOf(rs.getString(4)), rs.getInt(5));
                        ch.rawSetResidentInBed(bedId, r);
                    } else {
                        ch.rawSetResidentInBed(bedId, null); // ensures bed exists as vacant
                    }
                }
            }

            // shifts
            try (PreparedStatement ps = c.prepareStatement("SELECT staff_id,start_ts,end_ts FROM shifts");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ch.rawAddShift(new Shift(
                            rs.getString(1),
                            LocalDateTime.parse(rs.getString(2)),
                            LocalDateTime.parse(rs.getString(3))
                    ));
                }
            }

            // prescriptions + doses (active)
            Map<String, List<MedicationDose>> doses = new HashMap<>();
            try (PreparedStatement pm = c.prepareStatement("SELECT presc_id,medicine,dose,freq FROM medication_doses");
                 ResultSet rm = pm.executeQuery()) {
                while (rm.next()) {
                    doses.computeIfAbsent(rm.getString(1), k -> new ArrayList<>())
                            .add(new MedicationDose(rm.getString(2), rm.getString(3), rm.getString(4)));
                }
            }
            try (PreparedStatement pp = c.prepareStatement("SELECT id,doctor_id,resident_id,created_ts FROM prescriptions");
                 ResultSet rp = pp.executeQuery()) {
                while (rp.next()) {
                    String pid = rp.getString(1);
                    Prescription p = new Prescription(
                            pid, rp.getString(2), rp.getString(3),
                            LocalDateTime.parse(rp.getString(4)),
                            doses.getOrDefault(pid, List.of()));
                    ch.rawAddPrescription(p.residentId, p);
                }
            }

            // administrations (active)
            try (PreparedStatement pa = c.prepareStatement(
                    "SELECT nurse_id,presc_id,medicine,time_ts,notes FROM administrations");
                 ResultSet ra = pa.executeQuery()) {
                while (ra.next()) {
                    ch.rawAddAdministration(new Administration(
                            ra.getString(1), ra.getString(2), ra.getString(3),
                            LocalDateTime.parse(ra.getString(4)), ra.getString(5)));
                }
            }

            // archives (flattened)
            try (PreparedStatement sa = c.prepareStatement(
                    "SELECT rowid,discharged_ts,resident_id,resident_name,gender,age,bed_id FROM archives");
                 ResultSet rs = sa.executeQuery()) {
                while (rs.next()) {
                    long row = rs.getLong(1);
                    LocalDateTime when = LocalDateTime.parse(rs.getString(2));
                    String rid = rs.getString(3);
                    String name = rs.getString(4);
                    Gender g = Gender.valueOf(rs.getString(5));
                    int age = rs.getInt(6);
                    String bedId = rs.getString(7);

                    // archived prescriptions + doses
                    Map<String, List<MedicationDose>> adoses = new HashMap<>();
                    try (PreparedStatement sm = c.prepareStatement(
                            "SELECT presc_id,medicine,dose,freq FROM archive_medication_doses WHERE presc_id IN (SELECT id FROM archive_prescriptions WHERE stay_rowid=?)")) {
                        sm.setLong(1, row);
                        try (ResultSet rm = sm.executeQuery()) {
                            while (rm.next()) {
                                adoses.computeIfAbsent(rm.getString(1), k -> new ArrayList<>())
                                        .add(new MedicationDose(rm.getString(2), rm.getString(3), rm.getString(4)));
                            }
                        }
                    }
                    List<Prescription> ap = new ArrayList<>();
                    try (PreparedStatement sp = c.prepareStatement(
                            "SELECT id,doctor_id,resident_id,created_ts FROM archive_prescriptions WHERE stay_rowid=?")) {
                        sp.setLong(1, row);
                        try (ResultSet rp = sp.executeQuery()) {
                            while (rp.next()) {
                                String pid = rp.getString(1);
                                ap.add(new Prescription(pid, rp.getString(2), rp.getString(3),
                                        LocalDateTime.parse(rp.getString(4)),
                                        adoses.getOrDefault(pid, List.of())));
                            }
                        }
                    }
                    List<Administration> aa = new ArrayList<>();
                    try (PreparedStatement sn = c.prepareStatement(
                            "SELECT nurse_id,presc_id,medicine,time_ts,notes FROM archive_administrations WHERE stay_rowid=?")) {
                        sn.setLong(1, row);
                        try (ResultSet rn = sn.executeQuery()) {
                            while (rn.next()) {
                                aa.add(new Administration(rn.getString(1), rn.getString(2), rn.getString(3),
                                        LocalDateTime.parse(rn.getString(4)), rn.getString(5)));
                            }
                        }
                    }
                    ch.rawAddArchive(new ArchivedStay(rid, name, g, age, bedId, when, ap, aa));
                }
            }

            // logs
            try (PreparedStatement pl = c.prepareStatement(
                    "SELECT time_ts,staff_id,action FROM logs");
                 ResultSet rl = pl.executeQuery()) {
                while (rl.next()) {
                    ch.rawAddLog(new ActionLog(
                            rl.getString(2),                       // staff_id
                            rl.getString(3),                       // action
                            java.time.LocalDateTime.parse(rl.getString(1)) // time_ts
                    ));
                }
            }

            c.commit();
        } catch (SQLException e) { throw new RuntimeException(e); }
        return ch;
    }

    // Collect current administrations (active, non-archived)
    private List<Administration> getAllCurrentAdministrations(CareHome ch) {
        // You already fetch by resident for GUI; here we assume administrations field
        // is the current-active list stored in CareHome.
        try {
            var f = CareHome.class.getDeclaredField("administrations");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Administration> list = (List<Administration>) f.get(ch);
            return new ArrayList<>(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
