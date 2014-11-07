package org.sagebionetworks.bridge.models.healthdata;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.validators.Validate;

public final class HealthDataKey {

    private final String studyKey;
    private final String trackerIdentifier;
    private final String healthDataCode;
    
    public HealthDataKey(Study study, Tracker tracker, User user) {
        checkNotNull(study, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(study.getKey(), Validate.CANNOT_BE_NULL, "study key");
        checkNotNull(tracker, Validate.CANNOT_BE_NULL, "tracker");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkArgument(isNotBlank(tracker.getIdentifier()), Validate.CANNOT_BE_BLANK, "tracker identifier");
        checkArgument(isNotBlank(user.getHealthDataCode()), Validate.CANNOT_BE_BLANK, "user healthDataCode");

        this.studyKey = study.getKey();
        this.trackerIdentifier = tracker.getIdentifier();
        this.healthDataCode = user.getHealthDataCode();
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", studyKey, trackerIdentifier, healthDataCode);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((healthDataCode == null) ? 0 : healthDataCode.hashCode());
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
        result = prime * result + ((trackerIdentifier == null) ? 0 : trackerIdentifier.hashCode());
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
        if (trackerIdentifier == null) {
            if (other.trackerIdentifier != null)
                return false;
        } else if (!trackerIdentifier.equals(other.trackerIdentifier))
            return false;
        return true;
    }
}
