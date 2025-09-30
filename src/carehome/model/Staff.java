package carehome.model;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class Staff implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public String name;
    public Role role;
    public String password;
    public final List<Shift> shifts = new ArrayList<>();

    public Staff(String id, String name, Role role, String password) {
        this.id = id; this.name = name; this.role = role; this.password = password;
    }
    @Override public String toString(){ return id + "(" + role + ") " + name; }
}