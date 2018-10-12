package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;

@RunWith(MockitoJUnitRunner.class)
public class AccountPersistentExceptionConverterTest {

    private AccountPersistentExceptionConverter converter;
    
    @Before
    public void before() {
        converter = new AccountPersistentExceptionConverter();
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(ex, converter.convert(ex, null));
    }
    
    @Test
    public void entityAlreadyExistsForEmail() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setEmail("bridge-testing+alxdark-test-authenticationservicetest-tfuuo@sagebase.org");
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-bridge-testing+alxdark-test-authenticationservicetest-tfuuo@sagebase.org' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("Email address has already been used by another account.", result.getMessage());
        assertEquals("bridge-testing+alxdark-test-authenticationservicetest-tfuuo@sagebase.org", ((EntityAlreadyExistsException)result).getEntityKeys().get("email"));
    }
    
    @Test
    public void entityAlreadyExistsForPhone() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setPhone(TestConstants.PHONE);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+TestConstants.PHONE.getNationalFormat()+"' for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("Phone number has already been used by another account.", result.getMessage());
        assertEquals(TestConstants.PHONE, ((EntityAlreadyExistsException)result).getEntityKeys().get("phone"));
    }

    @Test
    public void entityAlreadyExistsForExternalId() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setExternalId("ext");
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("External ID has already been used by another account.", result.getMessage());
        assertEquals("ext", ((EntityAlreadyExistsException)result).getEntityKeys().get("externalId"));
    }
    
    // This scenario should not happen, but were it to happen, it would not generate an NPE exception.
    @Test
    public void entityAlreadyExistsIfAccountIsSomehowNullIsGenericConstraintViolation() {
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, null);
        assertEquals(ConstraintViolationException.class, result.getClass());
    }
    
    @Test
    public void constraintViolationException() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "This is a generic constraint violation.", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(ConstraintViolationException.class, result.getClass());
        assertEquals("This is a generic constraint violation.", result.getMessage());
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, account);
        assertEquals(ConcurrentModificationException.class, result.getClass());
        assertEquals("Account has the wrong version number; it may have been saved in the background.", result.getMessage());
    }
    
}