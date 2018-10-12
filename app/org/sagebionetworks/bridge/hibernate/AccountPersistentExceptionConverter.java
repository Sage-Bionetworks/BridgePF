package org.sagebionetworks.bridge.hibernate;

import java.util.Map;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

@Component
public class AccountPersistentExceptionConverter implements PersistenceExceptionConverter {

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
                // These are the constraint violation messages. Instead of finding the userId with another 
                // trip to the database... just include the credential that is conflicting. It makes more 
                // sense and we're still able to find the pre-existing account with this information.
                // "Duplicate entry 'api-email@email.com' for key 'Accounts-StudyId-Email-Index'"
                // "Duplicate entry 'api-+12064953700' for key 'Accounts-StudyId-Phone-Index'"
                // "Duplicate entry 'api-ext' for key 'Accounts-StudyId-ExternalId-Index'" 
                
                if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-ExternalId-Index'")) {
                    return new EntityAlreadyExistsException(Account.class,
                            "External ID has already been used by another account.",
                            getEntityKey("externalId", account.getExternalId()));
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Email-Index'")) {
                    return new EntityAlreadyExistsException(Account.class,
                            "Email address has already been used by another account.",
                            getEntityKey("email", account.getEmail()));
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Phone-Index'")) {
                    return new EntityAlreadyExistsException(Account.class,
                            "Phone number has already been used by another account.",
                            getEntityKey("phone", account.getPhone()));
                }
            }
            ConstraintViolationException.Builder cveBuilder = new ConstraintViolationException.Builder();
            if (message != null) {
                cveBuilder.withMessage(cause.getMessage());
            }
            return cveBuilder.build();
        }
        return exception;
    }
    
    private Map<String,Object> getEntityKey(String keyName, Object keyValue) {
        if (keyValue != null) {
            return ImmutableMap.of(keyName, keyValue);
        }
        return ImmutableMap.of();
    }

}
