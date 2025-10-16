package carehome;

import static org.junit.jupiter.api.Assertions.*;

import carehome.model.*;
import carehome.service.CareHome;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;

public class CareHomeCoreTest {

    @Test
    void seedCreatesBeds() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();
        assertTrue(ch.hasAnyBeds(), "should have beds after seeding");
        assertTrue(ch.getBeds().size() > 0, "beds map should not be empty");
    }

    @Test
    void managerCanBeAddedAndAuthenticated() {
        CareHome ch = new CareHome();
        Staff m = new Staff("M1", "Manager", Role.MANAGER);
        ch.rawPutStaff(m);
        ch.rawSetCredentials("M1", "manager", "pass");
        Staff got = ch.authenticate("M1", "pass");
        assertEquals(Role.MANAGER, got.getRole());
    }

    @Test
    void managerCanAssignSelfShiftAndAddResidentToAnyBed() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();

        Staff m = new Staff("M1", "Manager", Role.MANAGER);
        ch.rawPutStaff(m);
        ch.rawSetCredentials("M1", "manager", "pass");

        // manager assigns a shift to self (authorize with manager id)
        LocalDateTime now = LocalDateTime.now();
        ch.allocateShift("M1", new Shift("M1", now.minusMinutes(5), now.plusHours(2)));

        // pick any bed
        String anyBed = ch.getBeds().keySet().iterator().next();
        Resident r = new Resident("R1", "Alice", Gender.FEMALE, 30);

        // add to bed as manager
        ch.addResidentToBed("M1", anyBed, r);

        Resident inBed = ch.getResidentInBed("M1", anyBed);
        assertNotNull(inBed);
        assertEquals("R1", inBed.id);
    }

    @Test
    void nurseShiftAllowsMoveBetweenBeds() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();

        // Add staff
        ch.rawPutStaff(new Staff("M1", "Manager", Role.MANAGER));
        ch.rawPutStaff(new Staff("N1", "Nina", Role.NURSE));

        // Manager shift: keep it simple and valid "now" so setup calls pass
        LocalDateTime now = LocalDateTime.now();
        ch.allocateShift("M1", new Shift("M1", now.minusMinutes(5), now.plusHours(2)));

        // === Compute NEXT 14:00–22:00 window for nurse ===
        LocalDate day = LocalDate.now();
        LocalDateTime nurseStart = LocalDateTime.of(day, LocalTime.of(14, 0));
        if (now.isAfter(nurseStart.plusHours(7))) { // it's too late today; use tomorrow
            day = day.plusDays(1);
            nurseStart = LocalDateTime.of(day, LocalTime.of(14, 0));
        }
        LocalDateTime nurseEnd = nurseStart.plusHours(8);
        ch.allocateShift("M1", new Shift("N1", nurseStart, nurseEnd));

        // Put a resident in the first bed
        String firstBed = ch.getBeds().keySet().iterator().next();
        String secondBed = ch.getBeds().keySet().stream().skip(1).findFirst().orElse(firstBed);
        ch.addResidentToBed("M1", firstBed, new Resident("R2", "Bob", Gender.MALE, 65));

        // Move DURING the nurse shift (safe inside 14:00–22:00)
        LocalDateTime duringNurseShift = nurseStart.plusHours(1); // 15:00
        ch.moveResident("N1", firstBed, secondBed, duringNurseShift);

        assertTrue(ch.getBeds().get(firstBed).isVacant());
        assertEquals("R2", ch.getBeds().get(secondBed).occupant.id);
    }

}
