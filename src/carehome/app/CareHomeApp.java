package carehome.app;


import carehome.exception.CareHomeException;
import carehome.model.*;
import carehome.service.CareHome;
import java.time.LocalDate;
import carehome.model.Gender;
import carehome.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import carehome.model.Role;



public final class CareHomeApp {

    private static CareHome seed(){
        CareHome ch = new CareHome();
        ch.addBed("B101"); ch.addBed("B102"); ch.addBed("B103");
        ch.staff().put("D1", new Staff("D1","Dr Alice", Role.DOCTOR, "pass"));
        ch.staff().put("N1", new Staff("N1","Nurse Bob", Role.NURSE, "pass"));
        ch.staff().put("M1", new Staff("M1", "Manager Mia", Role.MANAGER, "pass"));

        ch.rebuildRoleLists();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(1);
        LocalDateTime end = now.plusHours(5);
        ch.staff().get("D1").shifts.add(new Shift(start,end));
        ch.staff().get("N1").shifts.add(new Shift(start,end));
        return ch;
    }
    private static Gender parseGender(String s){
        String x = s.trim().toUpperCase();
        if (x.equals("M") || x.equals("MALE"))   return Gender.MALE;
        if (x.equals("F") || x.equals("FEMALE")) return Gender.FEMALE;
        throw new ValidationException("Gender must be M/F");
    }

    private static int parseAge(String s){
        try {
            int a = Integer.parseInt(s.trim());
            if (a < 0 || a > 130) throw new ValidationException("Age out of range (0–130)");
            return a;
        } catch (NumberFormatException e){
            throw new ValidationException("Age must be a number");
        }
    }

    private static Role parseRole(String s){
        String x = s.trim().toUpperCase();
        switch (x) {
            case "DOCTOR":  return Role.DOCTOR;
            case "NURSE":   return Role.NURSE;
            case "MANAGER": return Role.MANAGER;
            default: throw new ValidationException("Role must be DOCTOR, NURSE or MANAGER");
        }
    }

//    private static String resolveActorId(CareHome ch, String input, LocalDateTime atTime){
//        String v = input.trim();
//        if (v.equalsIgnoreCase("manager")) return ch.pickManager();
//        if (v.equalsIgnoreCase("doctor"))  return ch.pickRostered(Role.DOCTOR, atTime);
//        if (v.equalsIgnoreCase("nurse"))   return ch.pickRostered(Role.NURSE,  atTime);
//        return v; // treat as explicit ID (e.g., D1, N2, M1)
//    }


    private static void printMenu(){
        System.out.println("\n== CareHome Demo ==");
        System.out.println("1) Add/Update Staff");
        System.out.println("2) Allocate/Modify Shift");
        System.out.println("3) Add Resident to Vacant Bed");
        System.out.println("4) Check Resident in Bed (medical staff)");
        System.out.println("5) Doctor: Attach Prescription to Bed");
        System.out.println("6) Nurse: Move Resident");
        System.out.println("7) Administer Prescription (Nurse)");
        System.out.println("8) Show Logs");
        System.out.println("9) Quit");
        System.out.println("10) Check Compliance (enter week start)");
        System.out.println("11) Save State");
        System.out.println("12) Load State");
        System.out.print("Choose: ");
    }

