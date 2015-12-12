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
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuidImpl;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyConsentDaoTest {
    
    private static final SubpopulationGuid SUBPOP_GUID = new SubpopulationGuidImpl("ABC");
    
    @Resource
    private DynamoStudyConsentDao studyConsentDao;

    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudyConsent1.class);
    }

    @After
    public void after() {
        studyConsentDao.deleteAllConsents(SUBPOP_GUID);

        assertEquals(0, studyConsentDao.getConsents(SUBPOP_GUID).size());
    }

    @Test
    public void crudStudyConsentWithFileBasedContent() {
        DateTime datetime = DateUtils.getCurrentDateTime();
        
        // Add consent version 1, inactive
        final StudyConsent consent1 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+datetime.getMillis(), datetime);
        assertNotNull(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getActiveConsent(SUBPOP_GUID));
        
        // Make version1 active
        StudyConsent consent = studyConsentDao.publish(consent1);
        assertConsentsEqual(consent1, consent, true);
        
        datetime = DateUtils.getCurrentDateTime();
        
        // Add version 2
        final StudyConsent consent2 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+datetime.getMillis(), datetime);
        assertNotNull(consent2);
        
        // The most recent consent should be version 2
        consent = studyConsentDao.getMostRecentConsent(SUBPOP_GUID);
        assertConsentsEqual(consent2, consent, false);

        // The active consent is still version 1
        consent = studyConsentDao.getActiveConsent(SUBPOP_GUID);
        assertConsentsEqual(consent1, consent, true);
        
        // Now make consent 2 the active consent. It should be retrieved by active consent call
        studyConsentDao.publish(consent2);
        consent = studyConsentDao.getActiveConsent(SUBPOP_GUID);
        assertConsentsEqual(consent2, consent, true);
        
        // And by the way, it's still also the most recent
        consent = studyConsentDao.getMostRecentConsent(SUBPOP_GUID);
        assertConsentsEqual(consent2, consent, true);
        
        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(SUBPOP_GUID, consent1.getCreatedOn());
        assertConsentsEqual(consent1, consent, false);
        
        // Add a third consent to test list of consents
        datetime = DateUtils.getCurrentDateTime();
        final StudyConsent consent3 = studyConsentDao.addConsent(SUBPOP_GUID, SUBPOP_GUID+"."+datetime.getMillis(), datetime);
        
        // Get all consents. Should return in reverse order
        List<StudyConsent> all = studyConsentDao.getConsents(SUBPOP_GUID);
        assertEquals(3, all.size());
        assertConsentsEqual(consent3, all.get(0), false);
        assertConsentsEqual(consent2, all.get(1), true);
        assertConsentsEqual(consent1, all.get(2), false);
    }
    
    @Test
    public void crudStudyConsentWithS3Content() throws Exception {
        DateTime createdOn = DateTime.now();
        String key = SUBPOP_GUID + "." + createdOn.getMillis();
        StudyConsent consent = studyConsentDao.addConsent(SUBPOP_GUID, key, createdOn);
        assertNotNull(consent);
        assertFalse(consent.getActive());
        assertNull(studyConsentDao.getActiveConsent(SUBPOP_GUID));

        // Now activate the consent
        consent = studyConsentDao.publish(consent);
        StudyConsent newConsent = studyConsentDao.getActiveConsent(SUBPOP_GUID);
        assertConsentsEqual(consent, newConsent, true);
        assertEquals(key, newConsent.getStoragePath());
    }
    
    @Test
    public void activateConsentActivatesOnlyOneVersion() {
        // Add a consent, activate it, add a consent, activate it, etc.
        for (int i=0; i < 3; i++) {
            DateTime createdOn = DateTime.now();
            String key = SUBPOP_GUID + "." + createdOn.getMillis();
            
            StudyConsent consent = studyConsentDao.addConsent(SUBPOP_GUID, key, createdOn);
            studyConsentDao.publish(consent);
        }
        
        // Only one should be active.
        List<StudyConsent> allConsents = studyConsentDao.getConsents(SUBPOP_GUID);
        assertEquals(3, allConsents.size());
        assertTrue(allConsents.get(0).getActive());
        assertFalse(allConsents.get(1).getActive());
        assertFalse(allConsents.get(2).getActive());
        
        // Now move the active flag.
        studyConsentDao.publish(allConsents.get(2));
        allConsents = studyConsentDao.getConsents(SUBPOP_GUID);
        assertEquals(3, allConsents.size());
        assertFalse(allConsents.get(0).getActive());
        assertFalse(allConsents.get(1).getActive());
        assertTrue(allConsents.get(2).getActive());
        
        // Re-activating the same one changes nothing
        studyConsentDao.publish(allConsents.get(2));
        allConsents = studyConsentDao.getConsents(SUBPOP_GUID);
        assertEquals(3, allConsents.size());
        assertFalse(allConsents.get(0).getActive());
        assertFalse(allConsents.get(1).getActive());
        assertTrue(allConsents.get(2).getActive());
    }
    
    private void assertConsentsEqual(StudyConsent existing, StudyConsent newOne, boolean isActive) {
        assertEquals(isActive, newOne.getActive());
        assertEquals(existing.getSubpopulationGuid(), newOne.getSubpopulationGuid());
        assertEquals(existing.getStoragePath(), newOne.getStoragePath());
        assertTrue(newOne.getCreatedOn() > 0);
    }
    
}