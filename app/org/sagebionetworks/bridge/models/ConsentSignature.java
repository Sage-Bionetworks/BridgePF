package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignature {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    private static final DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
    
    private final String name;
    private final DateTime birthdate;
    
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
        return birthdate.toString().split("T")[0];
    }
    
    public String getType() {
        return this.getClass().getSimpleName();
    }
    
    private static DateTime parseDate(String date) {
        return fmt.parseDateTime(date);
    }
}
