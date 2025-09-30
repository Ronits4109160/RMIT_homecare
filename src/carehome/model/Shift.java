package carehome.model;


import carehome.exception.ValidationException;

import java.io.Serializable;
import java.time.*;

public final class Shift implements Serializable {
    private static final long serialVersionUID = 1L;

    public final LocalDateTime start;
    public final LocalDateTime end;

    public Shift(LocalDateTime start, LocalDateTime end){
        if(end.isBefore(start)) throw new ValidationException("Shift end before start");
        this.start=start; this.end=end;
    }
    public boolean covers(LocalDateTime t){ return !t.isBefore(start) && !t.isAfter(end); }
    public long hours(){ return Duration.between(start,end).toHours(); }
    public LocalDate weekStart(){ return start.toLocalDate().with(DayOfWeek.MONDAY); }
}