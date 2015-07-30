package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudyConsent1.class);
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
        assertNotNull(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getActiveConsent(new StudyIdentifierImpl(consent1.getStudyKey())));
        
        // Make version1 active
        StudyConsent consent = studyConsentDao.publish(consent1);
        assertConsentsEqual(consent1, consent, true);
        
        datetime = DateUtils.getCurrentDateTime();
        
        // Add version 2
        final StudyConsent consent2 = studyConsentDao.addConsent(STUDY_ID, STUDY_ID.getIdentifier()+"."+datetime.getMillis(), datetime);
        assertNotNull(consent2);
        
        // The most recent consent should be version 2
        consent = studyConsentDao.getMostRecentConsent(STUDY_ID);
        assertConsentsEqual(consent2, consent, false);

        // The active consent is still version 1
        consent = studyConsentDao.getActiveConsent(STUDY_ID);
        assertConsentsEqual(consent1, consent, true);
        
        // Now make consent 2 the active consent. It should be retrieved by active consent call
        studyConsentDao.publish(consent2);
        consent = studyConsentDao.getActiveConsent(STUDY_ID);
        assertConsentsEqual(consent2, consent, true);
        
        // And by the way, it's still also the most recent
        consent = studyConsentDao.getMostRecentConsent(STUDY_ID);
        assertConsentsEqual(consent2, consent, true);
        
        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(STUDY_ID, consent1.getCreatedOn());
        assertConsentsEqual(consent1, consent, false);
        
        // Add a third consent to test list of consents
        datetime = DateUtils.getCurrentDateTime();
        final StudyConsent consent3 = studyConsentDao.addConsent(STUDY_ID, STUDY_ID.getIdentifier()+"."+datetime.getMillis(), datetime);
        
        // Get all consents. Should return in reverse order
        List<StudyConsent> all = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, all.size());
        assertConsentsEqual(consent3, all.get(0), false);
        assertConsentsEqual(consent2, all.get(1), true);
        assertConsentsEqual(consent1, all.get(2), false);
    }
    
    @Test
    public void crudStudyConsentWithS3Content() throws Exception {
        DateTime createdOn = DateTime.now();
        String key = STUDY_ID.getIdentifier() + "." + createdOn.getMillis();
        StudyConsent consent = studyConsentDao.addConsent(STUDY_ID, key, createdOn);
        assertNotNull(consent);
        assertFalse(consent.getActive());
        assertNull(studyConsentDao.getActiveConsent(new StudyIdentifierImpl(consent.getStudyKey())));

        // Now activate the consent
        consent = studyConsentDao.publish(consent);
        StudyConsent newConsent = studyConsentDao.getActiveConsent(new StudyIdentifierImpl(consent.getStudyKey()));
        assertConsentsEqual(consent, newConsent, true);
        assertEquals(key, newConsent.getStoragePath());
    }
    
    @Test
    public void activateConsentActivatesOnlyOneVersion() {
        // Add a consent, activate it, add a consent, activate it, etc.
        for (int i=0; i < 3; i++) {
            DateTime createdOn = DateTime.now();
            String key = STUDY_ID.getIdentifier() + "." + createdOn.getMillis();
            
            StudyConsent consent = studyConsentDao.addConsent(STUDY_ID, key, createdOn);
            studyConsentDao.publish(consent);
        }
        
        // Only one should be active.
        List<StudyConsent> allConsents = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, allConsents.size());
        assertEquals(true, allConsents.get(0).getActive());
        assertEquals(false, allConsents.get(1).getActive());
        assertEquals(false, allConsents.get(2).getActive());
        
        // Now move the active flag.
        studyConsentDao.publish(allConsents.get(2));
        allConsents = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, allConsents.size());
        assertEquals(false, allConsents.get(0).getActive());
        assertEquals(false, allConsents.get(1).getActive());
        assertEquals(true, allConsents.get(2).getActive());
        
        // Re-activating the same one changes nothing
        studyConsentDao.publish(allConsents.get(2));
        allConsents = studyConsentDao.getConsents(STUDY_ID);
        assertEquals(3, allConsents.size());
        assertEquals(false, allConsents.get(0).getActive());
        assertEquals(false, allConsents.get(1).getActive());
        assertEquals(true, allConsents.get(2).getActive());
    }
    
    private void assertConsentsEqual(StudyConsent existing, StudyConsent newOne, boolean isActive) {
        assertEquals(isActive, newOne.getActive());
        assertEquals(existing.getStudyKey(), newOne.getStudyKey());
        assertEquals(existing.getStoragePath(), newOne.getStoragePath());
        assertTrue(newOne.getCreatedOn() > 0);
    }
    
}