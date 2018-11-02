package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * An identifier that can be used to find an account (a study identifier with an ID, email, or phone number).
 * Note that AccountId inequality does not indicate the objects represent two different accounts! 
 */
public final class AccountId implements BridgeEntity {
    
    public final static AccountId forId(String studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        return new AccountId(studyId, id, null, null, null, null, true);
    }
    public final static AccountId forEmail(String studyId, String email) {
        checkNotNull(studyId);
        checkNotNull(email);
        return new AccountId(studyId, null, email, null, null, null, true);
    }
    public final static AccountId forPhone(String studyId, Phone phone) {
        checkNotNull(studyId);
        checkNotNull(phone);
        return new AccountId(studyId, null, null, phone, null, null, true);
    }
    public final static AccountId forHealthCode(String studyId, String healthCode) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        return new AccountId(studyId, null, null, null, healthCode, null, true);
    }
    public final static AccountId forExternalId(String studyId, String externalId) {
        checkNotNull(studyId);
        checkNotNull(externalId);
        return new AccountId(studyId, null, null, null, null, externalId, true);
    }

    private final String studyId;
    private final String id;
    private final String email;
    private final Phone phone;
    private final String healthCode;
    private final String externalId;
    private final boolean usePreconditions;

    @JsonCreator
    private AccountId(@JsonProperty("study") String studyId, @JsonProperty("id") String id,
            @JsonProperty("email") String email, @JsonProperty("phone") Phone phone,
            @JsonProperty("healthCode") String healthCode, @JsonProperty("externalId") String externalId) {
        this(studyId, id, email, phone, healthCode, externalId, true);
    }
    
    private AccountId(String studyId, String id, String email, Phone phone, String healthCode,
            String externalId, boolean usePreconditions) {
        this.studyId = studyId;
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.healthCode = healthCode;
        this.externalId = externalId;
        this.usePreconditions = usePreconditions;
    }
    
    // It's important to guard against constructing AccountId with one of the identifying values, 
    // then trying to retrieve a different value. Force a failure in this case until you get to 
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
    public String getExternalId() {
        if (usePreconditions && externalId == null) {
            throw new NullPointerException("AccountId.externalId is null");
        }
        return externalId;
    }
    public AccountId getUnguardedAccountId() {
        return new AccountId(this.studyId, this.id, this.email, this.phone, this.healthCode, this.externalId, false);
    }
    @Override
    public int hashCode() {
        return Objects.hash(studyId, email, id, phone, healthCode, externalId, usePreconditions);
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
                Objects.equals(externalId, other.externalId) && 
                Objects.equals(usePreconditions, other.usePreconditions);
    }
    @Override
    public String toString() {
        Set<Object> keys = Sets.newHashSet(id, email, phone, externalId, (healthCode==null) ? null : "HEALTH_CODE");
        return "AccountId [studyId=" + studyId + ", credential=" + Joiner.on(", ").skipNulls().join(keys) + "]";
    }
    
}
