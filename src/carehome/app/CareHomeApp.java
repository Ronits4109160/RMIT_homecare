package carehome.app;

import carehome.model.Role;
import carehome.model.Shift;
import carehome.model.Staff;
import carehome.service.CareHome;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Scanner;


public class CareHomeApp {

    private static final String DATA_FILE = "carehome_data.ser";

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        CareHome ch = new CareHome();

        // Bootstrap a manager so manager-only actions can be performed
        Staff manager = new Staff("M1", "Manager", Role.MANAGER);
        ch.addOrUpdateStaff("M1", manager, "manager", "pass");

        // Optional demo data (doctor, nurse, some shifts)
        ch = seed(ch);

        boolean RUN = true;
        while (RUN) {
            printMenu();
            String choice = sc.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> { // Add/Update Staff (Manager only)
                        System.out.print("Actor (Manager ID): ");
                        String actorId = sc.nextLine().trim();

                        System.out.print("Staff ID (e.g., D1/N1/M2): ");
                        String id = sc.nextLine().trim();

                        System.out.print("Name: ");
                        String name = sc.nextLine().trim();

                        System.out.print("Role (MANAGER/DOCTOR/NURSE): ");
                        String roleIn = sc.nextLine().trim().toUpperCase();
                        Role role = Role.valueOf(roleIn);

                        System.out.print("Username: ");
                        String username = sc.nextLine().trim();

                        System.out.print("Password: ");
                        String password = sc.nextLine().trim();

                        Staff s = new Staff(id, name, role);
                        ch.addOrUpdateStaff(actorId, s, username, password);
                        System.out.println("✅ Staff saved: " + s);
                    }

                    case "2" -> { // Allocate Shift (Manager only)
                        System.out.print("Actor (Manager ID): ");
                        String actorId = sc.nextLine().trim();

                        System.out.print("Staff ID to allocate: ");
                        String sid = sc.nextLine().trim();

                        System.out.print("Start (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime start = LocalDateTime.parse(sc.nextLine().trim());

                        System.out.print("End   (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime end = LocalDateTime.parse(sc.nextLine().trim());

                        ch.allocateShift(actorId, new Shift(sid, start, end));
                        System.out.println("✅ Shift allocated");
                    }

                    case "3" -> { // Check Compliance (no args)
                        ch.checkCompliance();
                        System.out.println("✅ Compliance OK");
                    }

                    case "4" -> { // List staff
                        System.out.println("=== Staff ===");
                        ch.getStaffById().values().forEach(System.out::println);
                    }

                    case "5" -> { // List shifts
                        System.out.println("=== Shifts ===");
                        ch.getShifts().forEach(s ->
                                System.out.println(s.getStaffId() + "  " + s.getStart() + " -> " + s.getEnd()));
                    }

                    case "11" -> { // Save single file
                        ch.saveToFile(Path.of(DATA_FILE));
                        System.out.println("💾 Saved to " + DATA_FILE);
                    }

                    case "12" -> { // Load single file
                        ch = CareHome.loadFromFile(Path.of(DATA_FILE));
                        System.out.println("📂 Loaded from " + DATA_FILE);
                    }

                    case "0" -> RUN = false;

                    default -> System.out.println("Invalid.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println(" Input error: " + e.getMessage());
            } catch (DateTimeParseException e) {
                System.out.println(" Bad date/time format. Use YYYY-MM-DDTHH:MM (e.g., 2025-10-06T08:00)");
            } catch (Exception e) {
                // Domain exceptions (Unauthorized, ShiftRule, Compliance, etc.) land here
                System.out.println(" " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        sc.close();
        System.out.println("Bye.");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("== CareHome Console ==");
        System.out.println("1) Add/Update Staff (manager)");
        System.out.println("2) Allocate Shift (manager)");
        System.out.println("3) Check Compliance");
        System.out.println("4) List Staff");
        System.out.println("5) List Shifts");
        System.out.println("11) Save");
        System.out.println("12) Load");
        System.out.println("0) Quit");
        System.out.print("Choose: ");
    }

    /**
     * Seeds demo data using ONLY public APIs of CareHome:
     * - Adds a Doctor and a Nurse (via manager authority)
     * - Allocates canonical nurse shifts (08–16 and 14–22) and a 1h doctor shift today
     */
    private static CareHome seed(CareHome ch) {
        // Staff via public API
        ch.addOrUpdateStaff("M1", new carehome.model.Staff("D1", "Dr Alice", carehome.model.Role.DOCTOR), "alice", "pass");
        ch.addOrUpdateStaff("M1", new carehome.model.Staff("N1", "Nurse Bob",  carehome.model.Role.NURSE),  "bob",   "pass");
        ch.addOrUpdateStaff("M1", new carehome.model.Staff("N2", "Nurse Eva",  carehome.model.Role.NURSE),  "eva",   "pass");

        java.time.LocalDate today = java.time.LocalDate.now();

        // Doctor: 1 hour today (meets doctor coverage)
        ch.allocateShift("M1", new carehome.model.Shift("D1", today.atTime(12, 0), today.atTime(13, 0)));

        // Nurses: coverage for both windows WITHOUT overlap on a single nurse
        ch.allocateShift("M1", new carehome.model.Shift("N1", today.atTime(8, 0),  today.atTime(16, 0)));  // N1
        ch.allocateShift("M1", new carehome.model.Shift("N2", today.atTime(14, 0), today.atTime(22, 0)));  // N2

        return ch;
    }

}
