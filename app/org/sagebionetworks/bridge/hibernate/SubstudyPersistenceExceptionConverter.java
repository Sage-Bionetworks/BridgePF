package org.sagebionetworks.bridge.hibernate;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.springframework.stereotype.Component;

@Component
public class SubstudyPersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException((HibernateSubstudy)entity);
        }
        return exception;
    }

}
