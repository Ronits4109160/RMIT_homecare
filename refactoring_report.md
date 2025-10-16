# Refactoring Report – CareHome Application

**Submitted by:** Ronit Bhandari (s4109169)  
**Course:**  Advanced Programming

## Project Overview

The **CareHome Application** is a JavaFX desktop program that simulates an aged-care home management system.  
It enables managers, doctors, and nurses to log in and perform tasks such as managing staff, allocating beds, handling medications, recording logs, and scheduling shifts.  
The app follows the **MVC (Model–View–Controller)** pattern and uses **Maven** for build and dependency management.  
Business rules are centralised in the **service layer (CareHome class)**, while JavaFX controllers handle UI behaviour and delegate logic to services.

---

##  Program Flow

1. **Launch:** `MainApp` seeds demo users, loads `LoginView.fxml`, and shows the login screen.
2. **Login:** `LoginController` verifies credentials through `CareHome` and navigates to the main dashboard (`MainView.fxml`).
3. **Navigation:** `MainController` switches between views (Staff, Beds, Shifts, Meds, Logs, Archives).
4. **Actions:** Each controller invokes methods from `CareHome` to perform operations. Success or errors are shown as alerts or logged in the console.
5. **Persistence:** SQLite JDBC and service layer manage data creation and validation.


---

## Default Login Credentials

| Role | ID | Username | Password |
|------|----|-----------|-----------|
| Manager | M1 | manager | pass |
| Doctor  | D1 | alice   | pass |
| Nurse   | N1 | bob     | pass |

---

##  Project Structure

```
Assignment2_AP/
│
├── pom.xml                  # Maven build configuration
├── README.md                # How to run the project
├── refactoring_report.pdf   # Design and structure explanation
│
└── src/
    └── main/
        ├── java/
        │   └── carehome/
        │       ├── model/          # Entity classes (Staff, Resident, Bed, Shift, etc.)
        │       ├── service/        # Business logic and rule enforcement
        │       ├── exception/      # Custom exception handling
        │       ├── ui/             # JavaFX entry point (MainApp.java)
        │       └── ui/controller/  # UI controllers for different FXML screens
        │
        └── resources/
            └── carehome/ui/        # FXML  files
                ├── LoginView.fxml
                ├── MainView.fxml
                ├── BedsView.fxml
                ├── StaffView.fxml
                ├── ShiftView.fxml
                ├── ArchivesView.fxml
                ├── MedsView.fxml
                └── LogsView.fxml
```

### **Manager Functions**
- Can **create, update, or remove staff** (doctors and nurses).
- Can **reset or change passwords** of any staff member.
- Can **allocate shifts** to doctors and nurses.
- Can **assign residents to specific beds**.
- Can **perform compliance checks** to ensure rules (e.g., room gender, occupancy limits) are satisfied.
- Can **save and load system data** using the integrated SQLite database — preserving state between sessions.

### **Doctor Functions**
- Can **prescribe medication** for residents assigned to beds.
- Can **discharge residents** once treatment is complete.
- Can view bed allocations and current resident medical status.

### **Nurse Functions**
- Can **move residents** between beds and across rooms.
- Can **discharge residents** upon doctor’s approval.
- Can **administer prescribed medication** and update treatment logs.
- Can access shift schedules and log completion reports.

### **UI Design**
- Each major function has a dedicated FXML view (e.g., BedsView, StaffView, ShiftView).
- `MainView.fxml` acts as a container allowing users to navigate between sections dynamically via the `MainController`.
- Data consistency is maintained by calling centralised methods in `CareHome.java`.


## 3. MVC Design Pattern

| Component | Folder | Description |
|------------|---------|-------------|
| **Model** | `carehome.model` | Domain objects: Staff, Resident, Bed, Shift, etc. |
| **View** | `src/main/resources/carehome/ui` | FXML files that define each screen's layout. |
| **Controller** | `carehome.ui.controller` | Handles UI events and delegates logic to services. |
| **Service Layer** | `carehome.service` | Implements business rules and application workflow (CareHome). |

---



