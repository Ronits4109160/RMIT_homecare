package carehome.model;

import java.io.Serializable;
import java.time.LocalTime;

public final class MedicationDose implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String medicine;
    public final String dose;
    public final LocalTime time;

    public MedicationDose(String medicine, String dose, LocalTime time){
        this.medicine=medicine; this.dose=dose; this.time=time;
    }
    @Override public String toString(){ return medicine+" "+dose+" @"+time; }
}