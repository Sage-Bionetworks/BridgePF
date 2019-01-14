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
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;

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
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setEmail(TestConstants.EMAIL);
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setEmail(TestConstants.EMAIL);
        
        when(accountDao.getAccount(AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.EMAIL)))
                .thenReturn(existing);
        
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
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setPhone(TestConstants.PHONE);
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setPhone(TestConstants.PHONE);
        
        when(accountDao.getAccount(AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE)))
                .thenReturn(existing);
        
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
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setExternalId("ext");
        
        Account existing = Account.create();
        existing.setId("userId");
        existing.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setExternalId("ext");
        
        when(accountDao.getAccount(AccountId.forExternalId(TestConstants.TEST_STUDY_IDENTIFIER, "ext"))).thenReturn(existing);
        
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
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setExternalId("ext");
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "Duplicate entry 'testStudy-ext' for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        // It is converted to a generic constraint violation exception
        RuntimeException result = converter.convert(pe, account);
        assertEquals(ConstraintViolationException.class, result.getClass());
        assertEquals("Accounts table constraint prevented save or update.", result.getMessage());
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
    public void constraintViolationExceptionMessageIsHidden() {
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        org.hibernate.exception.ConstraintViolationException cve = new org.hibernate.exception.ConstraintViolationException(
                "This is a generic constraint violation.", null, null);
        PersistenceException pe = new PersistenceException(cve);
        
        RuntimeException result = converter.convert(pe, account);
        assertEquals(ConstraintViolationException.class, result.getClass());
        assertEquals("Accounts table constraint prevented save or update.", result.getMessage());
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateAccount account = new HibernateAccount();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, account);
        assertEquals(ConcurrentModificationException.class, result.getClass());
        assertEquals("Account has the wrong version number; it may have been saved in the background.", result.getMessage());
    }
    
}