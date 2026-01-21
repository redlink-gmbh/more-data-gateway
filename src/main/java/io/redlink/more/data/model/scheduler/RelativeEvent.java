package io.redlink.more.data.model.scheduler;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelativeEvent implements ScheduleEvent {
    public static final String TYPE = "RelativeEvent";
    private String type;

    private RelativeDate dtstart;

    private RelativeDate dtend;

    private RelativeRecurrenceRule rrrule;

    private Randomization randomization = Randomization.none;

    public RelativeEvent() {
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public RelativeDate getDtstart() {
        return dtstart;
    }

    public RelativeEvent setDtstart(RelativeDate dtstart) {
        this.dtstart = dtstart;
        return this;
    }

    public RelativeDate getDtend() {
        return dtend;
    }

    public RelativeEvent setDtend(RelativeDate dtend) {
        this.dtend = dtend;
        return this;
    }

    public RelativeRecurrenceRule getRrrule() {
        return rrrule;
    }

    public RelativeEvent setRrrule(RelativeRecurrenceRule rrrule) {
        this.rrrule = rrrule;
        return this;
    }

    public Randomization getRandomization() {
        return randomization;
    }

    @JsonSetter(value = "randomization", nulls = Nulls.SKIP)
    public RelativeEvent setRandomization(Randomization randomization) {
        this.randomization = randomization;
        return this;
    }
}
