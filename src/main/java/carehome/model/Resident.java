package carehome.model;


//  represents a resident in the home.
import java.io.Serializable;

public final class Resident implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String name;
    public Gender gender;
    public int age;

    public Resident(String id, String name, Gender gender, int age) {
        this.id = id;
        this.name = name;
        this.gender = gender;
        this.age = age;
    }

    @Override
    public String toString() {
        return id + " - " + name + " (" + gender + ", " + age + ")";
    }
}
