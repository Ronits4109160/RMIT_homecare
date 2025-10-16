package carehome.exception;


// small custom exception for a specific error case.
public class ValidationException extends CareHomeException {
    public ValidationException(String m){ super(m); }
}