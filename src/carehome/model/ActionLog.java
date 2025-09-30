package carehome.model;



import java.io.Serializable;
import java.time.LocalDateTime;

public final class ActionLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public final LocalDateTime time;
    public final String staffId;
    public final String action;

    public ActionLog(LocalDateTime time, String staffId, String action){
        this.time=time; this.staffId=staffId; this.action=action;
    }
    @Override public String toString(){ return time+" ["+staffId+"] "+action; }
}