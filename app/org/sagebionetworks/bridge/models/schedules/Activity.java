package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

import com.fasterxml.jackson.databind.JsonNode;

public class Activity {

    private final ActivityType type;
    private final String ref;
    private final GuidCreatedOnVersionHolder survey;
    
    public static Activity fromJson(JsonNode node) {
        ActivityType type = JsonUtils.asActivityType(node, "type");
        String ref = JsonUtils.asText(node, "ref");
        JsonNode surveyNode = JsonUtils.asJsonNode(node, "survey");
        if (surveyNode != null) {
            String guid = JsonUtils.asText(surveyNode, "guid");
            long createdOn = JsonUtils.asMillisSinceEpoch(surveyNode, "createdOn");
            return new Activity(type, ref, new GuidCreatedOnVersionHolderImpl(guid, createdOn));
        }
        return new Activity(type, ref);
    }
    
    public Activity(ActivityType type, String ref, GuidCreatedOnVersionHolder survey) {
        this.type = type;
        this.ref = ref;
        this.survey = survey;
    }
    
    public Activity(ActivityType type, String ref) {
        this.type = type;
        this.ref = ref;
        if (ref.contains("/surveys/")) {
            String[] parts = ref.split("/surveys/")[1].split("/");
            String guid = parts[0];
            long createdOn = DateUtils.convertToMillisFromEpoch(parts[1]);
            this.survey = new GuidCreatedOnVersionHolderImpl(guid, createdOn);
        } else {
            this.survey = null;
        }
    }
    
    public ActivityType getType() {
        return type;
    }

    public String getRef() {
        return ref;
    }

    public GuidCreatedOnVersionHolder getSurvey() {
        return survey;
    }

}
