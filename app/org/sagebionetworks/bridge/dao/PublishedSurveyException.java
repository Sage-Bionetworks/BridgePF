package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class PublishedSurveyException extends BridgeServiceException {

    private static final long serialVersionUID = 6286676048771804971L;
    
    private final Survey survey;
    
    public PublishedSurveyException(Survey survey) {
        super("Survey is in the wrong publication state for this operation.", 400);
        this.survey = survey;
    }

    public Survey getSurvey() {
        return survey;
    }

}
