package carehome;

import carehome.exception.ValidationException;
import carehome.model.*;
import carehome.service.CareHome;

import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

public class CareHomeTest {

    @Test
    void residentIdMustBeUniqueAmongActive() {
        CareHome ch = new CareHome();

        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "manager", "pass");
        ch.addOrUpdateStaff("M1",     new Staff("N1", "Nurse",   Role.NURSE),   "nurse",   "pass");

        //  obey shift rules
        LocalDate today = LocalDate.now();
        LocalDateTime s1 = LocalDateTime.of(today, LocalTime.of(8, 0));
        LocalDateTime e1 = LocalDateTime.of(today, LocalTime.of(16, 0));
        ch.allocateShift("M1", new Shift("M1", s1, e1)); // manager shift

        LocalDateTime s2 = LocalDateTime.of(today, LocalTime.of(14, 0));
        LocalDateTime e2 = LocalDateTime.of(today, LocalTime.of(22, 0));
        ch.allocateShift("M1", new Shift("N1", s2, e2)); // nurse shift (valid window)

        ch.seedDefaultLayout();
        String bed1 = ch.getBeds().keySet().iterator().next();
        ch.addResidentToBed("M1", bed1, new Resident("R123", "Alice", Gender.FEMALE, 72));

        String bed2 = ch.getBeds().keySet().stream().skip(1).findFirst().orElse(bed1);
        assertThrows(
                ValidationException.class,
                () -> ch.addResidentToBed("M1", bed2, new Resident("R123", "Bob", Gender.MALE, 70))
        );
    }
}
