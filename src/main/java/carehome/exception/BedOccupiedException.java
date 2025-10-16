package carehome.exception;


// thrown when a bed is already taken.
public class BedOccupiedException extends CareHomeException {
    public BedOccupiedException(String m){ super(m); }
}