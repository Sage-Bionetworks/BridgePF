package org.sagebionetworks.bridge.models.healthdata;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;

public final class HealthDataKey {

    private final String studyKey;
    private final long trackerId;
    private final String healthDataCode;
    
    public HealthDataKey(Study study, Tracker tracker, User user) {
        if (study == null) {
            throw new BadRequestException("HealthDataKey study must not be null");
        } else if (StringUtils.isBlank(study.getKey())) {
            throw new BadRequestException("HealthDataKey study must have a valid key");
        } else if (tracker == null) {
            throw new BadRequestException("HealthDataKey tracker must not be null");
        } else if (tracker.getId() == null || tracker.getId().longValue() == 0L) {
            throw new BadRequestException("HealthDataKey tracker must have a valid ID");
        } else if (user == null) {
            throw new BadRequestException("HealthDataKey user must not be null");
        } else if (StringUtils.isBlank(user.getHealthDataCode())) {
            throw new BadRequestException("HealthDataKey healthDataCode must not be null");
        }
        this.studyKey = study.getKey();
        this.trackerId = tracker.getId();
        this.healthDataCode = user.getHealthDataCode();
    }

    public String getStudyKey() {
        return studyKey;
    }
    public long getTrackerId() {
        return trackerId;
    }
    public String getHealthDataCode() {
        return healthDataCode;
    }
    
    @Override
    public String toString() {
        return String.format("%s:%s:%s", getStudyKey(), getTrackerId(), getHealthDataCode());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((healthDataCode == null) ? 0 : healthDataCode.hashCode());
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
        result = prime * result + (int) (trackerId ^ (trackerId >>> 32));
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
        HealthDataKey other = (HealthDataKey) obj;
        if (healthDataCode == null) {
            if (other.healthDataCode != null)
                return false;
        } else if (!healthDataCode.equals(other.healthDataCode))
            return false;
        if (studyKey == null) {
            if (other.studyKey != null)
                return false;
        } else if (!studyKey.equals(other.studyKey))
            return false;
        if (trackerId != other.trackerId)
            return false;
        return true;
    }
}
