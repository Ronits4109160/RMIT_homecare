// src/test/java/carehome/CareHomeAuthTest.java
package carehome;

import carehome.exception.UnauthorizedException;
import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeAuthTest {

    @Test
    void authenticateSuccessAndFailure() {
        CareHome ch = new CareHome();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "boss", "secret");
        assertNotNull(ch.authenticate("M1", "secret"));
        assertThrows(UnauthorizedException.class, () -> ch.authenticate("M1", "wrong"));
        assertThrows(UnauthorizedException.class, () -> ch.authenticate("X9", "secret"));
    }

    @Test
    void onlyManagerCanAllocateShifts() {
        CareHome ch = new CareHome();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "boss", "secret");
        ch.addOrUpdateStaff("M1",     new Staff("N1", "Nina",   Role.NURSE),   "n", "p");

        var day = java.time.LocalDate.now();
        var s = java.time.LocalDateTime.of(day, java.time.LocalTime.of(14,0));
        var e = s.plusHours(8);
        ch.allocateShift("M1", new Shift("N1", s, e)); // OK: manager assigns

        // Nurse should not be able to allocate shifts
        assertThrows(RuntimeException.class, () ->
                ch.allocateShift("N1", new Shift("N1", s, e))
        );
    }
}
