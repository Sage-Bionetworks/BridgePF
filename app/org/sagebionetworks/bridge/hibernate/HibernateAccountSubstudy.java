package org.sagebionetworks.bridge.hibernate;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudyId;

@Entity
@Table(name = "AccountSubstudies")
//@IdClass(AccountSubstudyId.class)
public class HibernateAccountSubstudy implements AccountSubstudy {

    @EmbeddedId
    private AccountSubstudyId accountSubstudyId;
    
    private String externalId;
    
    @ManyToOne(fetch = FetchType.EAGER)
    private HibernateSubstudy substudy;
 
    @ManyToOne(fetch = FetchType.EAGER)
    private HibernateAccount account;
    
    @Override
    public AccountSubstudyId getAccountSubstudyId() {
        return accountSubstudyId;
    }

    @Override
    public String getExternalId() {
        return externalId;
    }
    
}
