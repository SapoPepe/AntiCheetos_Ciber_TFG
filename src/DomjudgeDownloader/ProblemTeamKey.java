package DomjudgeDownloader;

public class ProblemTeamKey {
    private final String problemId;
    private final String teamId;

    public ProblemTeamKey(String problemId, String teamId) {
        this.problemId = problemId;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProblemTeamKey that = (ProblemTeamKey) o;
        return problemId.equals(that.problemId) && teamId.equals(that.teamId);
    }

    @Override
    public int hashCode() {
        return 31 * problemId.hashCode() + teamId.hashCode();
    }

    public String getProblemId() {
        return problemId;
    }

    public String getTeamId() {
        return teamId;
    }
}