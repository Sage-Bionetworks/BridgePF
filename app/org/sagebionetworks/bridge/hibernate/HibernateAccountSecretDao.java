package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.time.DateUtils;

/** Hibernate implementation of Account Secret Dao. */
@Component
public class HibernateAccountSecretDao implements AccountSecretDao {
    static final String GET_QUERY = "SELECT secret FROM HibernateAccountSecret AS secret " + 
            "WHERE accountId = :accountId AND type = :type ORDER BY createdOn DESC";
    
    static final String DELETE_QUERY = "DELETE FROM HibernateAccountSecret WHERE " + 
            "accountId = :accountId AND type = :type";
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "substudyHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    protected String generateHash(PasswordAlgorithm algorithm, String plaintext) {
        try {
            return algorithm.generateHash(plaintext);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new BridgeServiceException("Could not generate secret", e);
        }
    }
    
    @Override
    public void createSecret(AccountSecretType type, String accountId, String plaintext) {
        checkNotNull(type);
        checkNotNull(accountId);
        checkNotNull(plaintext);
        
        AccountSecret secret = AccountSecret.create();
        secret.setAccountId(accountId);
        secret.setAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        secret.setHash(generateHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, plaintext));
        secret.setType(type);
        secret.setCreatedOn(DateUtils.getCurrentDateTime());

        hibernateHelper.create(secret, null); 
    }

    @Override
    public Optional<AccountSecret> verifySecret(AccountSecretType type, String accountId, String plaintext, int rotations) {
        return verifySecretInternal(null, type, accountId, plaintext, rotations);
    }
    
    @Override
    public Optional<AccountSecret> verifySecret(Account account, AccountSecretType type, String plaintext, int rotations) {
        if (account.getReauthTokenHash() != null) {
            HibernateAccountSecret secret = new HibernateAccountSecret();
            secret.setAlgorithm(account.getReauthTokenAlgorithm());
            secret.setHash(account.getReauthTokenHash());
            secret.setType(type);
            return verifySecretInternal(secret, type, account.getId(), plaintext, rotations);
        }
        return verifySecretInternal(null, type, account.getId(), plaintext, rotations);
    }
    
    private Optional<AccountSecret> verifySecretInternal(HibernateAccountSecret secret, AccountSecretType type,
            String accountId, String plaintext, int rotations) {
        Map<String,Object> params = new HashMap<>();
        params.put("accountId", accountId);
        params.put("type", type);
        
        List<HibernateAccountSecret> secrets = hibernateHelper.queryGet(GET_QUERY, params, 0, rotations,
                HibernateAccountSecret.class);
        // Allows us to include the legacy reauthentication token in the accounts record, until it is 
        // rotated out.
        if (secrets.size() < rotations && secret != null) {
            secrets.add(secret);
        }
        if (secrets.isEmpty()) {
            return Optional.empty();
        }
        // Because we're always going to use the same hash, unless we change this, it's sufficient to 
        // hash once and cache. We don't need to keep a map by hashing algorithm or anything like that.
        // Nevertheless, we'll verify the algorithm hasn't changed with each pass.
        PasswordAlgorithm lastAlgorithm = null;
        String lastHash = null;
        for (HibernateAccountSecret accountSecret : secrets) {
            if (accountSecret.getAlgorithm() != lastAlgorithm) {
                lastAlgorithm = accountSecret.getAlgorithm();
                lastHash = generateHash(lastAlgorithm, plaintext);
            }
            if (accountSecret.getHash().equals(lastHash)) {
                return Optional.of(accountSecret);
            }
        }
        return Optional.empty();
    }

    @Override
    public void removeSecrets(AccountSecretType type, String accountId) {
        Map<String,Object> params = new HashMap<>();
        params.put("accountId", accountId);
        params.put("type", type);
        
        hibernateHelper.query(DELETE_QUERY, params);
    }

}
