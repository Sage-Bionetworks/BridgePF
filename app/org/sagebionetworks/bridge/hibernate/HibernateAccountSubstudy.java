package org.sagebionetworks.bridge.hibernate;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudyId;

@Entity
@Table(name = "AccountsSubstudies")
@IdClass(AccountSubstudyId.class)
public final class HibernateAccountSubstudy implements AccountSubstudy {

    @Id
    private String studyId;
    @Id
    private String substudyId;
    @Id
    @JoinColumn(name = "account_id")
    private String accountId;
    private String externalId;
    
    // Needed for Hibernate, or else you have to create an instantiation helper class
    public HibernateAccountSubstudy() {
    }
    
    public HibernateAccountSubstudy(String studyId, String substudyId, String accountId) {
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
    public String getExternalId() {
        return externalId;
    }
    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    public void setSubstudyId(String substudyId) {
        this.substudyId = substudyId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, externalId, studyId, substudyId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        HibernateAccountSubstudy other = (HibernateAccountSubstudy) obj;
        return Objects.equals(accountId, other.accountId) && 
               Objects.equals(externalId, other.externalId) && 
               Objects.equals(studyId, other.studyId) && 
               Objects.equals(substudyId, other.substudyId);
    }

    @Override
    public String toString() {
        return "HibernateAccountSubstudy [studyId=" + studyId + ", substudyId=" + substudyId + ", accountId="
                + accountId + ", externalId=" + externalId + "]";
    }
}
