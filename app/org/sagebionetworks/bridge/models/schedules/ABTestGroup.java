package org.sagebionetworks.bridge.models.schedules;

public class ABTestGroup {
    private int percentage;
    private Schedule schedule;
    public ABTestGroup() { }
    public ABTestGroup(int percentage, Schedule schedule) {
        this.percentage = percentage;
        this.schedule = schedule;
    }
    public int getPercentage() {
        return percentage;
    }
    public void setPercentage(int perc) {
        this.percentage = perc;
    }
    public Schedule getSchedule() {
        return schedule;
    }
    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + percentage;
        result = prime * result + ((schedule == null) ? 0 : schedule.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ABTestGroup other = (ABTestGroup) obj;
        if (percentage != other.percentage)
            return false;
        if (schedule == null) {
            if (other.schedule != null)
                return false;
        } else if (!schedule.equals(other.schedule))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "ScheduleGroup [percentage=" + percentage + ", schedule=" + schedule + "]";
    }

}
