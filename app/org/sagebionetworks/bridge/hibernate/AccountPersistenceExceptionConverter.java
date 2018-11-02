package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

@Component
public class AccountPersistenceExceptionConverter implements PersistenceExceptionConverter {
    private final AccountDao accountDao;
    
    @Autowired
    public AccountPersistenceExceptionConverter(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    
    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        // The sequence of type-checking and unwrapping of this exception is significant as unfortunately, 
        // the hierarchy of wrapped exceptions is very specific. 
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(
                    "Account has the wrong version number; it may have been saved in the background.");
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
                    eae = createEntityAlreadyExistsException("External ID",
                            AccountId.forExternalId(account.getStudyId(), account.getExternalId()));
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
            return new EntityAlreadyExistsException(Account.class,
                    credentialName + " has already been used by another account.",
                    ImmutableMap.of("userId", existingAccount.getId()));
        }
        return null;
    }

}
