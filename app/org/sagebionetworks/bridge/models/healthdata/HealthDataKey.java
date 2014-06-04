package org.sagebionetworks.bridge.models.healthdata;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.Tracker;

public final class HealthDataKey {

    private final String studyKey;
    private final long trackerId;
    private final String sessionToken;
    
    public HealthDataKey(Study study, Tracker tracker, String sessionToken) {
        if (study == null) {
            throw new BridgeServiceException("HealthDataKey study must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(study.getKey())) {
            throw new BridgeServiceException("HealthDataKey study must have a valid key", HttpStatus.SC_BAD_REQUEST);
        } else if (tracker == null) {
            throw new BridgeServiceException("HealthDataKey tracker must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (tracker.getId() == null || tracker.getId().equals(0L)) {
            throw new BridgeServiceException("HealthDataKey tracker must have a valid ID", HttpStatus.SC_BAD_REQUEST);
        } else if (sessionToken == null) {
            throw new BridgeServiceException("HealthDataKey session token must not be null", HttpStatus.SC_BAD_REQUEST);
        } else if (StringUtils.isBlank(sessionToken)) {
            throw new BridgeServiceException("HealthDataKey session token is not valid", HttpStatus.SC_BAD_REQUEST);
        }
        this.studyKey = study.getKey();
        this.trackerId = tracker.getId();
        this.sessionToken = sessionToken;
    }

    public String getStudyKey() {
        return studyKey;
    }
    public long getTrackerId() {
        return trackerId;
    }
    public String getSessionToken() {
        return sessionToken;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((sessionToken == null) ? 0 : sessionToken.hashCode());
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
        if (sessionToken == null) {
            if (other.sessionToken != null)
                return false;
        } else if (!sessionToken.equals(other.sessionToken))
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
