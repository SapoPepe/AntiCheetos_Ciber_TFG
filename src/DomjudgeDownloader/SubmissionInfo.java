package DomjudgeDownloader;
import java.time.ZonedDateTime;

public class SubmissionInfo {
    private final ZonedDateTime time;
    private final String submissionId;

    public SubmissionInfo(ZonedDateTime time, String submissionId) {
        this.time = time;
        this.submissionId = submissionId;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public String getSubmissionId() {
        return submissionId;
    }
}