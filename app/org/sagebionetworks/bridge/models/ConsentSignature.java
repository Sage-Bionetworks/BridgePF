package org.sagebionetworks.bridge.models;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignature {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    private static DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");

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

    private static DateTime parseDate(String date) {
        return fmt.parseDateTime(date);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((birthdate == null) ? 0 : birthdate.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConsentSignature other = (ConsentSignature) obj;
        if (birthdate == null) {
            if (other.birthdate != null)
                return false;
        } else if (!birthdate.equals(other.birthdate))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
