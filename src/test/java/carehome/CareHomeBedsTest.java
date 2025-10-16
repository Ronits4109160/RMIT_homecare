// src/test/java/carehome/CareHomeBedsTest.java
package carehome;

import carehome.exception.BedOccupiedException;
import carehome.exception.RoomGenderConflictException;
import carehome.model.*;
import carehome.service.CareHome;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

public class CareHomeBedsTest {

    private void managerShiftNow(CareHome ch) {
        LocalDateTime now = LocalDateTime.now();
        ch.allocateShift("M1", new Shift("M1", now.minusMinutes(5), now.plusHours(2)));
    }

    @Test
    void addingToOccupiedBedThrows() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");
        managerShiftNow(ch);

        String bed = ch.getBeds().keySet().iterator().next();
        System.out.println(bed);
        ch.addResidentToBed("M1", bed, new Resident("R1", "Ann", Gender.FEMALE, 40));
        assertThrows(BedOccupiedException.class, () ->
                ch.addResidentToBed("M1", bed, new Resident("R2", "Bea", Gender.FEMALE, 30))
        );
    }

    @Test
    void roomGenderConflictDetected() {
        CareHome ch = new CareHome();
        ch.seedDefaultLayout();
        ch.addOrUpdateStaff("SYSTEM", new Staff("M1", "Manager", Role.MANAGER), "m","p");
        managerShiftNow(ch);

        // Put FEMALE in first bed
        String bed1 = ch.getBeds().keySet().iterator().next();
        ch.addResidentToBed("M1", bed1, new Resident("RX1", "Ella", Gender.FEMALE, 50));

        // Find another bed in the same room
        String roomPrefix = bed1.substring(0, bed1.lastIndexOf('-'));
        String bed2 = ch.getBeds().keySet().stream()
                .filter(b -> b.startsWith(roomPrefix) && !b.equals(bed1))
                .findFirst().orElse(bed1);

        if (!bed2.equals(bed1)) {
            // Add MALE into same room -> should throw
            assertThrows(RoomGenderConflictException.class, () ->
                    ch.addResidentToBed("M1", bed2, new Resident("RX2", "Max", Gender.MALE, 52))
            );
        } else {
            // If layout has 1 bed per room, ensure placing male elsewhere succeeds
            String otherBed = ch.getBeds().keySet().stream()
                    .filter(b -> !b.startsWith(roomPrefix))
                    .findFirst().orElse(bed1);
            ch.addResidentToBed("M1", otherBed, new Resident("RX2", "Max", Gender.MALE, 52));
        }
    }
}
