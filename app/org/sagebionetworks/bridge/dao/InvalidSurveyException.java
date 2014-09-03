package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class InvalidSurveyException extends BridgeServiceException {

    private static final long serialVersionUID = -8140190585391762426L;
    private Survey survey;
    
    public InvalidSurveyException(Survey survey) {
        super("Survey is invalid (most likely missing required fields)", 400);
        this.survey = survey;
    }
    
    public Survey getSurvey() {
        return survey;
    }

}
