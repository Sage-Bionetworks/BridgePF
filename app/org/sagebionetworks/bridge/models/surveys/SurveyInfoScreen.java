package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurveyQuestion.class)
@BridgeTypeName("SurveyInfoScreen")
public interface SurveyInfoScreen extends SurveyElement {
    
    String getTitle();
    void setTitle(String title);
    
    String getPrompt();
    void setPrompt(String prompt);
    
    String getPromptDetail();
    void setPromptDetail(String promptDetail);

    Image getImage();
    void setImage(Image image);
    
}
