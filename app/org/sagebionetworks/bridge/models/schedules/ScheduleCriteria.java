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
    
    private ScheduleCriteria(Schedule schedule, Criteria criteria) {
        this.schedule = schedule;
        this.criteria = criteria;
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
        return criteria.getKey();
    }
    @Override
    public Integer getMinAppVersion() {
        return criteria.getMinAppVersion();
    }
    @Override
    public Integer getMaxAppVersion() {
        return criteria.getMaxAppVersion();
    }
    @Override
    public Set<String> getAllOfGroups() {
        return criteria.getAllOfGroups();
    }
    @Override
    public Set<String> getNoneOfGroups() {
        return criteria.getNoneOfGroups();
    }
    
    public static class Builder {
        private Schedule schedule;
        private Criteria criteria;
        private String key;
        private Integer minAppVersion;
        private Integer maxAppVersion;
        private Set<String> allOfGroups = Sets.newHashSet();
        private Set<String> noneOfGroups = Sets.newHashSet();
        
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
        public Builder withAllOfGroups(Set<String> allOfGroups) {
            this.allOfGroups = (allOfGroups == null) ? Sets.newHashSet() : allOfGroups;
            return this;
        }
        public Builder withNoneOfGroups(Set<String> noneOfGroups) {
            this.noneOfGroups = (noneOfGroups == null) ? Sets.newHashSet() : noneOfGroups;
            return this;
        }
        public ScheduleCriteria build() {
            Criteria crit = Criteria.create();
            if (criteria != null) {
                crit.setKey(criteria.getKey());
                crit.setMinAppVersion(criteria.getMinAppVersion());
                crit.setMaxAppVersion(criteria.getMaxAppVersion());
                crit.setAllOfGroups(criteria.getAllOfGroups());
                crit.setNoneOfGroups(criteria.getNoneOfGroups());
            }
            if (key != null) {
                crit.setKey(key);
            }
            if (minAppVersion != null) {
                crit.setMinAppVersion(minAppVersion);
            }
            if (maxAppVersion != null) {
                crit.setMaxAppVersion(maxAppVersion);
            }
            if (!allOfGroups.isEmpty()) {
                crit.setAllOfGroups(allOfGroups);
            }
            if (!noneOfGroups.isEmpty()) {
                crit.setNoneOfGroups(noneOfGroups);
            }
            return new ScheduleCriteria(schedule, crit);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(schedule, criteria);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ScheduleCriteria other = (ScheduleCriteria) obj;
        return Objects.equals(schedule, other.schedule) && Objects.equals(criteria, other.criteria);
    }
    @Override
    public String toString() {
        return "ScheduleCriteria [schedule=" + schedule + ", criteria=" + criteria + "]";
    }
    
    // These have to exist during migration. Once we've moved to a separate criteria table and the criteria 
    // inteface can be removed from this object, these can be removed.
    @Override
    public void setKey(String key) {
    }
    @Override
    public void setMinAppVersion(Integer minAppVersion) {
    }
    @Override
    public void setMaxAppVersion(Integer maxAppVersion) {
    }
    @Override
    public void setAllOfGroups(Set<String> allOfGroups) {
    }
    @Override
    public void setNoneOfGroups(Set<String> noneOfGroups) {
    }
}
