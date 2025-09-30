package carehome.model;


import java.io.Serializable;


public final class Resident implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;
    public String name;
    public Gender gender;
    public int age;

    public Resident(String id, String name, Gender gender, int age){
        this.id = id; this.name = name; this.gender = gender; this.age = age;
    }

    // Backward-compat: old 2-arg ctor defaults to MALE and age 0
    public Resident(String id, String name){
        this(id, name, Gender.MALE, 0);
    }

    @Override public String toString(){
        return id + " " + name + " (" + (gender == Gender.MALE ? "Male" : "Female") + ", " + age + ")";
    }
}