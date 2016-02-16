package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Sets;

@JsonDeserialize(builder=ScheduleCriteria.Builder.class)
public final class ScheduleCriteria implements Criteria {
    private final Schedule schedule;
    private final Criteria criteria;
    private final String key;
    private final Integer minAppVersion;
    private final Integer maxAppVersion;
    private final Set<String> allOfGroups;
    private final Set<String> noneOfGroups;
    
    private ScheduleCriteria(Schedule schedule, Criteria criteria, String key, Integer minAppVersion,
            Integer maxAppVersion, Set<String> allOfGroups, Set<String> noneOfGroups) {
        this.schedule = schedule;
        this.criteria = criteria;
        this.key = key;
        this.minAppVersion = minAppVersion;
        this.maxAppVersion = maxAppVersion;
        this.allOfGroups = (allOfGroups != null) ? allOfGroups : Sets.newHashSet();
        this.noneOfGroups = (noneOfGroups != null) ? noneOfGroups : Sets.newHashSet();
    }
    public Schedule getSchedule() {
        return schedule;
    }
    @JsonIgnore
    public Criteria getCriteria() {
        return criteria;
    }
    @Override
    public String getKey() {
        return key;
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
        private Criteria criteria;
        private String key;
        private Integer minAppVersion;
        private Integer maxAppVersion;
        private Set<String> allOfGroups = Sets.newHashSet();
        private Set<String> noneOfGroups = Sets.newHashSet();
        
        public Builder withScheduleCriteria(ScheduleCriteria criteria) {
            this.schedule = criteria.schedule;
            this.criteria = criteria.criteria;
            this.key = criteria.key;
            this.minAppVersion = criteria.minAppVersion;
            this.maxAppVersion = criteria.maxAppVersion;
            this.allOfGroups = criteria.allOfGroups;
            this.noneOfGroups = criteria.noneOfGroups;
            return this;
        }
        public Builder withCriteria(Criteria criteria) {
            this.criteria = criteria;
            return this;
        }
        public Builder withSchedule(Schedule schedule) {
            this.schedule = schedule;
            return this;
        }
        public Builder withKey(String key) {
            this.key = key;
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
            return new ScheduleCriteria(schedule, criteria, key, minAppVersion, maxAppVersion, allOfGroups, noneOfGroups);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, minAppVersion, maxAppVersion, allOfGroups, noneOfGroups, schedule, criteria);
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
                && Objects.equals(schedule, other.schedule) && Objects.equals(key, other.key)
                && Objects.equals(criteria, other.criteria);
    }
    @Override
    public String toString() {
        return "ScheduleCriteria [key=" + key + ", schedule=" + schedule + ", minAppVersion=" + minAppVersion
                + ", maxAppVersion=" + maxAppVersion + ", allOfGroups=" + allOfGroups + ", noneOfGroups=" + noneOfGroups
                + ", criteria=" + criteria + "]";
    }

}
