package org.sagebionetworks.bridge.models.studies;

import java.util.HashMap;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
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
    public SharingScope getSharingScope() {
        String name = get(UserProfile.SHARING_SCOPE_FIELD);
        return (name == null) ? null : SharingScope.valueOf(name);
    }
    public void setSharingScope(SharingScope scope) {
        if (scope != null) {
            put(UserProfile.SHARING_SCOPE_FIELD, scope.name());    
        }
    }
    public Boolean getNotifyByEmail() {
        String emptyString = get(UserProfile.NOTIFY_BY_EMAIL_FIELD);
        return (emptyString == null) ? null : Boolean.valueOf(emptyString);
    }
    public void setNotifyByEmail(Boolean notifyByEmail) {
        put(UserProfile.NOTIFY_BY_EMAIL_FIELD, notifyByEmail.toString());
    }
}
