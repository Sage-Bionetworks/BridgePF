package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.databind.JsonNode;

public class ConsentSignature {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    private static final String SEND_EMAIL_FIELD = "sendEmail";

    private String name;
    private String birthdate;
    private boolean sendEmail;

    public ConsentSignature(String name, String birthdate, boolean sendEmail) {
        this.name = name;
        this.birthdate = DateConverter.convertISODateTime(birthdate);
        this.sendEmail = sendEmail;
    }
    
    public ConsentSignature(String name, String birthdate) {
        this(name, birthdate, true);
    }

    public static final ConsentSignature fromJson(JsonNode node) {
        String name = null;
        String birthdate = null;
        boolean sendEmail = true;
        
        if (node != null && node.get(NAME_FIELD) != null) {
            name = node.get(NAME_FIELD).asText();
        }
        if (node != null && node.get(BIRTHDATE_FIELD) != null) {
            birthdate = node.get(BIRTHDATE_FIELD).asText();
        }
        if (node != null && node.get(SEND_EMAIL_FIELD) != null) {
            sendEmail = node.get(SEND_EMAIL_FIELD).asBoolean();
        }
        return new ConsentSignature(name, birthdate, sendEmail);
    }

    public String getName() {
        return name;
    }

    public String getBirthdate() {
        return birthdate;
    }
    
    public boolean isSendEmail() {
        return sendEmail;
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
