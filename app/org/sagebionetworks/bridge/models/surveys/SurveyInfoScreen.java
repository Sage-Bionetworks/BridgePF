package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurveyQuestion.class)
@BridgeTypeName("SurveyInfoScreen")
public interface SurveyInfoScreen extends SurveyElement {
    
    public String getTitle();
    public void setTitle(String title);
    
    public String getPrompt();
    public void setPrompt(String prompt);
    
    public String getPromptDetail();
    public void setPromptDetail(String promptDetail);
    
    public String getImageSource();
    public void setImageSource(String imageSource);
    
}
