package carehome.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ActionLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public final LocalDateTime timestamp;
    public final String staffId;
    public final String action;

    public ActionLog(String staffId, String action) {
        this.timestamp = LocalDateTime.now();
        this.staffId = staffId;
        this.action = action;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + staffId + ": " + action;
    }
}
