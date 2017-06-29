package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.surveys.Image;
import org.sagebionetworks.bridge.models.surveys.SurveyElement;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DynamoSurveyInfoScreen extends DynamoSurveyElement implements SurveyInfoScreen {
    
    private String prompt;
    private String promptDetail;
    private String title;
    private Image image;
    
    public DynamoSurveyInfoScreen() {
        setType("SurveyInfoScreen");
    }
    
    public DynamoSurveyInfoScreen(SurveyElement entry) {
        setSurveyCompoundKey(entry.getSurveyCompoundKey());
        setType(entry.getType());
        setIdentifier(entry.getIdentifier());
        setGuid(entry.getGuid());
        setData(entry.getData());
        setBeforeRules(entry.getBeforeRules());
        setAfterRules(entry.getAfterRules());
    }

    @Override
    @DynamoDBIgnore
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    @DynamoDBIgnore
    public String getPrompt() {
        return prompt;
    }

    @Override
    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    @Override
    @DynamoDBIgnore
    public String getPromptDetail() {
        return promptDetail;
    }

    @Override
    public void setPromptDetail(String promptDetail) {
        this.promptDetail = promptDetail;
    }
    
    @Override
    @DynamoDBIgnore
    public Image getImage() {
        return image;
    }

    @Override
    public void setImage(Image image) {
        this.image = image;
    }
    
    @Override
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    @JsonIgnore
    public JsonNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put(PROMPT_PROPERTY, prompt);
        data.put(PROMPT_DETAIL_PROPERTY, promptDetail);
        data.put(TITLE_PROPERTY, title);
        data.putPOJO(IMAGE_PROPERTY, image);
        return data;
    }

    @Override
    public void setData(JsonNode data) {
        this.prompt = JsonUtils.asText(data, PROMPT_PROPERTY);
        this.promptDetail = JsonUtils.asText(data, PROMPT_DETAIL_PROPERTY);
        this.title = JsonUtils.asText(data, TITLE_PROPERTY);
        this.image = JsonUtils.asEntity(data, IMAGE_PROPERTY, Image.class);
    }
}
