package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * This validates data sent to the server using the survey response payload, which is pretty sparse: 
 * normally all we require are the survey key values, and an array of zero or more answers
 */
public class SurveyResponseValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return SurveyResponse.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        SurveyResponse response = (SurveyResponse)object;
        
        if (isBlank(response.getSurveyGuid())) {
            errors.rejectValue("surveyGuid", "cannot be null or blank");
        }
        if (response.getSurveyCreatedOn() == 0L) {
            errors.rejectValue("surveyCreatedOn", "cannot be null");
        }
    }
    
}
