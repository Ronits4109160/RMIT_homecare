package carehome.model;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class Prescription implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public final String residentId;
    public final String doctorId;
    public final List<MedicationDose> schedule = new ArrayList<>();
    public final List<Administration> administrations = new ArrayList<>();

    public Prescription(String id, String residentId, String doctorId){
        this.id=id; this.residentId=residentId; this.doctorId=doctorId;
    }
    @Override public String toString(){ return id+" for "+residentId+" by "+doctorId+" doses="+schedule; }
}