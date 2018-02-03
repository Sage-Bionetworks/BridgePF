package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

/**
 * An identifier that can be used to find an account (a study identifier with an ID, email, or phone number).
 * Note that AccountId inequality does not indicate the objects represent two different accounts! 
 */
public final class AccountId {
    
    public final static AccountId forId(String studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        return new AccountId(studyId, id, null, null, null, true);
    }
    public final static AccountId forEmail(String studyId, String email) {
        checkNotNull(studyId);
        checkNotNull(email);
        return new AccountId(studyId, null, email, null, null, true);
    }
    public final static AccountId forPhone(String studyId, Phone phone) {
        checkNotNull(studyId);
        checkNotNull(phone);
        return new AccountId(studyId, null, null, phone, null, true);
    }
    public final static AccountId forHealthCode(String studyId, String healthCode) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        return new AccountId(studyId, null, null, null, healthCode, true);
    }
    
    private final String studyId;
    private final String id;
    private final String email;
    private final Phone phone;
    private final String healthCode;
    private final boolean usePreconditions;
    
    private AccountId(String studyId, String id, String email, Phone phone, String healthCode, boolean usePreconditions) {
        this.studyId = studyId;
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.healthCode = healthCode;
        this.usePreconditions = usePreconditions;
    }
    
    // It's important to guard against constructing AccountId with one of the identifying values, 
    // then trying to retrieve a different value. Force a failue in this case until you get to 
    // the DAO where these values are checked to determine the type of database query.
    
    public String getStudyId() {
        if (usePreconditions && studyId == null) {
            throw new NullPointerException("AccountId.studyId is null");
        }
        return studyId;
    }
    public String getId() {
        if (usePreconditions && id == null) {
            throw new NullPointerException("AccountId.id is null");
        }
        return id;
    }
    public String getEmail() {
        if (usePreconditions && email == null) {
            throw new NullPointerException("AccountId.email is null");
        }
        return email;
    }
    public Phone getPhone() {
        if (usePreconditions && phone == null) {
            throw new NullPointerException("AccountId.phone is null");
        }
        return phone;
    }
    public String getHealthCode() {
        if (usePreconditions && healthCode == null) {
            throw new NullPointerException("AccountId.healthCode is null");
        }
        return healthCode;
    }
    public AccountId getUnguardedAccountId() {
        return new AccountId(this.studyId, this.id, this.email, this.phone, this.healthCode, false);
    }
    @Override
    public int hashCode() {
        return Objects.hash(studyId, email, id, phone, healthCode, usePreconditions);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountId other = (AccountId) obj;
        return Objects.equals(studyId, other.studyId) &&
                Objects.equals(email, other.email) && 
                Objects.equals(id, other.id) &&
                Objects.equals(phone, other.phone) &&
                Objects.equals(healthCode, other.healthCode) && 
                Objects.equals(usePreconditions, other.usePreconditions);
    }
    
}
