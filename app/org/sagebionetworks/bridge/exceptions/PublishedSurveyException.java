package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.models.surveys.Survey;

@SuppressWarnings("serial")
public class PublishedSurveyException extends BridgeServiceException {
    
    private final Survey survey;
    
    public PublishedSurveyException(Survey survey) {
        super("A published survey cannot be updated or deleted (only closed).", 400);
        this.survey = survey;
    }

    public Survey getSurvey() {
        return survey;
    }

}
