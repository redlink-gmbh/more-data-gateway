package io.redlink.more.data.model.goal;

import java.util.ArrayList;
import java.util.List;

public class StudyGoalConfig {
    private Long studyId;
    private String commitment;
    private String achievability;
    private String understandability;
    private List<AdherenceCheck> adherenceChecks = new ArrayList<>();
    private List<GoalTopic> goalTopics = new ArrayList<>();

    // Getters & Setters...
    public Long getStudyId() { return studyId; }
    public StudyGoalConfig setStudyId(Long studyId) { this.studyId = studyId; return this; }

    public String getCommitment() { return commitment; }
    public StudyGoalConfig setCommitment(String commitment) { this.commitment = commitment; return this; }

    public String getAchievability() { return achievability; }
    public StudyGoalConfig setAchievability(String achievability) { this.achievability = achievability; return this; }

    public String getUnderstandability() { return understandability; }
    public StudyGoalConfig setUnderstandability(String understandability) { this.understandability = understandability; return this; }

    public StudyGoalConfig setAdherenceChecks(List<AdherenceCheck> adherenceChecks) {
        this.adherenceChecks = adherenceChecks ==  null ? new ArrayList<>() : adherenceChecks;
        return this;
    }

    public List<AdherenceCheck> getAdherenceChecks() {
        return adherenceChecks;
    }

    public void setGoalTopics(List<GoalTopic> goalTopics) {
        this.goalTopics = goalTopics;
    }

    public List<GoalTopic> getGoalTopics() {
        return goalTopics;
    }
}