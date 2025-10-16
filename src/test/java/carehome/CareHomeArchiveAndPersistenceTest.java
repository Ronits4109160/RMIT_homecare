// src/test/java/carehome/CareHomeArchiveAndPersistenceTest.java
package carehome;

import carehome.model.*;
import carehome.service.CareHome;
import carehome.persistence.JdbcStore;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeArchiveAndPersistenceTest {

    @Test
    void dischargeCreatesArchive() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();

        // staff
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");
        ch.addOrUpdateStaff("M1",     new Staff("N1", "Nina",   Role.NURSE),   "n","p");

        // manager shift just to authorize setup actions
        var now = LocalDateTime.now();
        ch.allocateShift("M1", new Shift("M1", now.minusMinutes(5), now.plusHours(1)));

        // nurse shift :  14:00–22:00 window
        LocalDate day = LocalDate.now();
        LocalDateTime nurseStart = LocalDateTime.of(day, LocalTime.of(14, 0));
        if (now.isAfter(nurseStart.plusHours(8))) { // if it's already past 22:00 today, use tomorrow
            day = day.plusDays(1);
            nurseStart = LocalDateTime.of(day, LocalTime.of(14, 0));
        }
        LocalDateTime nurseEnd = nurseStart.plusHours(8);
        ch.allocateShift("M1", new Shift("N1", nurseStart, nurseEnd));

        // put a resident in a bed
        String bed = ch.getBeds().keySet().iterator().next();
        Resident r = new Resident("R8", "Ivy", Gender.FEMALE, 48);
        ch.addResidentToBed("M1", bed, r);

        // discharge DURING nurse shift
        var when = nurseStart.plusHours(1);
        ch.dischargeResident("N1", bed, when);

        assertTrue(ch.getArchives().stream().anyMatch(a -> "R8".equals(a.residentId)));
        assertTrue(ch.getBeds().get(bed).isVacant());
    }

    @Test
    void jdbcRoundTripPersistsCoreData() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");

        var now = LocalDateTime.now();
        ch.allocateShift("M1", new Shift("M1", now.minusMinutes(5), now.plusHours(1)));

        String bed = ch.getBeds().keySet().iterator().next();
        Resident r = new Resident("R10", "Zara", Gender.FEMALE, 29);
        ch.addResidentToBed("M1", bed, r);

        JdbcStore store = new JdbcStore();
        store.init();
        store.saveAll(ch);

        CareHome loaded = store.loadAll();
        assertTrue(loaded.hasAnyBeds());
        assertEquals(ch.getBeds().size(), loaded.getBeds().size());
        var occ = loaded.getBeds().get(bed);
        assertNotNull(occ);
        assertFalse(occ.isVacant());
        assertEquals("R10", occ.occupant.id);
    }
}
