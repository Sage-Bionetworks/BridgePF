package org.sagebionetworks.bridge.hibernate;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretId;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;

@Entity
@Table(name = "AccountSecrets")
@IdClass(AccountSecretId.class)
public class HibernateAccountSecret implements AccountSecret {
    
    @Id
    private String hash;
    @Id
    private String accountId;
    @Enumerated(EnumType.STRING)
    private PasswordAlgorithm algorithm;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Enumerated(EnumType.STRING)
    private AccountSecretType type;
    
    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    @Override
    public String getHash() {
        return hash;
    }

    @Override
    public void setHash(String hash) {
        this.hash = hash;
    }
    
    @Override
    public PasswordAlgorithm getAlgorithm() {
        return algorithm;
    }

    @Override
    public void setAlgorithm(PasswordAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }

    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    
    @Override
    public AccountSecretType getType() {
        return type;
    }

    @Override
    public void setType(AccountSecretType type) {
        this.type = type;
    }
}
