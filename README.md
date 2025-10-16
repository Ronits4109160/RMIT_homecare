# RMIT CareHome App

### Submitted by: Ronit Bhandari (s4109169)  
### Advance Programming Assignment 2

---

##  Overview

The **CareHome Application** is a JavaFX-based desktop program designed to manage an aged-care home environment.  
It allows managers, doctors, and nurses to log in, manage residents, allocate beds, handle prescriptions, record logs, and manage staff shifts.  
The project follows the **MVC (Model–View–Controller)** architecture and uses **Maven** for dependency management and builds.

---
## Technologies Used

| Tool | Purpose |
|------|----------|
| Java 17 | Programming Language |
| JavaFX 21 | GUI Framework |
| Maven | Build & Dependency Management |
| SQLite JDBC | Embedded Database (Optional) |
| SLF4J Simple | Logging |
| JUnit 5 / TestNG | Testing Frameworks |

---


## ️ Requirements

Before running the application, ensure you have the following installed:

- **Java 17 or later**  
- **Apache Maven 3.8+**

Check installations by running:
```bash
java -version
mvn -version
```

---

## How to Run the Application

### **Option 1: Run from Terminal **

1. Navigate to the project folder (where `pom.xml` is located):

   ```bash
   cd Assignment2_AP
   ```

2. Clean and compile the project:

   ```bash
   mvn clean compile
   ```

3. Run the JavaFX application:

   ```bash
   mvn javafx:run
   ```

This will launch the **CareHome** login window.

---

### **Option 2: Run in IntelliJ IDEA**

1. Open IntelliJ -  **File then Open Project** → select the folder containing `pom.xml`  
2. Wait for dependencies to load.  
3. Click on **Reload All Maven Projects** .  
4. Run the class:  
   `carehome.ui.MainApp`  

---

##  Running Tests

You can run all test cases using:

```bash
mvn test
```

To run a specific test class:

```bash
mvn -Dtest=CareHomeShiftsTest test
```

Test results will be stored in:

```
target/surefire-reports/
```

