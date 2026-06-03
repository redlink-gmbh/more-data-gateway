package io.redlink.more.data.model.goal;

import io.redlink.more.data.exception.ConflictException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Goal {

    public static final String GOAL_ID_PREFIX = "goal-";
    private static final Pattern PATTERN_GOAL_ID = Pattern.compile("^" + GOAL_ID_PREFIX + "(\\d+)$");

    private Long studyId;
    private Integer goalId;
    private Integer participantId;
    private Integer templateId;
    private String title;
    private Set<Integer> adherenceCheckIds;
    private Object properties;
    private Instant created;
    private Instant modified;

    // Getters & Setters (fluent)
    public Long getStudyId() { return studyId; }
    public Goal setStudyId(Long studyId) { this.studyId = studyId; return this; }

    public Integer getGoalId() { return goalId; }
    public Goal setGoalId(Integer goalId) { this.goalId = goalId; return this; }

    public String getExternalGoalId(){
        return GOAL_ID_PREFIX + goalId;
    }

    public Integer getParticipantId() { return participantId; }
    public Goal setParticipantId(Integer participantId) { this.participantId = participantId; return this; }

    public Integer getTemplateId() { return templateId; }
    public Goal setTemplateId(Integer templateId) { this.templateId = templateId; return this; }

    public String getExternalTemplateId(){
        return GoalTemplate.GAOL_TEMPLATE_ID_PREFIX + templateId;
    }
    public void setExternalTemplateId(String externalTemplateId) {

    }

    public String getTitle() { return title; }
    public Goal setTitle(String title) { this.title = title; return this; }

    public Goal setAdherenceCheckIds(Set<Integer> adherenceCheckIds) {
        this.adherenceCheckIds = adherenceCheckIds == null ? new HashSet<>() : adherenceCheckIds;
        return this;
    }

    public Set<Integer> getAdherenceCheckIds() { return adherenceCheckIds; }

    public Object getProperties() { return properties; }
    public Goal setProperties(Object properties) { this.properties = properties; return this; }

    public Instant getCreated() { return created; }
    public Goal setCreated(Instant created) { this.created = created; return this; }

    public Instant getModified() { return modified; }
    public Goal setModified(Instant modified) { this.modified = modified; return this; }

    public static Integer parseExternalGoalId(String templateId) throws ConflictException {
        Matcher m = PATTERN_GOAL_ID.matcher(Objects.requireNonNull(templateId));
        if(!m.find()){
            throw new ConflictException("Invalid goal ID: " + templateId);
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            throw new ConflictException("Invalid goal ID: " + templateId);
        }
    }


}