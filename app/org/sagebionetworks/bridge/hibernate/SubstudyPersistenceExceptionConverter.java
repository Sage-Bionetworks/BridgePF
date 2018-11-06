package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import org.springframework.stereotype.Component;

@Component
public class SubstudyPersistenceExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        throw new RuntimeException(exception);
    }

}
