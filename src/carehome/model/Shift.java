package carehome.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class Shift implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String staffId;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public Shift(String staffId, LocalDateTime start, LocalDateTime end) {
        this.staffId = Objects.requireNonNull(staffId);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        if (!end.isAfter(start)) throw new IllegalArgumentException("Shift end must be after start");
    }

    public String getStaffId() { return staffId; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }

    public long hours() { return java.time.Duration.between(start, end).toHours(); }

    public boolean overlaps(Shift other) {
        return start.isBefore(other.end) && other.start.isBefore(end) && staffId.equals(other.staffId);
    }
}
