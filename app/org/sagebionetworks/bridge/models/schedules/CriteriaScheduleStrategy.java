package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.validators.ScheduleValidator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public final class CriteriaScheduleStrategy implements ScheduleStrategy {

    public static class ScheduleCriteria {
        private final Schedule schedule;
        private final Integer minAppVersion;
        private final Integer maxAppVersion;
        private final Set<String> allOfGroups;
        private final Set<String> noneOfGroups;

        @JsonCreator
        private ScheduleCriteria(@JsonProperty("schedule") Schedule schedule, 
                @JsonProperty("minAppVersin") Integer minAppVersion, 
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
        public Integer getMinAppVersion() {
            return minAppVersion;
        }
        public Integer getMaxAppVersion() {
            return maxAppVersion;
        }
        public Set<String> getAllOfGroups() {
            return allOfGroups;
        }
        public Set<String> getNoneOfGroups() {
            return noneOfGroups;
        }
        public boolean matches(ScheduleContext context) {
            Integer appVersion = context.getClientInfo().getAppVersion();
            if (appVersion != null) {
                if ((minAppVersion != null && appVersion < minAppVersion) ||
                    (maxAppVersion != null && appVersion > maxAppVersion)) {
                    return false;
                }
            }
            Set<String> dataGroups = context.getUserDataGroups();
            if (dataGroups != null) {
                if (!dataGroups.containsAll(allOfGroups)) {
                    return false;
                }
                for (String group : noneOfGroups) {
                    if (dataGroups.contains(group)) {
                        return false;
                    }
                }
            }
            return true;
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
    
    private final List<ScheduleCriteria> scheduleCriteria = Lists.newArrayList();
    
    public void addCriteria(ScheduleCriteria criteria) {
        this.scheduleCriteria.add(criteria);
    }
    
    public List<ScheduleCriteria> getScheduleCriteria() {
        return scheduleCriteria;
    }
    
    public void setScheduleCriteria(List<ScheduleCriteria> criteria) {
        if (criteria != null) {
            this.scheduleCriteria.addAll(criteria);    
        }
    }
    
    @Override
    public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
        for (ScheduleCriteria criteria : scheduleCriteria) {
            if (criteria.matches(context)) {
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
            if ((criteria.getMinAppVersion() != null && criteria.getMaxAppVersion() != null) && 
                (criteria.getMaxAppVersion() < criteria.getMinAppVersion())) {
                errors.rejectValue("maxAppVersion", "cannot be less than minAppVersion");
            }
            if (criteria.getMinAppVersion() != null && criteria.getMinAppVersion() < 0) {
                errors.rejectValue("minAppVersion", "cannot be negative");
            }
            if (criteria.getMaxAppVersion() != null && criteria.getMaxAppVersion() < 0) {
                errors.rejectValue("maxAppVersion", "cannot be negative");
            }
            validateDataGroups(errors, dataGroups, criteria.getAllOfGroups(), "allOfGroups");
            validateDataGroups(errors, dataGroups, criteria.getNoneOfGroups(), "noneOfGroups");
            errors.popNestedPath();
        }
    }
    
    private void validateDataGroups(Errors errors, Set<String> dataGroups, Set<String> criteriaGroups, String propName) {
        if (criteriaGroups != null) {
            for (String dataGroup : criteriaGroups) {
                if (!dataGroups.contains(dataGroup)) {
                    errors.rejectValue(propName, getDataGroupMessage(dataGroup, dataGroups));
                }
            }
        }
    }

    private String getDataGroupMessage(String identifier, Set<String> dataGroups) {
        String message = "'" + identifier + "' is not in enumeration: ";
        if (dataGroups.isEmpty()) {
            message += "<no data groups declared>";
        } else {
            message += Joiner.on(", ").join(dataGroups);
        }
        return message;
    }
    
    @Override
    public List<Schedule> getAllPossibleSchedules() {
        return ImmutableList.copyOf(
                scheduleCriteria.stream().map(ScheduleCriteria::getSchedule).collect(Collectors.toList()));
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
