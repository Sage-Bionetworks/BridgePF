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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((prompt == null) ? 0 : prompt.hashCode());
        result = prime * result + ((promptDetail == null) ? 0 : promptDetail.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + ((image == null) ? 0 : image.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DynamoSurveyInfoScreen other = (DynamoSurveyInfoScreen) obj;
        if (prompt == null) {
            if (other.prompt != null)
                return false;
        } else if (!prompt.equals(other.prompt))
            return false;
        if (promptDetail == null) {
            if (other.promptDetail != null)
                return false;
        } else if (!promptDetail.equals(other.promptDetail))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        if (image == null) {
            if (other.image != null)
                return false;
        } else if (!image.equals(other.image))
            return false;
        return true;
    }

}
