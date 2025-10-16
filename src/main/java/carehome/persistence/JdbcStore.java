package carehome.persistence;


// small helper class.
import carehome.model.*;
import carehome.service.CareHome;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class JdbcStore {
    private final String url;

    public JdbcStore() { this(makeUrl()); }

    public JdbcStore(String url) {
        this.url = Objects.requireNonNull(url);
        ensureDriverLoaded();
    }

    private static void ensureDriverLoaded() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found on classpath", e);
        }
    }

    private static String makeUrl() {
        var dir = java.nio.file.Paths.get("data");
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        var db = dir.resolve("carehome.db");
        return "jdbc:sqlite:" + db;
    }

    /** Create tables if missing. */
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

    private static void setStr(PreparedStatement ps, int idx, String v) throws SQLException {
        if (v == null || v.isBlank()) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, v);
    }
    private static void setLdt(PreparedStatement ps, int idx, LocalDateTime dt) throws SQLException {
        setStr(ps, idx, dt == null ? null : dt.toString());
    }
    private static LocalDateTime defaultIfNull(LocalDateTime dt) {
        return (dt == null ? LocalDateTime.now() : dt);
    }
    private static LocalDateTime parseLdt(String s) {
        return (s == null || s.isBlank()) ? LocalDateTime.now() : LocalDateTime.parse(s);
    }
    private static void setActionLogTime(PreparedStatement ps, int idx, ActionLog al) throws SQLException {
        setLdt(ps, idx, al == null ? null : al.getTime());
    }

    //  SAVE
    public void saveAll(CareHome ch) {
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);

            // clear tables
            for (String t : List.of(
                    "logs","archive_administrations","archive_medication_doses",
                    "archive_prescriptions","archives","administrations","medication_doses",
                    "prescriptions","bed_occupancy","residents","beds","shifts","staff","meta")) {
                try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM " + t); }
            }

            // staff
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO staff(id,name,role,username,password) VALUES(?,?,?,?,?)")) {
                for (Staff s : ch.getStaffById().values()) {
                    setStr(ps, 1, s.getId());
                    setStr(ps, 2, s.getName());
                    setStr(ps, 3, s.getRole() == null ? null : s.getRole().name());
                    setStr(ps, 4, s.getUsername());
                    setStr(ps, 5, s.getPassword());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            // meta: managerId
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meta(k,v) VALUES('managerId', ?)")) {
                setStr(ps, 1, ch.getManagerId());
                ps.executeUpdate();
            }

            // shifts
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO shifts(staff_id,start_ts,end_ts) VALUES(?,?,?)")) {
                for (Shift s : ch.getShifts()) {
                    setStr(ps, 1, s.getStaffId());
                    setLdt(ps, 2, s.getStart());
                    setLdt(ps, 3, s.getEnd());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // beds + occupancy + residents
            try (PreparedStatement pb = c.prepareStatement(
                    "INSERT OR IGNORE INTO beds(bed_id) VALUES(?)");
                 PreparedStatement po = c.prepareStatement(
                         "INSERT OR REPLACE INTO bed_occupancy(bed_id,resident_id) VALUES(?,?)");
                 PreparedStatement pr = c.prepareStatement(
                         "INSERT OR REPLACE INTO residents(id,name,gender,age) VALUES(?,?,?,?)")) {
                for (var e : ch.getBeds().entrySet()) {
                    String bedId = e.getKey();
                    Bed b = e.getValue();
                    setStr(pb, 1, bedId); pb.addBatch();

                    if (b != null && !b.isVacant()) {
                        Resident r = b.occupant;
                        setStr(pr, 1, r.id);
                        setStr(pr, 2, r.name);
                        setStr(pr, 3, r.gender == null ? null : r.gender.name());
                        pr.setInt(4, r.age);
                        pr.addBatch();

                        setStr(po, 1, bedId);
                        setStr(po, 2, r.id);
                        po.addBatch();
                    }
                }
                pb.executeBatch(); pr.executeBatch(); po.executeBatch();
            }

            // prescriptions + doses
            try (PreparedStatement pp = c.prepareStatement(
                    "INSERT INTO prescriptions(id,doctor_id,resident_id,created_ts) VALUES(?,?,?,?)");
                 PreparedStatement pm = c.prepareStatement(
                         "INSERT INTO medication_doses(presc_id,medicine,dose,freq) VALUES(?,?,?,?)")) {

                for (var entry : ch.getBeds().entrySet()) {
                    Bed b = entry.getValue();
                    if (b == null || b.isVacant()) continue;

                    List<Prescription> prescs = ch.getPrescriptionsForResident(b.occupant.id);
                    if (prescs == null) continue;

                    for (Prescription p : prescs) {
                        if (p == null) continue;
                        setStr(pp, 1, p.id);
                        setStr(pp, 2, p.doctorId);
                        setStr(pp, 3, p.residentId);
                        // created_ts comes from ActionLog (Prescription.timeCreated)
                        setActionLogTime(pp, 4, p.timeCreated);
                        pp.addBatch();

                        if (p.meds != null) {
                            for (MedicationDose md : p.meds) {
                                if (md == null) continue;
                                setStr(pm, 1, p.id);
                                setStr(pm, 2, md.medicine);
                                setStr(pm, 3, md.dosage);     // DB column is 'dose'
                                setStr(pm, 4, md.frequency);
                                pm.addBatch();
                            }
                        }
                    }
                }
                pp.executeBatch(); pm.executeBatch();
            }

            // administrations (active)
            try (PreparedStatement pa = c.prepareStatement(
                    "INSERT INTO administrations(nurse_id,presc_id,medicine,time_ts,notes) VALUES(?,?,?,?,?)")) {
                for (Administration a : getAllCurrentAdministrations(ch)) {
                    if (a == null) continue;
                    setStr(pa, 1, a.nurseId);
                    setStr(pa, 2, a.prescriptionId);
                    setStr(pa, 3, a.medicine);
                    setActionLogTime(pa, 4, a.time);
                    setStr(pa, 5, a.notes);
                    pa.addBatch();
                }
                pa.executeBatch();
            }

            // archives (flatten)
            try (PreparedStatement sa = c.prepareStatement(
                    "INSERT INTO archives(discharged_ts,resident_id,resident_name,gender,age,bed_id) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement sp = c.prepareStatement(
                         "INSERT INTO archive_prescriptions(stay_rowid,id,doctor_id,resident_id,created_ts) VALUES(?,?,?,?,?)");
                 PreparedStatement sm = c.prepareStatement(
                         "INSERT INTO archive_medication_doses(presc_id,medicine,dose,freq) VALUES(?,?,?,?)");
                 PreparedStatement sn = c.prepareStatement(
                         "INSERT INTO archive_administrations(stay_rowid,nurse_id,presc_id,medicine,time_ts,notes) VALUES(?,?,?,?,?,?)")) {

                for (ArchivedStay s : ch.getArchives()) {
                    if (s == null) continue;

                    setLdt(sa, 1, s.dischargedAt);
                    setStr(sa, 2, s.residentId);
                    setStr(sa, 3, s.residentName);
                    setStr(sa, 4, s.gender == null ? null : s.gender.name());
                    sa.setInt(5, s.age);
                    setStr(sa, 6, s.lastBedId);
                    sa.executeUpdate();

                    long stayRowId;
                    try (ResultSet rs = sa.getGeneratedKeys()) { rs.next(); stayRowId = rs.getLong(1); }

                    if (s.prescriptions != null) {
                        for (Prescription p : s.prescriptions) {
                            if (p == null) continue;
                            sp.setLong(1, stayRowId);
                            setStr(sp, 2, p.id);
                            setStr(sp, 3, p.doctorId);
                            setStr(sp, 4, p.residentId);
                            // created_ts for archived prescriptions (ActionLog on Prescription)
                            setActionLogTime(sp, 5, p.timeCreated);
                            sp.addBatch();

                            if (p.meds != null) {
                                for (MedicationDose md : p.meds) {
                                    if (md == null) continue;
                                    setStr(sm, 1, p.id);
                                    setStr(sm, 2, md.medicine);
                                    setStr(sm, 3, md.dosage);
                                    setStr(sm, 4, md.frequency);
                                    sm.addBatch();
                                }
                            }
                        }
                    }

                    if (s.administrations != null) {
                        for (Administration a : s.administrations) {
                            if (a == null) continue;

                            sn.setLong(1, stayRowId);
                            setStr(sn, 2, a.nurseId);
                            setStr(sn, 3, a.prescriptionId);
                            setStr(sn, 4, a.medicine);
                            // was: setLdt(sn, 5, a.time);  // a.time is ActionLog
                            setActionLogTime(sn, 5, a.time);   // uses ActionLog.getTime() safely
                            setStr(sn, 6, a.notes);
                            sn.addBatch();
                        }
                    }


                    sp.executeBatch(); sm.executeBatch(); sn.executeBatch();
                }
            }

            // logs
            try (PreparedStatement pl = c.prepareStatement(
                    "INSERT INTO logs(time_ts,staff_id,action) VALUES(?,?,?)")) {
                for (ActionLog l : ch.getLogs()) {
                    if (l == null) continue;
                    setLdt(pl, 1, l.getTime());
                    setStr(pl, 2, l.getStaffId());
                    setStr(pl, 3, l.getAction());
                    pl.addBatch();
                }
                pl.executeBatch();
            }

            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** LOAD  */
    public CareHome loadAll() {
        CareHome ch = new CareHome();
        try (Connection c = DriverManager.getConnection(url)) {
            c.setAutoCommit(false);

            // staff
            Map<String, Staff> staff = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,name,role,username,password FROM staff");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Staff s = new Staff(rs.getString(1), rs.getString(2),
                            Role.valueOf(rs.getString(3)));
                    ch.rawPutStaff(s);
                    ch.rawSetCredentials(s.getId(), rs.getString(4), rs.getString(5));
                    staff.put(s.getId(), s);
                }
            }

            // beds + occupancy
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT b.bed_id, o.resident_id, r.name, r.gender, r.age
                    FROM beds b
                    LEFT JOIN bed_occupancy o ON b.bed_id=o.bed_id
                    LEFT JOIN residents r ON r.id=o.resident_id
                """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bedId = rs.getString(1);
                    if (rs.getString(2) != null) {
                        Resident r = new Resident(
                                rs.getString(2), rs.getString(3),
                                Gender.valueOf(rs.getString(4)), rs.getInt(5));
                        ch.rawSetResidentInBed(bedId, r);
                    } else {
                        ch.rawSetResidentInBed(bedId, null);
                    }
                }
            }

            // shifts
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT staff_id,start_ts,end_ts FROM shifts");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ch.rawAddShift(new Shift(
                            rs.getString(1),
                            parseLdt(rs.getString(2)),
                            parseLdt(rs.getString(3))
                    ));
                }
            }

            // prescriptions + doses
            Map<String, List<MedicationDose>> doses = new HashMap<>();
            try (PreparedStatement pm = c.prepareStatement(
                    "SELECT presc_id,medicine,dose,freq FROM medication_doses");
                 ResultSet rm = pm.executeQuery()) {
                while (rm.next()) {
                    doses.computeIfAbsent(rm.getString(1), k -> new ArrayList<>())
                            .add(new MedicationDose(rm.getString(2), rm.getString(3), rm.getString(4)));
                }
            }
            try (PreparedStatement pp = c.prepareStatement(
                    "SELECT id,doctor_id,resident_id,created_ts FROM prescriptions");
                 ResultSet rp = pp.executeQuery()) {
                while (rp.next()) {
                    String pid = rp.getString(1);
                    Prescription p = new Prescription(
                            pid, rp.getString(2), rp.getString(3),
                            parseLdt(rp.getString(4)),
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
                            parseLdt(ra.getString(4)), ra.getString(5)));
                }
            }

            // archives (flattened)
            try (PreparedStatement sa = c.prepareStatement(
                    "SELECT rowid,discharged_ts,resident_id,resident_name,gender,age,bed_id FROM archives");
                 ResultSet rs = sa.executeQuery()) {
                while (rs.next()) {
                    long row = rs.getLong(1);
                    LocalDateTime when = parseLdt(rs.getString(2));
                    String rid  = rs.getString(3);
                    String name = rs.getString(4);
                    Gender g    = Gender.valueOf(rs.getString(5));
                    int age     = rs.getInt(6);
                    String bedId= rs.getString(7);

                    // archived prescriptions + doses
                    Map<String, List<MedicationDose>> adoses = new HashMap<>();
                    try (PreparedStatement sm = c.prepareStatement(
                            "SELECT presc_id,medicine,dose,freq FROM archive_medication_doses " +
                                    "WHERE presc_id IN (SELECT id FROM archive_prescriptions WHERE stay_rowid=?)")) {
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
                                        parseLdt(rp.getString(4)),
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
                                        parseLdt(rn.getString(4)), rn.getString(5)));
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
                            rl.getString(2),
                            rl.getString(3),
                            parseLdt(rl.getString(1))
                    ));
                }
            }

            c.commit();
        } catch (SQLException e) { throw new RuntimeException(e); }
        return ch;
    }

    private List<Administration> getAllCurrentAdministrations(CareHome ch) {
        try {
            var f = CareHome.class.getDeclaredField("administrations");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Administration> list = (List<Administration>) f.get(ch);
            return (list == null) ? new java.util.ArrayList<>() : new java.util.ArrayList<>(list);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