    public static void main(String[] args){
        CareHome ch = seed();
        Scanner sc = new Scanner(System.in);
        boolean run = true;
        while(run){
            try{
                printMenu();
                String choice = sc.nextLine().trim();
                switch (choice) {
                    case "1": {
                        System.out.print("Actor (must be rostered): "); String actor = sc.nextLine().trim();
                        System.out.print("Staff ID: "); String id=sc.nextLine();
                        System.out.print("Name: "); String name=sc.nextLine();
                        System.out.print("Role (DOCTOR/NURSE): "); Role r = parseRole(sc.nextLine());
                        System.out.print("Password: "); String pw=sc.nextLine();
                        ch.addOrUpdateStaff(actor,id,name,r,pw);
                        System.out.println("OK");
                        break;
                    }
                    case "2": {
                        System.out.print("Actor: "); String actor=sc.nextLine();
                        System.out.print("Staff ID to roster: "); String sid=sc.nextLine();
                        System.out.print("Start (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime s=LocalDateTime.parse(sc.nextLine()+":00");
                        System.out.print("End (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime e=LocalDateTime.parse(sc.nextLine()+":00");
                        ch.allocateOrModifyShift(actor,sid,s,e);
                        System.out.println("OK");
                        break;
                    }
                    case "3": {
                        System.out.print("Actor: "); String actor=sc.nextLine();
                        System.out.print("Resident ID: "); String rid=sc.nextLine();
                        System.out.print("Resident Name: "); String rn=sc.nextLine();
                        System.out.print("Gender (M/F): "); Gender g = parseGender(sc.nextLine());
                        System.out.print("Age (years): "); int age = parseAge(sc.nextLine());
                        System.out.print("Bed ID: "); String bid=sc.nextLine();

                        ch.addResidentToVacantBed(actor, rid, rn, g, age, bid);
                        System.out.println("OK");
                        break;
                    }
                    case "4": {
                        System.out.print("Actor (doctor/nurse): "); String actor=sc.nextLine();
                        System.out.print("Bed ID: "); String bid=sc.nextLine();
                        Resident r = ch.checkResidentInBed(actor,bid);
                        System.out.println("Resident: "+r);
                        break;
                    }
                    case "5": {
                        System.out.print("Doctor ID: "); String did=sc.nextLine();
                        System.out.print("Bed ID: "); String bid=sc.nextLine();
                        List<MedicationDose> doses = new ArrayList<>();
                        while(true){
                            System.out.print("Add dose? (y/n): "); String yn=sc.nextLine();
                            if(!yn.equalsIgnoreCase("y")) break;
                            System.out.print("Medicine: "); String med=sc.nextLine();
                            System.out.print("Dose like 500mg: "); String dose=sc.nextLine();
                            System.out.print("Time like 09:00: ");
                            LocalTime t=LocalTime.parse(sc.nextLine()+":00");
                            doses.add(new MedicationDose(med,dose,t));
                        }
                        Prescription p = ch.doctorAttachPrescription(did,bid,doses);
                        System.out.println("Created: "+p);
                        break;
                    }
                    case "6": {
                        System.out.print("Nurse ID: "); String nid=sc.nextLine();
                        System.out.print("From Bed: "); String fb=sc.nextLine();
                        System.out.print("To Bed: "); String tb=sc.nextLine();
                        ch.nurseMoveResident(nid,fb,tb);
                        System.out.println("OK");
                        break;
                    }
                    case "7": {
                        System.out.print("Nurse ID: "); String nid=sc.nextLine();
                        System.out.print("Prescription ID: "); String pid=sc.nextLine();
                        System.out.print("Medicine: "); String med=sc.nextLine();
                        System.out.print("Dose: "); String dose=sc.nextLine();
                        System.out.print("Time (YYYY-MM-DDTHH:MM): ");
                        LocalDateTime t=LocalDateTime.parse(sc.nextLine()+":00");
                        Administration admin = ch.administerPrescription(nid,pid,med,dose,t);
                        System.out.println("Recorded: "+admin);
                        System.out.print("Add/Update notes (blank to skip): "); String notes=sc.nextLine();
                        if(!notes.isBlank()){ ch.updateAdministrationNotes(nid, admin, notes); System.out.println("Notes updated."); }
                        break;
                    }
                    case "8": {
                        ch.getLogs().forEach(System.out::println);
                        break;
                    }
                    case "9": run=false; break;
                    case "10": {
                        System.out.print("Week start (YYYY-MM-DD, Monday): ");
                        LocalDate ws = LocalDate.parse(sc.nextLine().trim());
                        ch.checkCompliance(ws);
                        System.out.println("Compliance OK for week starting "+ws);
                        break;
                    }
                    case "11": {
                        System.out.print("File save  ");
//                        String path = sc.nextLine().trim();
                        ch.saveToFile("Data.txt");
                        System.out.println("Saved");
                        break;
                    }
                    case "12": {
                        ch = CareHome.loadFromFile("Data.txt");
                        System.out.println("Data Loaded ");
                        break;
                    }
                    default: System.out.println("Invalid.");
                }
            } catch (CareHomeException ex){
                System.out.println("ERROR: "+ex.getMessage());
            } catch (Exception ex){
                System.out.println("UNEXPECTED: "+ex);
            }
        }
        System.out.println("Bye.");
    }
}