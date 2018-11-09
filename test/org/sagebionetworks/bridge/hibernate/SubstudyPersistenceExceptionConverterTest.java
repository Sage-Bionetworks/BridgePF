package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;

public class SubstudyPersistenceExceptionConverterTest {
    
    private SubstudyPersistenceExceptionConverter converter;
    
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

}
