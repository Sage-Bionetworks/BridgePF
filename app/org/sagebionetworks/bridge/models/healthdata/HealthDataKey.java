package org.sagebionetworks.bridge.models.healthdata;

import org.apache.commons.httpclient.HttpStatus;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;
import org.sagebionetworks.bridge.models.User;

public final class HealthDataKey {

    private final String studyKey;
    private final long trackerId;
    private final User user;
    
    public HealthDataKey(Study study, Tracker tracker, User user) {
        if (study == null) {
            throw new BridgeServiceException("HealthDataKey study must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(study.getKey())) {
            throw new BridgeServiceException("HealthDataKey study must have a valid key", HttpStatus.SC_BAD_REQUEST);
        } else if (tracker == null) {
            throw new BridgeServiceException("HealthDataKey tracker must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (tracker.getId() == null || tracker.getId().longValue() == 0L) {
            throw new BridgeServiceException("HealthDataKey tracker must have a valid ID", HttpStatus.SC_BAD_REQUEST);
        } else if (user == null) {
            throw new BridgeServiceException("HealthDataKey user must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(user.getHealthDataCode())) {
            throw new BridgeServiceException("User health data code must not be null", HttpStatus.SC_BAD_REQUEST);
        }
        this.studyKey = study.getKey();
        this.trackerId = tracker.getId();
        this.user = user;
    }

    public String getStudyKey() {
        return studyKey;
    }
    public long getTrackerId() {
        return trackerId;
    }
    public User getUser() {
        return user;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((studyKey == null) ? 0 : studyKey.hashCode());
        result = prime * result + (int) (trackerId ^ (trackerId >>> 32));
        result = prime * result + ((user == null) ? 0 : user.hashCode());
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
        if (studyKey == null) {
            if (other.studyKey != null)
                return false;
        } else if (!studyKey.equals(other.studyKey))
            return false;
        if (trackerId != other.trackerId)
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.equals(other.user))
            return false;
        return true;
    }

}
