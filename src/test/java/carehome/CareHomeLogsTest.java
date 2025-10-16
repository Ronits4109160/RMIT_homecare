package carehome;

import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeLogsTest {

    private static boolean hasActionContaining(List<ActionLog> logs, String needle) {
        return logs.stream().anyMatch(l -> l.getAction() != null && l.getAction().contains(needle));
    }

    @Test
    void logsAreAppendedForKeyActions_andInOrder() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();

        // add staff (manager, doctor, nurse)
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1","Manager", Role.MANAGER), "m", "p");
        ch.addOrUpdateStaff("M1",     new Staff("D1","Doc",     Role.DOCTOR),  "d", "p");
        ch.addOrUpdateStaff("M1",     new Staff("N1","Nurse",   Role.NURSE),   "n", "p");

        // allocate shifts (valid per rules)
        LocalDate today = LocalDate.now();
        ch.allocateShift("M1", new Shift("M1", LocalDateTime.of(today, LocalTime.of(8,0)),  LocalDateTime.of(today, LocalTime.of(16,0))));
        ch.allocateShift("M1", new Shift("D1", LocalDateTime.of(today, LocalTime.of(11,0)), LocalDateTime.of(today, LocalTime.of(12,0)))); // doctor exactly 1h
        ch.allocateShift("M1", new Shift("N1", LocalDateTime.of(today, LocalTime.of(14,0)), LocalDateTime.of(today, LocalTime.of(22,0)))); // nurse valid window

        // resident + meds + admin + discharge
        String bed1 = ch.getBeds().keySet().iterator().next();
        Resident r = new Resident("RL1", "Liam", Gender.MALE, 71);
        ch.addResidentToBed("M1", bed1, r);

        // move within nurse shift
        String bed2 = ch.getBeds().keySet().stream().skip(1).findFirst().orElse(bed1);
        ch.moveResident("N1", bed1, bed2, LocalDateTime.of(today, LocalTime.of(15,0)));

        // prescribe during doctor shift
        Prescription p = new Prescription("PLOG", "D1", r.id, LocalDateTime.of(today, LocalTime.of(11,0)),
                List.of(new MedicationDose("Amox","500mg","8h")));
        ch.addPrescription("D1", bed2, p, LocalDateTime.of(today, LocalTime.of(11,0)));

        // administer during nurse shift
        ch.administerMedication("N1", bed2,
                new Administration("N1", "PLOG", "Amox", LocalDateTime.of(today, LocalTime.of(15,0)), "dose 1"),
                LocalDateTime.of(today, LocalTime.of(15,0)));

        // discharge during nurse shift
        ch.dischargeResident("N1", bed2, LocalDateTime.of(today, LocalTime.of(16,0)));

        // assertions on logs
        var logs = ch.getLogs();
        assertTrue(logs.size() >= 6, "expect several log entries");

        assertTrue(hasActionContaining(logs, "ADD/UPDATE STAFF"), "staff updates should be logged");
        assertTrue(hasActionContaining(logs, "ALLOCATE SHIFT"),   "shift allocation should be logged");
        assertTrue(hasActionContaining(logs, "ADD RESIDENT"),     "admission should be logged");
        assertTrue(hasActionContaining(logs, "MOVE RESIDENT"),    "move should be logged");
        assertTrue(hasActionContaining(logs, "ADD PRESCRIPTION"), "prescription should be logged");
        assertTrue(hasActionContaining(logs, "ADMINISTER"),       "administration should be logged");
        assertTrue(hasActionContaining(logs, "DISCHARGE"),        "discharge should be logged");

        // ordering sanity: find first and last timestamps are non-decreasing
        assertTrue(!logs.isEmpty());
        for (int i = 1; i < logs.size(); i++) {
            assertFalse(logs.get(i).getTime().isBefore(logs.get(i-1).getTime()),
                    "log timestamps should be non-decreasing");
        }
    }
}
