package carehome.exception;


// used when staff isn't on duty.
public class NotRosteredException extends CareHomeException {
    public NotRosteredException(String m) { super(m); }
}