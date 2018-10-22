package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.models.surveys.Survey;

@SuppressWarnings("serial")
@NoStackTraceException
public class PublishedSurveyException extends BridgeServiceException {
    
    private final Survey survey;

    public PublishedSurveyException(Survey survey, String message) {
        super(message, 400);
        this.survey = survey;
    }
    
    public PublishedSurveyException(Survey survey) {
        this(survey, "A published survey cannot be updated or deleted (only closed).");
    }
    
    public Survey getSurvey() {
        return survey;
    }

}
