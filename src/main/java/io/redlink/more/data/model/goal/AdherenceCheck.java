package io.redlink.more.data.model.goal;


import java.time.LocalTime;

public class AdherenceCheck {
    private Long studyId;
    private Integer checkId;
    private String title;
    private LocalTime time;

    // Getters & Setters...
    public Long getStudyId() { return studyId; }
    public AdherenceCheck setStudyId(Long studyId) { this.studyId = studyId; return this; }

    public Integer getCheckId() { return checkId; }
    public AdherenceCheck setCheckId(Integer checkId) { this.checkId = checkId; return this; }

    public String getTitle() { return title; }
    public AdherenceCheck setTitle(String title) { this.title = title; return this; }

    public LocalTime getTime() { return time; }
    public AdherenceCheck setTime(LocalTime time) { this.time = time; return this; }
}