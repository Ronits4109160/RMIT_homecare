package carehome.model;


// small event log entry.
import java.io.Serializable;
import java.time.LocalDateTime;

public class ActionLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String staffId;
    public final String action;
    public final LocalDateTime time;

    // existing path (used by CareHome.log)
    public ActionLog(String staffId, String action) {
        this(staffId, action, LocalDateTime.now());
    }

    // lets JDBC restore the exact timestamp
    public ActionLog(String staffId, String action, LocalDateTime time) {
        this.staffId = staffId;
        this.action = action;
        this.time = time;
    }

    public String getStaffId() { return staffId; }
    public String getAction()  { return action; }
    public LocalDateTime getTime() { return time; }

    @Override public String toString() {
        return "[" + time + "] " + staffId + ": " + action;
    }
}
