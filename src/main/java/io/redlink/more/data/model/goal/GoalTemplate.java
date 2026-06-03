package io.redlink.more.data.model.goal;

import io.redlink.more.data.exception.ConflictException;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoalTemplate {
    public static final String GAOL_TEMPLATE_ID_PREFIX = "goaltemplate-";
    private static final Pattern PATTERN_GOAL_TEMPLATE_ID = Pattern.compile("^" + GAOL_TEMPLATE_ID_PREFIX + "(\\d+)$");

    private Long studyId;
    private Integer templateId;
    private String title;
    private String participantTitle;
    private String participantInfo;
    private String type;
    private String kind;
    private Integer studyGroupId;
    private Object properties;
    private Instant created;
    private Instant modified;
    private Set<Integer> observationGroupIds = new HashSet<>();
    private Set<String> topicKeys = new HashSet<>();
    private Set<Integer> adherenceCheckIds = new HashSet<>();

    // Getters & Setters (fluent)
    public Long getStudyId() { return studyId; }
    public GoalTemplate setStudyId(Long studyId) { this.studyId = studyId; return this; }

    public Integer getTemplateId() { return templateId; }
    public GoalTemplate setTemplateId(Integer templateId) { this.templateId = templateId; return this; }

    public String getExternalTemplateId() {
        return GAOL_TEMPLATE_ID_PREFIX + templateId;
    }

    public String getTitle() { return title; }
    public GoalTemplate setTitle(String title) { this.title = title; return this; }

    public String getParticipantTitle() { return participantTitle; }
    public GoalTemplate setParticipantTitle(String participantTitle) { this.participantTitle = participantTitle; return this; }

    public String getParticipantInfo() { return participantInfo; }
    public GoalTemplate setParticipantInfo(String participantInfo) { this.participantInfo = participantInfo; return this; }

    public String getType() { return type; }
    public GoalTemplate setType(String type) { this.type = type; return this; }

    public String getKind() { return kind; }
    public GoalTemplate setKind(String kind) { this.kind = kind; return this; }

    public Integer getStudyGroupId() { return studyGroupId; }
    public GoalTemplate setStudyGroupId(Integer studyGroupId) { this.studyGroupId = studyGroupId; return this; }

    public Object getProperties() { return properties; }
    public GoalTemplate setProperties(Object properties) { this.properties = properties; return this; }

    public Instant getCreated() { return created; }
    public GoalTemplate setCreated(Instant created) { this.created = created; return this; }

    public Instant getModified() { return modified; }
    public GoalTemplate setModified(Instant modified) { this.modified = modified; return this; }

    public Set<Integer> getObservationGroupIds() { return observationGroupIds; }
    public GoalTemplate setObservationGroupIds(Set<Integer> observationGroupIds) {
        this.observationGroupIds = observationGroupIds != null ? observationGroupIds : new HashSet<>();
        return this;
    }

    public Set<String> getTopicKeys() { return topicKeys; }
    public GoalTemplate setTopicKeys(Set<String> topicKeys) {
        this.topicKeys = topicKeys != null ? topicKeys : new HashSet<>();
        return this;
    }

    public Set<Integer> getAdherenceCheckIds() { return adherenceCheckIds; }
    public GoalTemplate setAdherenceCheckIds(Set<Integer> adherenceCheckIds) {
        this.adherenceCheckIds = adherenceCheckIds != null ? adherenceCheckIds : new HashSet<>();
        return this;
    }

    public static Integer parseExternalTemplateId(String templateId) throws ConflictException {
        Matcher m = PATTERN_GOAL_TEMPLATE_ID.matcher(Objects.requireNonNull(templateId));
        if(!m.find()){
            throw new ConflictException("Invalid template ID: " + templateId);
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            throw new ConflictException("Invalid template ID: " + templateId);
        }
    }

}