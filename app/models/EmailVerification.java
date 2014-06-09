package models;

import com.fasterxml.jackson.databind.JsonNode;

public class EmailVerification {

    private static final String SPTOKEN_FIELD = "sptoken";
    
    private final String sptoken;
    
    public EmailVerification(String sptoken) {
        this.sptoken = sptoken;
    }
    
    public static final EmailVerification fromJson(JsonNode node) {
        String sptoken = null;
        if (node != null && node.get(SPTOKEN_FIELD) != null) {
            sptoken = node.get(SPTOKEN_FIELD).asText();
        }
        return new EmailVerification(sptoken);
    }

    public String getSptoken() {
        return sptoken;
    }

}
