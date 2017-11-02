package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * An identifier that can be used to find an account (a study identifier with an ID, email, or phone number).
 * Note that AccountId inequality does not indicate the objects represent two different accounts! 
 */
public final class AccountId {
    
    public final static AccountId forId(String studyId, String id) {
        checkNotNull(studyId);
        checkNotNull(id);
        return new AccountId(studyId, id, null, null, true);
    }
    public final static AccountId forEmail(String studyId, String email) {
        checkNotNull(studyId);
        checkNotNull(email);
        return new AccountId(studyId, null, email, null, true);
    }
    public final static AccountId forPhone(String studyId, Phone phone) {
        checkNotNull(studyId);
        checkNotNull(phone);
        return new AccountId(studyId, null, null, phone, true);
    }
    
    private final String studyId;
    private final String id;
    private final String email;
    private final Phone phone;
    private final boolean usePreconditions;
    
    private AccountId(String studyId, String id, String email, Phone phone, boolean usePreconditions) {
        this.studyId = studyId;
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.usePreconditions = usePreconditions;
    }
    
    // It's important to guard against constructing AccountId with one of the identifying values, 
    // then trying to retrieve a different value. Force a failue in this case until you get to 
    // the DAO where these values are checked to determine the type of database query.
    
    public String getStudyId() {
        //Preconditions.checkArgument(usePreconditions && studyId != null);
        Preconditions.checkArgument( !(usePreconditions && studyId == null) );
        return studyId;
    }
    public String getId() {
        //Preconditions.checkArgument(usePreconditions && id != null);
        Preconditions.checkArgument( !(usePreconditions && id == null) );
        return id;
    }
    public String getEmail() {
        //Preconditions.checkArgument(usePreconditions && email != null);
        Preconditions.checkArgument( !(usePreconditions && email == null) );
        return email;
    }
    public Phone getPhone() {
        //Preconditions.checkArgument(usePreconditions && phone != null);
        Preconditions.checkArgument( !(usePreconditions && phone == null) );
        return phone;
    }
    
    public AccountId getUnguardedAccountId() {
        return new AccountId(this.studyId, this.id, this.email, this.phone, false);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, email, id, phone, usePreconditions);
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
                Objects.equals(usePreconditions, other.usePreconditions);
    }
    
}
