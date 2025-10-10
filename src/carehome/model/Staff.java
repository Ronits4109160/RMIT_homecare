package carehome.model;

import java.io.Serializable;
import java.util.Objects;


public class Staff implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;       // e.g. M1, D1, N3
    private String name;
    private Role role;

    // Credentials (set by manager)
    private String username;
    private String password; // plain for assignment scope

    public Staff(String id, String name, Role role) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.role = Objects.requireNonNull(role);
    }

    // Encapsulation
    public String getId() { return id; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public void setRole(Role role) { this.role = Objects.requireNonNull(role); }

    // Manager-only operation
    public void setCredentials(String username, String password) {
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }


    public boolean checkPassword(String rawPassword) {
        return Objects.equals(this.password, rawPassword);
    }


//      Returns true if this staff member has manager privileges.

    public boolean isManager() {
        return this.role == Role.MANAGER;
    }

    @Override
    public String toString() {
        return role + "(" + id + ") " + name;
    }
}
