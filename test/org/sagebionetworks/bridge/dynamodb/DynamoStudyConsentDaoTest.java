package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyConsentDaoTest {
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("fake-study");
    
    @Resource
    private DynamoStudyConsentDao studyConsentDao;
    
    private List<StudyConsent> toDelete;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudyConsent1.class);
        toDelete = new ArrayList<StudyConsent>();
    }

    @After
    public void after() {
        studyConsentDao.deleteAllConsents(STUDY_ID);

        assertEquals(0, studyConsentDao.getConsents(STUDY_ID).size());
    }

    @Test
    public void crudStudyConsentWithFileBasedContent() {
        DateTime datetime = DateUtils.getCurrentDateTime();
        
        // Add consent version 1, inactive
        final StudyConsent consent1 = studyConsentDao.addConsent(STUDY_ID, STUDY_ID.getIdentifier()+"."+datetime.getMillis(), datetime);
        toDelete.add(consent1);
        assertNotNull(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getConsent(new StudyIdentifierImpl(consent1.getStudyKey())));
        
        // Make version1 active
        StudyConsent consent = studyConsentDao.activate(consent1);
        assertTrue(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getStoragePath(), consent.getStoragePath());
        assertTrue(consent.getCreatedOn() > 0);
        
        datetime = DateUtils.getCurrentDateTime();
        
        // Add version 2
        final StudyConsent consent2 = studyConsentDao.addConsent(STUDY_ID, STUDY_ID.getIdentifier()+"."+datetime.getMillis(), datetime);
        toDelete.add(consent2);
        assertNotNull(consent2);
        studyConsentDao.activate(consent2);
        
        // The latest should be version 2
        consent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey()));
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getStoragePath(), consent.getStoragePath());
        
        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent1.getStudyKey()), consent1.getCreatedOn());
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getStoragePath(), consent.getStoragePath());
        
        datetime = DateUtils.getCurrentDateTime();
        
        // All consents
        final StudyConsent consent3 = studyConsentDao.addConsent(STUDY_ID, STUDY_ID.getIdentifier()+"."+datetime.getMillis(), datetime);
        toDelete.add(consent3);

        List<StudyConsent> all = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, all.size());
        // In reverse order
        consent = all.get(0);
        assertFalse(consent.getActive());
        assertEquals(consent3.getStudyKey(), consent.getStudyKey());
        assertEquals(consent3.getStoragePath(), consent.getStoragePath());
        consent = all.get(1);
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getStoragePath(), consent.getStoragePath());
        consent = all.get(2);
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getStoragePath(), consent.getStoragePath());
    }
    
    @Test
    public void crudStudyConsentWithS3Content() throws Exception {
        DateTime createdOn = DateTime.now();
        String key = STUDY_ID.getIdentifier() + "." + createdOn.getMillis();
        StudyConsent consent = studyConsentDao.addConsent(STUDY_ID, key, createdOn);
        assertNotNull("1", consent);
        toDelete.add(consent);
        assertFalse("2", consent.getActive());
        assertNull("3", studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey())));
        
        // Now activate the consent
        consent = studyConsentDao.activate(consent);
        StudyConsent newConsent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey()));
        assertTrue("4", newConsent.getActive());
        assertEquals("5", consent.getStudyKey(), newConsent.getStudyKey());
        assertEquals("6", key, consent.getStoragePath());
        assertEquals("7", createdOn.getMillis(), newConsent.getCreatedOn());
        
        List<StudyConsent> all = studyConsentDao.getConsents(STUDY_ID);
        assertEquals("8", 1, all.size());
        // In reverse order
        assertEquals("9", consent, all.get(0));
    }
    
    @Test
    public void activateConsentActivatesOnlyOneVersion() {
        // Add a consent, activate it, add a consent, activate it, etc.
        for (int i=0; i < 3; i++) {
            DateTime createdOn = DateTime.now();
            String key = STUDY_ID.getIdentifier() + "." + createdOn.getMillis();
            
            StudyConsent consent = studyConsentDao.addConsent(STUDY_ID, key, createdOn);
            studyConsentDao.activate(consent);
        }
        
        // Only one should be active.
        List<StudyConsent> allConsents = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, allConsents.size());
        assertEquals(true, allConsents.get(0).getActive());
        assertEquals(false, allConsents.get(1).getActive());
        assertEquals(false, allConsents.get(2).getActive());
    }
    
}