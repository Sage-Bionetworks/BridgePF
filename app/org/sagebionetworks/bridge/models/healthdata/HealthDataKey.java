package org.sagebionetworks.bridge.models.healthdata;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.Tracker;
import org.sagebionetworks.bridge.validators.Validate;

public final class HealthDataKey {

    private final String studyKey;
    private final String trackerIdentifier;
    private final String healthCode;
    
    public HealthDataKey(StudyIdentifier studyIdentifier, Tracker tracker, User user) {
        checkNotNull(studyIdentifier, Validate.CANNOT_BE_NULL, "study");
        checkNotNull(tracker, Validate.CANNOT_BE_NULL, "tracker");
        checkNotNull(user, Validate.CANNOT_BE_NULL, "user");
        checkArgument(isNotBlank(tracker.getIdentifier()), Validate.CANNOT_BE_BLANK, "tracker identifier");
        checkArgument(isNotBlank(user.getHealthCode()), Validate.CANNOT_BE_BLANK, "user healthCode");

        this.studyKey = studyIdentifier.getIdentifier();
        this.trackerIdentifier = tracker.getIdentifier();
        this.healthCode = user.getHealthCode();
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", studyKey, trackerIdentifier, healthCode);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((healthCode == null) ? 0 : healthCode.hashCode());
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
        if (healthCode == null) {
            if (other.healthCode != null)
                return false;
        } else if (!healthCode.equals(other.healthCode))
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
