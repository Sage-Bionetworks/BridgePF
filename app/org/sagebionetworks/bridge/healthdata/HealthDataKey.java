package org.sagebionetworks.bridge.healthdata;

public final class HealthDataKey {

    private final long studyId;
    private final long trackerId;
    private final String sessionToken;
    
    public HealthDataKey(long studyId, long trackerId, String sessionToken) {
        this.studyId = studyId;
        this.trackerId = trackerId;
        this.sessionToken = sessionToken;
    }

    public long getStudyId() {
        return studyId;
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
        result = prime * result + (int) (studyId ^ (studyId >>> 32));
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
        if (studyId != other.studyId)
            return false;
        if (trackerId != other.trackerId)
            return false;
        return true;
    }
    
}
