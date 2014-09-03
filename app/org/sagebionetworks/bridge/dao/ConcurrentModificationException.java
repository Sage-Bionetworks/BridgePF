package org.sagebionetworks.bridge.dao;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.surveys.Survey;

public class ConcurrentModificationException extends BridgeServiceException {

    private static final long serialVersionUID = 282926281684175001L;
    private Survey survey;
    
    public ConcurrentModificationException(Survey survey) {
        super("The survey's version is incorrect; it may have been saved in the background.", 400);
        this.survey = survey;
    }
    
    public Survey getSurvey() {
        return survey;
    }

}
