package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.models.surveys.Survey;

@SuppressWarnings("serial")
public class ImmutableSurveyException extends BridgeServiceException {
    
    private final Survey survey;
    
    public ImmutableSurveyException(Survey survey) {
        super("Once published a survey cannot be updated.", 400);
        this.survey = survey;
    }

    public Survey getSurvey() {
        return survey;
    }

}
