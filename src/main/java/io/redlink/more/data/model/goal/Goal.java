package io.redlink.more.data.model.goal;

import java.time.Instant;

public class Goal {
    private Long studyId;
    private Integer goalId;
    private Integer participantId;
    private Integer templateId;
    private Object properties;
    private Instant created;
    private Instant modified;

    // Getters & Setters (fluent)
    public Long getStudyId() { return studyId; }
    public Goal setStudyId(Long studyId) { this.studyId = studyId; return this; }

    public Integer getGoalId() { return goalId; }
    public Goal setGoalId(Integer goalId) { this.goalId = goalId; return this; }

    public Integer getParticipantId() { return participantId; }
    public Goal setParticipantId(Integer participantId) { this.participantId = participantId; return this; }

    public Integer getTemplateId() { return templateId; }
    public Goal setTemplateId(Integer templateId) { this.templateId = templateId; return this; }

    public Object getProperties() { return properties; }
    public Goal setProperties(Object properties) { this.properties = properties; return this; }

    public Instant getCreated() { return created; }
    public Goal setCreated(Instant created) { this.created = created; return this; }

    public Instant getModified() { return modified; }
    public Goal setModified(Instant modified) { this.modified = modified; return this; }
}