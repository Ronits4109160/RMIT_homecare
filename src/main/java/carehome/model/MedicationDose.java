package carehome.model;


// one medication entry
import java.io.Serializable;

public class MedicationDose implements Serializable {
    private static final long serialVersionUID = 1L;

    public String medicine;
    public String dosage;
    public String frequency;

    public MedicationDose(String medicine, String dosage, String frequency) {
        this.medicine = medicine;
        this.dosage = dosage;
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return medicine + " (" + dosage + ", " + frequency + ")";
    }
}
