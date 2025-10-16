package carehome.exception;


//  used when something we look for is not there.
public class NotFoundException extends CareHomeException {
    public NotFoundException(String m){ super(m); }
}