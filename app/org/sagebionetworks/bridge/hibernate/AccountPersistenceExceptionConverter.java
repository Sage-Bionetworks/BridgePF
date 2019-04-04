package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.NonUniqueObjectException;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

@Component
public class AccountPersistenceExceptionConverter implements PersistenceExceptionConverter {
    private static final Logger LOG = LoggerFactory.getLogger(AccountPersistenceExceptionConverter.class);

    static final String NON_UNIQUE_MSG = "This account has already been associated to the substudy (possibly through another external ID).";
    
    private final AccountDao accountDao;

    @Autowired
    public AccountPersistenceExceptionConverter(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        // Some of these exceptions subclass PersistenceException, and some are wrapped by 
        // PersistenceException (such as org.hibernate.exception.ConstraintViolationException). I 
        // do not know the logic behind this. 
        
        // The sequence of type-checking and unwrapping of this exception is significant as unfortunately, 
        // the hierarchy of wrapped exceptions is very specific. 
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(
                    "Account has the wrong version number; it may have been saved in the background.");
        }
        // You can reliably trigger this exception by assigning a second external ID from a substudy 
        // that an account is already associated to.
        if (exception instanceof NonUniqueObjectException) {
            return new ConstraintViolationException.Builder().withMessage(NON_UNIQUE_MSG).build();
        }
        if (exception.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            // The specific error message is buried in the root MySQLIntegrityConstraintViolationException
            Throwable cause = Throwables.getRootCause(exception);
            String message = cause.getMessage();
            if (message != null && entity != null) {
                HibernateAccount account = (HibernateAccount)entity;
                // These are the constraint violation messages. To ensure we don't log credentials, we look
                // up the existing account and its userId to report in the EntityAlreadyExistsException.
                // Messages:
                // "Duplicate entry 'api-email@email.com' for key 'Accounts-StudyId-Email-Index'"
                // "Duplicate entry 'api-+12064953700' for key 'Accounts-StudyId-Phone-Index'"
                // "Duplicate entry 'api-ext' for key 'Accounts-StudyId-ExternalId-Index'" 
                EntityAlreadyExistsException eae = null;
                if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-ExternalId-Index'")) {
                    // We do not know which external ID is the conflict without parsing the error message. 
                    // Try them until we find one. This external ID could be in a sub-study the caller is 
                    // not associated to, but external IDs have to be unique at the scope of the study, 
                    // so the external ID must be exposed to the caller to troubleshoot.
                    for (String externalId : BridgeUtils.collectExternalIds(account)) {
                        eae = createEntityAlreadyExistsException("External ID",
                                AccountId.forExternalId(account.getStudyId(), externalId));
                        if (eae != null) {
                            break;
                        }
                    }
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Email-Index'")) {
                    eae = createEntityAlreadyExistsException("Email address",
                            AccountId.forEmail(account.getStudyId(), account.getEmail()));
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Phone-Index'")) {
                    eae = createEntityAlreadyExistsException("Phone number",
                            AccountId.forPhone(account.getStudyId(), account.getPhone()));
                }
                if (eae != null) {
                    return eae;
                }
            }
            return new ConstraintViolationException.Builder()
                    .withMessage("Accounts table constraint prevented save or update.").build();
        }
        return exception;
    }
    
    private EntityAlreadyExistsException createEntityAlreadyExistsException(String credentialName, AccountId accountId) {
        Account existingAccount = accountDao.getAccount(accountId);
        if (existingAccount != null) {
            // Log to make conflicts easier to diagnose.
            LOG.info(credentialName + " has already been used by account " + existingAccount.getId());
            return new EntityAlreadyExistsException(Account.class,
                    credentialName + " has already been used by another account.",
                    ImmutableMap.of("userId", existingAccount.getId()));
        }
        return null;
    }

}
