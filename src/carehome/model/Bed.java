package carehome.model;


import java.io.Serializable;

public final class Bed implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public Resident occupant; // null if vacant
    public Bed(String id){ this.id=id; }
    public boolean isVacant(){ return occupant==null; }
    @Override public String toString(){ return id+"["+(isVacant()?"vacant":occupant)+"]"; }
}