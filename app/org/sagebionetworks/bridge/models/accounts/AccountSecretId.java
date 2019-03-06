package org.sagebionetworks.bridge.models.accounts;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@SuppressWarnings("serial")
@Embeddable
public final class AccountSecretId implements Serializable {
    @Column(name = "accountId")
    private String accountId;

    @Column(name = "hash")
    private String hash;
    
    public AccountSecretId() {
    }
    public AccountSecretId(String accountId, String hash) {
        this.accountId = accountId;
        this.hash = hash;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public String getHash() {
        return hash;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accountId, hash);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        AccountSecretId other = (AccountSecretId) obj;
        return Objects.equals(hash, other.hash) && Objects.equals(accountId, other.accountId);
    }    
}
