package org.sagebionetworks.bridge.models.substudies;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public final class AccountSubstudyId implements Serializable {
    private static final long serialVersionUID = -2741510011134968409L;

    @Column(name = "studyId")
    private String studyId;

    @Column(name = "substudyId")
    private String substudyId;
    
    @Column(name = "accountId")
    private String accountId;

    public AccountSubstudyId() {
    }
    public AccountSubstudyId(String studyId, String substudyId, String accountId) {
        this.studyId = studyId;
        this.substudyId = substudyId;
        this.accountId = accountId;
    }
    
    public String getStudyId() {
        return studyId;
    }
    public String getSubstudyId() {
        return substudyId;
    }
    public String getAccountId() {
        return accountId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studyId, substudyId, accountId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSubstudyId other = (AccountSubstudyId) obj;
        return Objects.equals(studyId, other.studyId) &&
                Objects.equals(substudyId, other.substudyId) &&
                Objects.equals(accountId, other.accountId);
    }    
}
