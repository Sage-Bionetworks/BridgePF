package org.sagebionetworks.bridge.hibernate;

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
public class HibernateAccountSubstudy implements AccountSubstudy {

    @Id
    private String studyId;
    @Id
    private String substudyId;
    @Id
    @JoinColumn(name = "account_id") // TODO Does this do anything?
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
}
