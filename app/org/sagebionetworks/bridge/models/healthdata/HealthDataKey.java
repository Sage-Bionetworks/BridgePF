package org.sagebionetworks.bridge.models.healthdata;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;

public final class HealthDataKey {

    private final String studyKey;
    private final long trackerId;
    private final String healthDataCode;
    
    public HealthDataKey(Study study, Tracker tracker, User user) {
        checkNotNull(study, "HealthDataKey study must not be null");
        checkNotNull(study.getKey(), "HealthDataKey study must have a valid key");
        checkNotNull(tracker, "HealthDataKey tracker must not be null");
        checkNotNull(tracker.getId() == null || tracker.getId().longValue() == 0L, "HealthDataKey tracker must have a valid ID");
        checkNotNull(user, "HealthDataKey user must not be null");
        checkArgument(StringUtils.isNotBlank(user.getHealthDataCode()), "HealthDataKey healthDataCode must not be null or blank");

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
