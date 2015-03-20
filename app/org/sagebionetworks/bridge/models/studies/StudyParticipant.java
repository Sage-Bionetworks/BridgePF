package org.sagebionetworks.bridge.models.studies;

import java.util.HashMap;

import org.sagebionetworks.bridge.models.UserProfile;

@SuppressWarnings("serial")
public final class StudyParticipant extends HashMap<String,String> {

    public String getEmpty(Object key) {
        String value = super.get(key);
        return (value == null) ? "" : value;
    }
    
    public String getFirstName() {
        return getEmpty(UserProfile.FIRST_NAME_FIELD);
    }
    public void setFirstName(String firstName) {
        put(UserProfile.FIRST_NAME_FIELD, firstName);
    }
    public String getLastName() {
        return getEmpty(UserProfile.LAST_NAME_FIELD);
    }
    public void setLastName(String lastName) {
        put(UserProfile.LAST_NAME_FIELD, lastName);
    }
    public String getEmail() {
        return getEmpty(UserProfile.EMAIL_FIELD);
    }
    public void setEmail(String email) {
        put(UserProfile.EMAIL_FIELD, email);
    }
    public String getPhone() {
        return getEmpty(UserProfile.PHONE_FIELD);
    }
    public void setPhone(String phone) {
        put(UserProfile.PHONE_FIELD, phone);
    }

}
