package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class SurveyNotFoundException extends BridgeServiceException {

    private static final long serialVersionUID = 8127893323437931302L;

    private Survey survey;
    
    public SurveyNotFoundException(Survey survey) {
        super("Survey not found.", 404);
        this.survey = survey;
    }

    public Survey getSurvey() {
        return survey;
    }
    
}
