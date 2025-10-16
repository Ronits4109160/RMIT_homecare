package carehome.exception;


// login/permission issue.
public class UnauthorizedException extends CareHomeException {
    public UnauthorizedException(String m){ super(m); }
}