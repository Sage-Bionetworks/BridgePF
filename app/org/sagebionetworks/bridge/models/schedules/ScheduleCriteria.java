package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Sets;

public final class ScheduleCriteria implements Criteria {
    private final Schedule schedule;
    private final Integer minAppVersion;
    private final Integer maxAppVersion;
    private final Set<String> allOfGroups;
    private final Set<String> noneOfGroups;
    
    @JsonCreator
    private ScheduleCriteria(@JsonProperty("schedule") Schedule schedule, 
            @JsonProperty("minAppVersion") Integer minAppVersion, 
            @JsonProperty("maxAppVersion") Integer maxAppVersion, 
            @JsonProperty("allOfGroups") Set<String> allOfGroups, 
            @JsonProperty("noneOfGroups") Set<String> noneOfGroups) {
        this.schedule = schedule;
        this.minAppVersion = minAppVersion;
        this.maxAppVersion = maxAppVersion;
        this.allOfGroups = allOfGroups;
        this.noneOfGroups = noneOfGroups;
    }
    public Schedule getSchedule() {
        return schedule;
    }
    @Override
    public Integer getMinAppVersion() {
        return minAppVersion;
    }
    @Override
    public Integer getMaxAppVersion() {
        return maxAppVersion;
    }
    @Override
    public Set<String> getAllOfGroups() {
        return allOfGroups;
    }
    @Override
    public Set<String> getNoneOfGroups() {
        return noneOfGroups;
    }
    
    public static class Builder {
        private Schedule schedule;
        private Integer minAppVersion;
        private Integer maxAppVersion;
        private Set<String> allOfGroups = Sets.newHashSet();
        private Set<String> noneOfGroups = Sets.newHashSet();
        
        public Builder withSchedule(Schedule schedule) {
            this.schedule = schedule;
            return this;
        }
        public Builder withMinAppVersion(Integer minAppVersion) {
            this.minAppVersion = minAppVersion;
            return this;
        }
        public Builder withMaxAppVersion(Integer maxAppVersion) {
            this.maxAppVersion = maxAppVersion;
            return this;
        }
        public Builder addRequiredGroup(String... groups) {
            for (String group : groups) {
                this.allOfGroups.add(group);
            }
            return this;
        }
        public Builder addProhibitedGroup(String... groups) {
            for (String group : groups) {
                this.noneOfGroups.add(group);    
            }
            return this;
        }
        public ScheduleCriteria build() {
            return new ScheduleCriteria(schedule, minAppVersion, maxAppVersion, allOfGroups, noneOfGroups);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(minAppVersion, maxAppVersion, allOfGroups, noneOfGroups, schedule);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleCriteria other = (ScheduleCriteria) obj;
        return Objects.equals(minAppVersion, other.minAppVersion) && Objects.equals(maxAppVersion, other.maxAppVersion)
                && Objects.equals(allOfGroups, other.allOfGroups) && Objects.equals(noneOfGroups, other.noneOfGroups) 
                && Objects.equals(schedule, other.schedule);
    }
    @Override
    public String toString() {
        return "ScheduleCriteria [schedule=" + schedule + ", minAppVersion=" + minAppVersion + ", maxAppVersion="
                + maxAppVersion + ", allOfGroups=" + allOfGroups + ", noneOfGroups=" + noneOfGroups + "]";
    }

}
