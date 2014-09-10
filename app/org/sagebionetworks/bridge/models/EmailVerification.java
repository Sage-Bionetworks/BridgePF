package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

public class EmailVerification {

    private static final String SPTOKEN_FIELD = "sptoken";

    private final String sptoken;

    public EmailVerification(String sptoken) {
        this.sptoken = sptoken;
    }

    public static final EmailVerification fromJson(JsonNode node) {
        String sptoken = JsonUtils.asText(node, SPTOKEN_FIELD);
        return new EmailVerification(sptoken);
    }

    public String getSptoken() {
        return sptoken;
    }

}
