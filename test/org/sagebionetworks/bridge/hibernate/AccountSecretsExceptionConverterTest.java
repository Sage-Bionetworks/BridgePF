package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertSame;

import javax.persistence.PersistenceException;

import org.junit.Test;

public class AccountSecretsExceptionConverterTest {
    
    @Test
    public void before() {
        AccountSecretsExceptionConverter converter = new AccountSecretsExceptionConverter();
        PersistenceException ex = new PersistenceException();
        RuntimeException returnValue = converter.convert(ex, new Object());
        assertSame(ex, returnValue);
    }

}
