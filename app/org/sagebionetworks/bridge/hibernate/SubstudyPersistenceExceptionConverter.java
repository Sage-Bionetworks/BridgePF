package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.springframework.stereotype.Component;

import com.google.common.base.Throwables;

@Component
public class SubstudyPersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException((HibernateSubstudy)entity);
        }
        if (exception.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            // The specific error message is buried in the root MySQLIntegrityConstraintViolationException
            Throwable cause = Throwables.getRootCause(exception);
            String message = cause.getMessage();

            String finalMessage = "Substudy table constraint prevented save or update.";
            if (message.matches(".*a foreign key constraint fails.*REFERENCES `Substudies`.*")) {
                finalMessage = "Substudy cannot be deleted, it is referenced by an account";
            }
            return new ConstraintViolationException.Builder().withMessage(finalMessage).build();
        }
        return exception;
    }

}
