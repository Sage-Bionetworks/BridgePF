package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.util.BridgeCollectors;
import org.sagebionetworks.bridge.validators.ScheduleValidator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public final class CriteriaScheduleStrategy implements ScheduleStrategy {
    
    private final List<ScheduleCriteria> scheduleCriteria = Lists.newArrayList();

    public void addCriteria(ScheduleCriteria criteria) {
        this.scheduleCriteria.add(criteria);
    }
    
    public List<ScheduleCriteria> getScheduleCriteria() {
        return scheduleCriteria;
    }
    
    public void setScheduleCriteria(List<ScheduleCriteria> criteria) {
        this.scheduleCriteria.clear();
        if (criteria != null) {
            this.scheduleCriteria.addAll(criteria);    
        }
    }    
    
    /**
     * Iterate through the list of schedules and return the first schedule that matches 
     * the criteria submitted for this user and this request (so order matters). Can 
     * return null, and this is accounted for now elsewehere in the code.
     * @param plan
     * @param context
     * @return schedule that matches users's criteria, or null if no schedules match the 
     *      criteria.
     */
    @Override
    public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
        for (ScheduleCriteria criteria : scheduleCriteria) {
            if (CriteriaUtils.matchCriteria(context, criteria)) {
                return criteria.getSchedule();
            }
        }
        return null;        
    }

    @Override
    public void validate(Set<String> dataGroups, Set<String> taskIdentifiers, Errors errors) {
        for (int i=0; i < scheduleCriteria.size(); i++) {
            ScheduleCriteria criteria = scheduleCriteria.get(i);
            errors.pushNestedPath("scheduleCriteria["+i+"]");
            if (criteria.getSchedule() == null){
                errors.rejectValue("schedule", "is required");
            } else {
                errors.pushNestedPath("schedule");
                new ScheduleValidator(taskIdentifiers).validate(criteria.getSchedule(), errors);
                errors.popNestedPath();
            }
            CriteriaUtils.validate(criteria, dataGroups, errors);
            errors.popNestedPath();
        }
    }

    @Override
    public List<Schedule> getAllPossibleSchedules() {
        return scheduleCriteria.stream().map(ScheduleCriteria::getSchedule).collect(BridgeCollectors.toImmutableList());
    }        
    
    public static class ScheduleCriteria implements Criteria {
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
                checkNotNull(schedule);
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

    @Override
    public int hashCode() {
        return Objects.hash(scheduleCriteria);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CriteriaScheduleStrategy other = (CriteriaScheduleStrategy) obj;
        return Objects.equals(scheduleCriteria, other.scheduleCriteria);
    }

    @Override
    public String toString() {
        return "CriteriaScheduleStrategy [scheduleCriteria=" + scheduleCriteria + "]";
    }
}
