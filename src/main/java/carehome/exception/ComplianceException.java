package carehome.exception;


// raised if compliance checks fail.
public class ComplianceException extends CareHomeException {
    public ComplianceException(String message) { super(message); }
}