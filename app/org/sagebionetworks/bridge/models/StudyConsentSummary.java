package org.sagebionetworks.bridge.models;

public class StudyConsentSummary {
    private final int minAge;
    private final long createdOn;
    private final boolean active;
    private final String path;

    public StudyConsentSummary(StudyConsent studyConsent) {
        this.minAge = studyConsent.getMinAge();
        this.createdOn = studyConsent.getCreatedOn();
        this.active = studyConsent.getActive();
        this.path = studyConsent.getPath();
    }
    
    public StudyConsentSummary(int minAge, long createdOn, boolean active, String path) {
        this.minAge = minAge;
        this.createdOn = createdOn;
        this.active = active;
        this.path = path;
    }

    public int getMinAge() {
        return this.minAge;
    }

    public long getCreatedOn() {
        return this.createdOn;
    }

    public boolean isActive() {
        return this.active;
    }
    public String getPath() {
        return this.path;
    }

}
