package org.sagebionetworks.bridge.hibernate;

import javax.persistence.PersistenceException;

public class AccountSecretsExceptionConverter implements PersistenceExceptionConverter {

    @Override
    public RuntimeException convert(PersistenceException exception, Object entity) {
        return exception;
    }

}
