package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class SurveyAlreadyExistsException extends BridgeServiceException {
    
    private static final long serialVersionUID = 1797476899905986637L;
    private Survey survey;
    
    public SurveyAlreadyExistsException(Survey survey) {
        super("Survey already exists.", 400);
        this.survey = survey;
    }
    
    public Survey getSurvey() {
        return survey;
    }

}
