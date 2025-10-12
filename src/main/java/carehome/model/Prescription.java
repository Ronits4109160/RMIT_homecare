package carehome.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class Prescription implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public final String doctorId;
    public final String residentId;
    public final LocalDateTime dateTime;
    public final List<MedicationDose> meds;
    public ActionLog timeCreated;

    public Prescription(String id, String doctorId, String residentId, LocalDateTime dateTime, List<MedicationDose> meds) {
        this.id = id;
        this.doctorId = doctorId;
        this.residentId = residentId;
        this.dateTime = dateTime;
        this.meds = meds;
    }

    @Override
    public String toString() {
        return "Prescription " + id + " by " + doctorId + " for " + residentId + " (" + meds.size() + " meds)";
    }
}
