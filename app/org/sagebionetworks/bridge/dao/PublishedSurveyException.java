package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.models.surveys.Survey;

public class PublishedSurveyException extends RuntimeException {

    private static final long serialVersionUID = 6286676048771804971L;
    
    private final String studyKey;
    private final String surveyGuid;
    private final long versionedOn;
    
    public PublishedSurveyException(Survey survey) {
        super("Survey is in the wrong publication state for this operation.");
        this.studyKey = survey.getStudyKey();
        this.surveyGuid = survey.getGuid();
        this.versionedOn = survey.getVersionedOn();
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
