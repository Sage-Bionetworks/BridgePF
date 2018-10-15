package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@RunWith(MockitoJUnitRunner.class)
public class AccountPersistenceExceptionConverterTest {

    private AccountPersistenceExceptionConverter converter;
    
    @Mock
    private AccountDao accountDao;
    
    @Before
    public void before() {
        converter = new AccountPersistenceExceptionConverter(accountDao);
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
        account.setEmail(TestConstants.EMAIL);
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(new StudyIdentifierImpl("testStudy"));
        existing.setEmail(TestConstants.EMAIL);
        
        when(accountDao.getAccount(AccountId.forEmail("testStudy", TestConstants.EMAIL))).thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+TestConstants.EMAIL+"' for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("Email address has already been used by another account.", result.getMessage());
        assertEquals("userId", ((EntityAlreadyExistsException)result).getEntityKeys().get("userId"));
    }
    
    @Test
    public void entityAlreadyExistsForPhone() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setPhone(TestConstants.PHONE);
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(new StudyIdentifierImpl("testStudy"));
        existing.setPhone(TestConstants.PHONE);
        
        when(accountDao.getAccount(AccountId.forPhone("testStudy", TestConstants.PHONE))).thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-"+TestConstants.PHONE.getNationalFormat()+"' for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("Phone number has already been used by another account.", result.getMessage());
        assertEquals("userId", ((EntityAlreadyExistsException)result).getEntityKeys().get("userId"));
    }

    @Test
    public void entityAlreadyExistsForExternalId() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setExternalId("ext");
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(new StudyIdentifierImpl("testStudy"));
        existing.setExternalId("ext");
        
        when(accountDao.getAccount(AccountId.forExternalId("testStudy", "ext"))).thenReturn(existing);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(EntityAlreadyExistsException.class, result.getClass());
        assertEquals("External ID has already been used by another account.", result.getMessage());
        assertEquals("userId", ((EntityAlreadyExistsException)result).getEntityKeys().get("userId"));
    }

    // This should not happen, we're testing that not finding an account with this message doesn't break the converter.
    @Test
    public void entityAlreadyExistsIfAccountCannotBeFound() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId("testStudy");
        account.setExternalId("ext");
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(ConstraintViolationException.class, result.getClass());
        assertEquals("Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", result.getMessage());
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