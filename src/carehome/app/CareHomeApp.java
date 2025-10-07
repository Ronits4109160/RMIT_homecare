package carehome.app;

import carehome.model.*;
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

        // Bootstrap manager (needed for first setup)
        Staff manager = new Staff("M1", "Manager", Role.MANAGER);
        ch.addOrUpdateStaff("M1", manager, "manager", "pass");


        ch = seed(ch);

        boolean RUN = true;
        while (RUN) {
            printMenu();
            String choice = sc.nextLine().trim();

            try {
                switch (choice) {


                    // 1) Add / Update Staff (Manager)

                    case "1" -> {
                        System.out.print("Actor (Manager ID): ");
                        String actorId = sc.nextLine().trim();

                        System.out.print("Staff ID (e.g., D1/N1/M2): ");
                        String id = sc.nextLine().trim();

                        System.out.print("Name: ");
                        String name = sc.nextLine().trim();

                        System.out.print("Role (MANAGER/DOCTOR/NURSE): ");
                        Role role = Role.valueOf(sc.nextLine().trim().toUpperCase());

                        System.out.print("Username: ");
                        String username = sc.nextLine().trim();

                        System.out.print("Password: ");
                        String password = sc.nextLine().trim();

                        Staff s = new Staff(id, name, role);
                        ch.addOrUpdateStaff(actorId, s, username, password);
                        System.out.println("✅ Staff saved: " + s);
                    }


                    // 2) Allocate Shift (Manager)

                    case "2" -> {
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


                    // 3) Add Resident to Vacant Bed (Manager)

                    case "3" -> {
                        System.out.print("Manager ID: ");
                        String mid = sc.nextLine().trim();

                        System.out.print("Resident ID: ");
                        String rid = sc.nextLine().trim();
                        System.out.print("Name: ");
                        String name = sc.nextLine().trim();
                        System.out.print("Gender (M/F): ");
                        String g = sc.nextLine().trim().toUpperCase();
                        Gender gender = Gender.valueOf(g);
                        System.out.print("Age: ");
                        int age = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Assign Bed ID: ");
                        String bedId = sc.nextLine().trim();

                        Resident r = new Resident(rid, name, gender, age);
                        ch.addResidentToBed(mid, bedId, r);
                        System.out.println("✅ Resident " + name + " added to bed " + bedId);
                    }


                    // 4) Check Resident in Bed

                    case "4" -> {
                        System.out.print("Actor ID (Doctor/Nurse): ");
                        String actorId = sc.nextLine().trim();
                        System.out.print("Bed ID: ");
                        String bedId = sc.nextLine().trim();

                        Resident r = ch.getResidentInBed(actorId, bedId);
                        System.out.println(" Resident in Bed " + bedId + ": " + r);
                    }


                    // 5) Doctor: Attach Prescription
                    case "5" -> {
                        System.out.print("Doctor ID: ");
                        String did = sc.nextLine().trim();
                        System.out.print("Bed ID: ");
                        String bedId = sc.nextLine().trim();

                        // Get resident in that bed so we can set residentId on the prescription
                        Resident r = ch.getResidentInBed(did, bedId);

                        System.out.print("Prescription ID: ");
                        String pid = sc.nextLine().trim();

                        // One medication for now
                        System.out.print("Medicine name: ");
                        String med = sc.nextLine().trim();
                        System.out.print("Dosage (e.g., 1 tablet): ");
                        String dose = sc.nextLine().trim();
                        System.out.print("Frequency (e.g., daily): ");
                        String freq = sc.nextLine().trim();

                        MedicationDose md = new MedicationDose(med, dose, freq);
                        java.util.List<MedicationDose> meds = java.util.List.of(md);


                        Prescription p = new Prescription(
                                pid,
                                did,
                                r.id,
                                java.time.LocalDateTime.now(),
                                meds
                        );

                        ch.addPrescription(did, bedId, p, java.time.LocalDateTime.now());
                        System.out.println("✅ Prescription added for bed " + bedId);
                    }



                    // 6) Nurse: Move Resident

                    case "6" -> {
                        System.out.print("Nurse ID: ");
                        String nid = sc.nextLine().trim();
                        System.out.print("From Bed ID: ");
                        String from = sc.nextLine().trim();
                        System.out.print("To Bed ID: ");
                        String to = sc.nextLine().trim();

                        ch.moveResident(nid, from, to, LocalDateTime.now());
                        System.out.println("✅ Resident moved from " + from + " to " + to);
                    }


                    // 7) Nurse: Administer Prescription

                    case "7" -> {
                        System.out.print("Nurse ID: ");
                        String nid = sc.nextLine().trim();
                        System.out.print("Bed ID: ");
                        String bedId = sc.nextLine().trim();
                        System.out.print("Prescription ID to administer: ");
                        String prescId = sc.nextLine().trim();

                        System.out.print("Medicine name: ");
                        String med = sc.nextLine().trim();
                        System.out.print("Dose given: ");
                        String dose = sc.nextLine().trim();


                        String notes = "dose=" + dose;

                        Administration admin = new Administration(
                                nid,
                                prescId,
                                med,
                                java.time.LocalDateTime.now(),
                                notes
                        );

                        ch.administerMedication(nid, bedId, admin, java.time.LocalDateTime.now());
                        System.out.println("✅ Administered " + med + " to " + bedId);
                    }



                    // 8) Show Logs

                    case "8" -> {
                        System.out.println("=== ACTION LOGS ===");
                        ch.getLogs().forEach(System.out::println);
                    }


                    // 9) Check Compliance

                    case "9" -> {
                        ch.checkCompliance();
                        System.out.println("✅ Compliance OK");
                    }


                    // 10) List Staff

                    case "10" -> {
                        System.out.println("=== STAFF ===");
                        ch.getStaffById().values().forEach(System.out::println);
                    }


                    // 11) List Shifts

                    case "11" -> {
                        System.out.println("=== SHIFTS ===");
                        ch.getShifts().forEach(s ->
                                System.out.println(s.getStaffId() + "  " + s.getStart() + " -> " + s.getEnd()));
                    }


                    // 12) Save

                    case "12" -> {
                        ch.saveToFile(Path.of(DATA_FILE));
                        System.out.println("💾 Saved to " + DATA_FILE);
                    }


                    // 13) Load

                    case "13" -> {
                        ch = CareHome.loadFromFile(Path.of(DATA_FILE));
                        System.out.println("📂 Loaded from " + DATA_FILE);
                    }


                    // 0) Quit

                    case "0" -> RUN = false;

                    default -> System.out.println(" Invalid choice.");
                }

            } catch (IllegalArgumentException e) {
                System.out.println(" Input error: " + e.getMessage());
            } catch (DateTimeParseException e) {
                System.out.println(" Bad date/time format. Use YYYY-MM-DDTHH:MM (e.g., 2025-10-06T08:00)");
            } catch (Exception e) {
                System.out.println(" " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        sc.close();
        System.out.println(" Bye!");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("== CareHome Console ==");
        System.out.println("1) Add/Update Staff (Manager)");
        System.out.println("2) Allocate Shift (Manager)");
        System.out.println("3) Add Resident to Vacant Bed (Manager)");
        System.out.println("4) Check Resident in Bed (Doctor/Nurse)");
        System.out.println("5) Doctor: Attach Prescription");
        System.out.println("6) Nurse: Move Resident");
        System.out.println("7) Nurse: Administer Prescription");
        System.out.println("8) Show Logs");
        System.out.println("9) Check Compliance");
        System.out.println("10) List Staff");
        System.out.println("11) List Shifts");
        System.out.println("12) Save");
        System.out.println("13) Load");
        System.out.println("0) Quit");
        System.out.print("Choose: ");
    }

    /** Demo data for quick testing **/
    private static CareHome seed(CareHome ch) {
        ch.addOrUpdateStaff("M1", new Staff("D1", "Dr Alice", Role.DOCTOR), "alice", "pass");
        ch.addOrUpdateStaff("M1", new Staff("N1", "Nurse Bob", Role.NURSE), "bob", "pass");
        ch.addOrUpdateStaff("M1", new Staff("N2", "Nurse Eva", Role.NURSE), "eva", "pass");

        LocalDate today = LocalDate.now();
        ch.allocateShift("M1", new Shift("D1", today.atTime(12, 0), today.atTime(13, 0)));
        ch.allocateShift("M1", new Shift("N1", today.atTime(8, 0), today.atTime(16, 0)));
        ch.allocateShift("M1", new Shift("N2", today.atTime(14, 0), today.atTime(22, 0)));

        return ch;
    }
}
