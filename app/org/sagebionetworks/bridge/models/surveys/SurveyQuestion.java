package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.dynamodb.DynamoSurveyQuestion;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = DynamoSurveyQuestion.class)
@BridgeTypeName("SurveyQuestion")
public interface SurveyQuestion extends SurveyElement {

    String getPrompt();

    void setPrompt(String prompt);

    String getPromptDetail();

    void setPromptDetail(String promptDetail);

    boolean getFireEvent();

    void setFireEvent(boolean fireEvent);

    UIHint getUiHint();

    void setUiHint(UIHint hint);

    Constraints getConstraints();

    void setConstraints(Constraints constraints);

}
