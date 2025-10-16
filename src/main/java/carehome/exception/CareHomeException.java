package carehome.exception;


// base error for the app.
public class CareHomeException extends RuntimeException {
    public CareHomeException(String m){ super(m); }
}