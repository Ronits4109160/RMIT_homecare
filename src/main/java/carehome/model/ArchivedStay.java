package carehome.model;


// history record after discharge.
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**  patient's complete stay for audit. */
public class ArchivedStay implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String residentId;
    public final String residentName;
    public final Gender gender;
    public final int age;
    public final String lastBedId;
    public final LocalDateTime dischargedAt;

    // Full medical history for this stay
    public final List<Prescription> prescriptions;
    public final List<Administration> administrations;

    public ArchivedStay(String residentId,
                        String residentName,
                        Gender gender,
                        int age,
                        String lastBedId,
                        LocalDateTime dischargedAt,
                        List<Prescription> prescriptions,
                        List<Administration> administrations) {
        this.residentId = residentId;
        this.residentName = residentName;
        this.gender = gender;
        this.age = age;
        this.lastBedId = lastBedId;
        this.dischargedAt = dischargedAt;
        this.prescriptions = prescriptions;
        this.administrations = administrations;
    }

    @Override
    public String toString() {
        return "ArchivedStay{" + residentId + " " + residentName + " @ " + lastBedId + " on " + dischargedAt + "}";
    }
}
