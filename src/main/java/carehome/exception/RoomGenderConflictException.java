package carehome.exception;


// room has mixed genders, not allowed.
public class RoomGenderConflictException extends RuntimeException {
    public RoomGenderConflictException(String message) {
        super(message);
    }
}
