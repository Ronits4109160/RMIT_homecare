// src/test/java/carehome/CareHomeShiftsTest.java
package carehome;

import carehome.exception.NotRosteredException;
import carehome.exception.ShiftRuleException;
import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeShiftsTest {

    @Test
    void nurseShiftMustBeAllowedWindow() {
        CareHome ch = new CareHome();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");
        ch.addOrUpdateStaff("M1",     new Staff("N1", "Nina",   Role.NURSE),   "n","p");

        LocalDate day = LocalDate.now();
        // 09:00–17:00 is NOT an allowed nurse window -> expect ShiftRuleException
        LocalDateTime badStart = LocalDateTime.of(day, LocalTime.of(9,0));
        LocalDateTime badEnd   = LocalDateTime.of(day, LocalTime.of(17,0));
        assertThrows(ShiftRuleException.class, () ->
                ch.allocateShift("M1", new Shift("N1", badStart, badEnd))
        );
    }

}
