package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;

@Entity
@Table(name = "AccountSecrets")
public class HibernateAccountSecret implements AccountSecret {

    private String hash;
    private String accountId;
    private PasswordAlgorithm algorithm;
    private DateTime createdOn;
    private AccountSecretType type;
    
    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    @Id
    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    @Enumerated(EnumType.STRING)
    @Override
    public PasswordAlgorithm getAlgorithm() {
        return algorithm;
    }

    @Override
    public void setAlgorithm(PasswordAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    
    @Enumerated(EnumType.STRING)
    @Override
    public AccountSecretType getType() {
        return type;
    }

    @Override
    public void setType(AccountSecretType type) {
        this.type = type;
    }

}
