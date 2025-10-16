// src/test/java/carehome/CareHomeMedsTest.java
package carehome;

import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeMedsTest {

    @Test
    void prescriptionAndAdministrationFlow() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();

        // staff
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");
        ch.addOrUpdateStaff("M1",     new Staff("D1", "Dr Dee", Role.DOCTOR),  "d","p");
        ch.addOrUpdateStaff("M1",     new Staff("N1", "Nina",   Role.NURSE),   "n","p");

        LocalDate today = LocalDate.now();

        // Manager shift : broad window so setup actions are authorized
        LocalDateTime mgrStart = LocalDateTime.of(today, LocalTime.of(8, 0));
        LocalDateTime mgrEnd   = mgrStart.plusHours(8);
        ch.allocateShift("M1", new Shift("M1", mgrStart, mgrEnd));

        // Doctor shift : EXACTLY 1 hour  — prescribe at shift start
        LocalDateTime dStart = LocalDateTime.of(today, LocalTime.of(11, 0));
        LocalDateTime dEnd   = dStart.plusHours(1);
        ch.allocateShift("M1", new Shift("D1", dStart, dEnd));

        // Nurse shift : valid window 14:00–22:00
        LocalDateTime nStart = LocalDateTime.of(today, LocalTime.of(14, 0));
        LocalDateTime nEnd   = nStart.plusHours(8);
        ch.allocateShift("M1", new Shift("N1", nStart, nEnd));

        // Bed + resident
        String bed = ch.getBeds().keySet().iterator().next();
        Resident r = new Resident("R7", "Ria", Gender.FEMALE, 33);
        ch.addResidentToBed("M1", bed, r);

        // Doctor prescribes AT the shift start
        LocalDateTime presTime = dStart;
        Prescription p = new Prescription(
                "P1", "D1", r.id, presTime,
                List.of(
                        new MedicationDose("Amox", "500mg", "8h"),
                        new MedicationDose("VitC",  "1tab",  "24h")
                )
        );
        ch.addPrescription("D1", bed, p, presTime);
        assertTrue(!ch.getPrescriptionsForResident(r.id).isEmpty());

        // Nurse administers during nurse shift
        LocalDateTime adminTime = nStart.plusHours(1);
        Administration a1 = new Administration("N1", "P1", "Amox", adminTime, "first dose");
        ch.administerMedication("N1", bed, a1, adminTime);

        var admins = ch.getAdministrationsForResident(r.id);
        assertTrue(admins.stream().anyMatch(a -> "Amox".equals(a.medicine)));
    }
}

