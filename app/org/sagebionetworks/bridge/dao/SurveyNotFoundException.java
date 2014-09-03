package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;

public class SurveyNotFoundException extends BridgeServiceException {

    private static final long serialVersionUID = 8127893323437931302L;

    private final String studyKey;
    private final String surveyGuid;
    private final long versionedOn;
    
    public SurveyNotFoundException(String studyKey, String surveyGuid, long versionedOn) {
        super("Survey not found.", 404);
        this.studyKey = studyKey;
        this.surveyGuid = surveyGuid;
        this.versionedOn = versionedOn;
    }

    public String getStudyKey() {
        return studyKey;
    }

    public String getSurveyGuid() {
        return surveyGuid;
    }
    
    public long getVersionedOn() {
        return versionedOn;
    }

}
