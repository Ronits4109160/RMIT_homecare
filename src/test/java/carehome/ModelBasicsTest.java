package carehome;

import static org.junit.jupiter.api.Assertions.*;
import carehome.model.*;

import org.junit.jupiter.api.Test;

public class ModelBasicsTest {

    @Test
    void residentToStringLooksReasonable() {
        Resident r = new Resident("R3", "Cara", Gender.FEMALE, 28);
        String s = r.toString();
        assertTrue(s.contains("R3"));
        assertTrue(s.contains("Cara"));
    }

    @Test
    void bedVacancyToggles() {
        Bed b = new Bed("B1");
        assertTrue(b.isVacant());
        b.occupant = new Resident("R9", "Zed", Gender.MALE, 40);
        assertFalse(b.isVacant());
    }
}
