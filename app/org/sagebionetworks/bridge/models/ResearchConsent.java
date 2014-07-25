package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;

public class ResearchConsent {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    
    private String name;
    private DateTime birthdate;
    
    public ResearchConsent(String name, DateTime birthdate) {
        this.name = name;
        this.birthdate = birthdate;
    }
    
    public static final ResearchConsent fromJson(JsonNode node) {
        String name = null;
        DateTime birthdate = null;
        if (node != null && node.get(NAME_FIELD) != null) {
            name = node.get(NAME_FIELD).asText();
        }
        if (node != null && node.get(BIRTHDATE_FIELD) != null) {
            DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
            birthdate = fmt.parseDateTime(node.get(BIRTHDATE_FIELD).asText());
        }
        return new ResearchConsent(name, birthdate);
    }

    public String getName() {
        return name;
    }

    public DateTime getBirthdate() {
        return birthdate;
    }

}
