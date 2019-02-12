package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

@RunWith(MockitoJUnitRunner.class)
public class SubstudyPersistenceExceptionConverterTest {
    
    private SubstudyPersistenceExceptionConverter converter;
    
    @Mock
    private ConstraintViolationException cve;
    
    @Before
    public void before() {
        this.converter = new SubstudyPersistenceExceptionConverter();
    }
    
    @Test
    public void noConversion() { 
        PersistenceException ex = new PersistenceException(new RuntimeException("message"));
        
        assertSame(ex, converter.convert(ex, null));
    }
    
    @Test
    public void optimisticLockException() { 
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        
        OptimisticLockException ole = new OptimisticLockException();
        
        RuntimeException result = converter.convert(ole, substudy);
        assertEquals(ConcurrentModificationException.class, result.getClass());
        assertEquals("Substudy has the wrong version number; it may have been saved in the background.", result.getMessage());
    }
    
    @Test
    public void genericConstraintViolationException() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        PersistenceException ex = new PersistenceException(cve);
        when(cve.getMessage()).thenReturn("This is some generic constraint violation message");

        RuntimeException result = converter.convert(ex, substudy);

        assertEquals(org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class, result.getClass());
        assertEquals("Substudy table constraint prevented save or update.", result.getMessage());
    }
    
    @Test
    public void usedByAccountsConstraintViolationException() {
        HibernateSubstudy substudy = new HibernateSubstudy();
        substudy.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);

        PersistenceException ex = new PersistenceException(cve);
        when(cve.getMessage()).thenReturn("abc a foreign key constraint fails abc REFERENCES `Substudies`abc");

        RuntimeException result = converter.convert(ex, substudy);

        assertEquals(org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class, result.getClass());
        assertEquals("Substudy cannot be deleted, it is referenced by an account", result.getMessage());
    }
}
