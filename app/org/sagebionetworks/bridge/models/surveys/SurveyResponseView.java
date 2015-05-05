package org.sagebionetworks.bridge.models.surveys;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse.Status;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A version of the survey response object that includes the complete survey 
 * and serves to hide other values that should not be in the JSON returned from 
 * the API.
 *
 */
@BridgeTypeName("SurveyResponse")
public class SurveyResponseView {

    private final SurveyResponse response;
    private final Survey survey;
    
    public SurveyResponseView(SurveyResponse response, Survey survey) {
        checkNotNull(response);
        checkNotNull(survey);
        this.response = response;
        this.survey = survey;
    }
    
    public Survey getSurvey(){
        return survey;
    }
    @JsonIgnore
    public SurveyResponse getResponse() {
        return response;
    }
    public Long getVersion() {
        return response.getVersion();
    }
    public String getIdentifier() {
        return response.getIdentifier();
    }
    public Status getStatus() {
        return response.getStatus();
    }
    public Long getStartedOn() {
        return response.getStartedOn();
    }
    public Long getCompletedOn() {
        return response.getCompletedOn();
    }
    public List<SurveyAnswer> getAnswers() {
        return response.getAnswers();
    }

}
