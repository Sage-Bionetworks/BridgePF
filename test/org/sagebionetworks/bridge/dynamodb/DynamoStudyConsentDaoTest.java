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
        for (StudyConsent sc : toDelete) {
            studyConsentDao.deleteConsent(new StudyIdentifierImpl(sc.getStudyKey()), sc.getCreatedOn());
        }
        toDelete.clear();
    }

    @Test
    public void crudStudyConsentWithFileBasedContent() {
        StudyIdentifier studyId = new StudyIdentifierImpl("fake-study");
        
        // Add consent version 1, inactive
        final StudyConsent consent1 = studyConsentDao.addConsent(studyId, "fake-path1", null, DateUtils.getCurrentDateTime());
        assertNotNull(consent1);
        toDelete.add(consent1);
        assertFalse(consent1.getActive());
        assertNull(studyConsentDao.getConsent(new StudyIdentifierImpl(consent1.getStudyKey())));
        // Make version1 active
        studyConsentDao.activate(consent1);
        StudyConsent consent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent1.getStudyKey()));
        assertTrue(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        assertTrue(consent.getCreatedOn() > 0);
        // Add version 2
        final StudyConsent consent2 = studyConsentDao.addConsent(studyId, "fake-path2", null, DateUtils.getCurrentDateTime());
        assertNotNull(consent2);
        toDelete.add(consent2);
        studyConsentDao.deactivate(consent1);
        studyConsentDao.activate(consent2);
        // The latest should be version 2
        consent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey()));
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getPath(), consent.getPath());
        // Can still get version 1 using its timestamp
        consent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent1.getStudyKey()), consent1.getCreatedOn());
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        // All consents
        final StudyConsent consent3 = studyConsentDao.addConsent(studyId, "fake-path3", null, DateUtils.getCurrentDateTime());
        assertNotNull(consent3);
        toDelete.add(consent3);
        List<StudyConsent> all = studyConsentDao.getConsents(studyId);
        assertEquals(3, all.size());
        // In reverse order
        consent = all.get(0);
        assertFalse(consent.getActive());
        assertEquals(consent3.getStudyKey(), consent.getStudyKey());
        assertEquals(consent3.getPath(), consent.getPath());
        assertEquals(consent3.getStoragePath(), consent.getStoragePath());
        consent = all.get(1);
        assertTrue(consent.getActive());
        assertEquals(consent2.getStudyKey(), consent.getStudyKey());
        assertEquals(consent2.getPath(), consent.getPath());
        assertEquals(consent2.getStoragePath(), consent.getStoragePath());
        consent = all.get(2);
        assertFalse(consent.getActive());
        assertEquals(consent1.getStudyKey(), consent.getStudyKey());
        assertEquals(consent1.getPath(), consent.getPath());
        assertEquals(consent1.getStoragePath(), consent.getStoragePath());
        // Delete all consents
        for (StudyConsent aConsent : all) {
            studyConsentDao.deleteConsent(new StudyIdentifierImpl(aConsent.getStudyKey()), aConsent.getCreatedOn());
        }
        all = studyConsentDao.getConsents(studyId);
        assertTrue(all.isEmpty());
    }
    
    @Test
    public void crudStudyConsentWithS3Content() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("fake-study");
        DateTime createdOn = DateTime.now();
        String key = "fake-study." + createdOn.getMillis();
        StudyConsent consent = null;
        try {
            consent = studyConsentDao.addConsent(studyId, null, key, createdOn);
            assertNotNull(consent);
            toDelete.add(consent);
            assertFalse(consent.getActive());
            assertNull(studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey())));
            
            // Now activate the consent
            consent = studyConsentDao.activate(consent);
            StudyConsent newConsent = studyConsentDao.getConsent(new StudyIdentifierImpl(consent.getStudyKey()));
            assertTrue(newConsent.getActive());
            assertEquals(consent.getStudyKey(), newConsent.getStudyKey());
            assertNull(consent.getPath());
            assertEquals(key, consent.getStoragePath());
            assertEquals(createdOn.getMillis(), newConsent.getCreatedOn());
            
            List<StudyConsent> all = studyConsentDao.getConsents(studyId);
            assertEquals(1, all.size());
            // In reverse order
            assertEquals(consent, all.get(0));
        } finally {
            studyConsentDao.deleteConsent(new StudyIdentifierImpl(consent.getStudyKey()), consent.getCreatedOn());
            
            List<StudyConsent> all = studyConsentDao.getConsents(studyId);
            assertTrue(all.isEmpty());
        }
    }
    
    @Test
    public void activateConsentActivatesOnlyOneVersion() {
        StudyIdentifier studyId = new StudyIdentifierImpl("fake-study");
        try {
            // Add a consent, activate it, add a consent, activate it, etc.
            for (int i=0; i < 3; i++) {
                DateTime createdOn = DateTime.now();
                String key = "fake-study." + createdOn.getMillis();
                
                StudyConsent consent = studyConsentDao.addConsent(studyId, null, key, createdOn);
                studyConsentDao.activate(consent);
            }
            
            // Only one should be active.
            List<StudyConsent> allConsents = studyConsentDao.getConsents(studyId);
            assertEquals(3, allConsents.size());
            assertEquals(true, allConsents.get(0).getActive());
            assertEquals(false, allConsents.get(1).getActive());
            assertEquals(false, allConsents.get(2).getActive());
        } finally {
            List<StudyConsent> allConsents = studyConsentDao.getConsents(studyId);
            for (StudyConsent consent : allConsents) {
                studyConsentDao.deleteConsent(studyId, consent.getCreatedOn());
            }
        }
    }
    
}