package carehome.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class Administration implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String residentId;
    public final String prescriptionId;
    public final String medicine;
    public final String dose;
    public final LocalDateTime time;
    public final String staffId;
    public String notes = "";

    public Administration(String residentId, String prescriptionId, String medicine, String dose,
                          LocalDateTime time, String staffId){
        this.residentId=residentId; this.prescriptionId=prescriptionId;
        this.medicine=medicine; this.dose=dose; this.time=time; this.staffId=staffId;
    }
    @Override public String toString(){
        return "Admin[res="+residentId+", pres="+prescriptionId+", "+medicine+" "+dose+
                ", "+time+", by="+staffId+", notes="+notes+"]";
    }
}