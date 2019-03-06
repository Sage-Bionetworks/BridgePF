package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

import org.springframework.stereotype.Component;

@Component
public class AccountSecretsExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        // I cannot generate any SQL errors given the fact that we write and don't read,
        // and the primary key involves a partly randomized hashed string. Just return
        // persistence exception.
        return exception;
    }

}
