package org.sagebionetworks.bridge.exceptions;

import org.sagebionetworks.bridge.models.studies.Study;

@SuppressWarnings("serial")
public class StudyLimitExceededException extends BridgeServiceException {

    // 473 - "Study Limit Exceeded", not yet and probably never listed in the list of HTTP status codes...
    
    public StudyLimitExceededException(Study study) {
        super("The study '" + study.getName() + "' has reached the limit of allowed participants.", 473);
    }

}
