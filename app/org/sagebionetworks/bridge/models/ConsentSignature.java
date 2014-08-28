package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignature {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    
    private String name;
    private String birthdate;
    
    public ConsentSignature(String name, String birthdate) {
        this.name = name;
        this.birthdate = parseDate(birthdate);
    }
    
    public static final ConsentSignature fromJson(JsonNode node) {
        String name = null;
        String birthdate = null;
        if (node != null && node.get(NAME_FIELD) != null) {
            name = node.get(NAME_FIELD).asText();
        }
        if (node != null && node.get(BIRTHDATE_FIELD) != null) {
            birthdate = node.get(BIRTHDATE_FIELD).asText();
        }
        return new ConsentSignature(name, birthdate);
    }

    public String getName() {
        return name;
    }

    public String getBirthdate() {
        return birthdate;
    }
    
    public String getType() {
        return this.getClass().getSimpleName();
    }

    private static String parseDate(String date) {
        return new Date(date).getISODate();
    }
}
