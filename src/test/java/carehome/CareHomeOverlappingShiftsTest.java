package carehome;

import carehome.exception.ShiftRuleException;
import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeOverlappingShiftsTest {

    @Test
    void overlappingShiftsForSameStaffAreRejected() {
        CareHome ch = new CareHome();

        // manager who assigns and is also the staff being scheduled
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1","Manager", Role.MANAGER), "m", "p");

        LocalDate d = LocalDate.now();
        // first shift: 08:00–12:00
        ch.allocateShift("M1", new Shift("M1",
                LocalDateTime.of(d, LocalTime.of(8,0)),
                LocalDateTime.of(d, LocalTime.of(12,0))));

        // attempt an overlapping shift for the SAME staff (11:00–13:00) -> must fail
        assertThrows(ShiftRuleException.class, () ->
                ch.allocateShift("M1", new Shift("M1",
                        LocalDateTime.of(d, LocalTime.of(11,0)),
                        LocalDateTime.of(d, LocalTime.of(13,0))))
        );
    }

    @Test
    void backToBackShiftsForSameStaffAreAllowed() {
        CareHome ch = new CareHome();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1","Manager", Role.MANAGER), "m", "p");

        LocalDate d = LocalDate.now();
        // 08:00–12:00 then 12:00–16:00  - should be OK
        ch.allocateShift("M1", new Shift("M1",
                LocalDateTime.of(d, LocalTime.of(8,0)),
                LocalDateTime.of(d, LocalTime.of(12,0))));
        assertDoesNotThrow(() ->
                ch.allocateShift("M1", new Shift("M1",
                        LocalDateTime.of(d, LocalTime.of(12,0)),
                        LocalDateTime.of(d, LocalTime.of(16,0))))
        );
    }

    @Test
    void overlapsAcrossDifferentStaffAreAllowed_whenEachShiftMeetsRoleRules() {
        CareHome ch = new CareHome();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1","Manager", Role.MANAGER), "m", "p");
        ch.addOrUpdateStaff("M1",     new Staff("N1","Nina",   Role.NURSE),   "n", "p");
        ch.addOrUpdateStaff("M1",     new Staff("D1","Doc",    Role.DOCTOR),  "d", "p");

        LocalDate d = LocalDate.now();

        // manager 08–16
        ch.allocateShift("M1", new Shift("M1",
                LocalDateTime.of(d, LocalTime.of(8,0)),
                LocalDateTime.of(d, LocalTime.of(16,0))));

        // nurse 14–22
        ch.allocateShift("M1", new Shift("N1",
                LocalDateTime.of(d, LocalTime.of(14,0)),
                LocalDateTime.of(d, LocalTime.of(22,0))));

        // doctor 11–12
        ch.allocateShift("M1", new Shift("D1",
                LocalDateTime.of(d, LocalTime.of(11,0)),
                LocalDateTime.of(d, LocalTime.of(12,0))));

        // No exception expected: different staff can overlap.
        // If any role-specific rule fails, this test will surface it.
        assertTrue(true);
    }
}
