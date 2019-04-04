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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AccountSecretDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.AccountSecret;
import org.sagebionetworks.bridge.models.accounts.AccountSecretType;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.time.DateUtils;

/** Hibernate implementation of Account Secret Dao. */
@Component
public class HibernateAccountSecretDao implements AccountSecretDao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountSecretDao.class);
    
    static final String GET_QUERY = "SELECT secret FROM HibernateAccountSecret as secret " + 
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
    public Optional<AccountSecret> verifySecret(AccountSecretType type, String accountId, String plaintext,
            int rotations) {
        checkNotNull(type);
        checkNotNull(accountId);
        checkNotNull(plaintext);
        
        Map<String,Object> params = new HashMap<>();
        params.put("accountId", accountId);
        params.put("type", type);
        
        List<HibernateAccountSecret> secrets = hibernateHelper.queryGet(
                GET_QUERY, params, 0, rotations, HibernateAccountSecret.class);
        for (HibernateAccountSecret accountSecret : secrets) {
            try {
                // It's not possible to cache the hashed plaintext, as it is being compared to a hash
                // that has been seeded with a random salt (in the default algorithm's case). So we
                // must extract and use that salt + iterations to compare the hashes.
                if (accountSecret.getAlgorithm().checkHash(accountSecret.getHash(), plaintext)) {
                    return Optional.of(accountSecret);
                }
            } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                LOG.error("Error checking reauthentication token", e);
            }
        }
        return Optional.empty();
    }
    
    @Override
    public void removeSecrets(AccountSecretType type, String accountId) {
        checkNotNull(type);
        checkNotNull(accountId);
        
        Map<String,Object> params = new HashMap<>();
        params.put("accountId", accountId);
        params.put("type", type);
        
        hibernateHelper.query(DELETE_QUERY, params);
    }

}
