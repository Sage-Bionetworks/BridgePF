package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as=DynamoSurveyQuestion.class)
@BridgeTypeName("SurveyQuestion")
public interface SurveyQuestion extends SurveyElement {
    
    public String getTitle();
    public void setTitle(String title);
    
    public String getPrompt();
    public void setPrompt(String prompt);
    
    public String getPromptDetail();
    public void setPromptDetail(String promptDetail);
    
    public UIHint getUiHint();
    public void setUiHint(UIHint hint);

    public Constraints getConstraints();
    public void setConstraints(Constraints constraints);

}
