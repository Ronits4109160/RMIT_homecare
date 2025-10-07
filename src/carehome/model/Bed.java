package carehome.model;

import java.io.Serializable;

public class Bed implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public Resident occupant;

    public Bed(String id) {
        this.id = id;
    }

    public boolean isVacant() {
        return occupant == null;
    }

    @Override
    public String toString() {
        return "Bed " + id + " -> " + (occupant == null ? "VACANT" : occupant.name);
    }
}
