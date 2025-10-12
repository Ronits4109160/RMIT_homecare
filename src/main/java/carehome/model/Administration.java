package carehome.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Administration implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String nurseId;
    public final String prescriptionId;
    public final String medicine;
    public final LocalDateTime administeredAt;
    public final String notes;
    public ActionLog time;

    public Administration(String nurseId, String prescriptionId, String medicine, LocalDateTime administeredAt, String notes) {
        this.nurseId = nurseId;
        this.prescriptionId = prescriptionId;
        this.medicine = medicine;
        this.administeredAt = administeredAt;
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "Administered " + medicine + " by " + nurseId + " @ " + administeredAt;
    }
}
