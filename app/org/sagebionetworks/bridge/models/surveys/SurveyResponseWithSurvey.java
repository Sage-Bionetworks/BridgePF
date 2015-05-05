package org.sagebionetworks.bridge.models.surveys;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyResponse.Status;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class SurveyResponseWithSurvey {

    private final SurveyResponse response;
    private final Survey survey;
    
    public SurveyResponseWithSurvey(SurveyResponse response, Survey survey) {
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
