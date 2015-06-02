package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceImplTest {

    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    StudyConsentServiceImpl studyConsentService;
    
    private CacheProvider cache;
    
    private Study study;
    
    private String identifier;
    
    @Before
    public void before() {
        cache = mock(CacheProvider.class);
        studyService.setCacheProvider(cache);
    }
    
    @After
    public void after() {
        if (identifier != null) {
            studyService.deleteStudy(identifier);
        }
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidIdentifier() {
        study = new DynamoStudy();
        study.setName("Belgian Waffles [Test]");
        study = studyService.createStudy(study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidName() {
        study = new DynamoStudy();
        study.setIdentifier("belgium");
        study = studyService.createStudy(study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void invalidIdentifierOfOnlyOneLetter() {
        study = new DynamoStudy();
        study.setIdentifier("a");
        study = studyService.createStudy(study);
    }
    
    @Test
    public void cannotCreateAnExistingStudyWithAVersion() {
        identifier = TestUtils.randomName();
        study = new DynamoStudy();
        study.setIdentifier(identifier);
        study.setName("Belgium Waffles [Test]");
        study.setSupportEmail("bridge-testing@sagebase.org");
        study = studyService.createStudy(study);
        try {
            study = studyService.createStudy(study);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void cannotCreateAStudyWithAVersion() {
        study = new DynamoStudy();
        study.setIdentifier("sage-test");
        study.setName("Test of study creation");
        study.setVersion(1L);
        study = studyService.createStudy(study);
    }
    
    @Test
    public void crudStudy() {
        identifier = TestUtils.randomName();
        study = createStudy();

        study = studyService.createStudy(study);
        assertNotNull("Version has been set", study.getVersion());
        verify(cache).setStudy(study);
        verifyNoMoreInteractions(cache);
        reset(cache);
        
        // A default, active consent should be created for the study.
        StudyConsentView view = studyConsentService.getActiveConsent(study.getStudyIdentifier());
        assertTrue(view.getDocumentContent().contains(BridgeConstants.BRIDGE_DEFAULT_CONSENT_DOCUMENT));
        assertTrue(view.getActive());
        
        study = studyService.getStudy(identifier);
        assertEquals(identifier, study.getIdentifier());
        assertEquals("Test of study creation", study.getName());
        assertEquals(100, study.getMaxNumOfParticipants());
        assertEquals(18, study.getMinAgeOfConsent());
        // these should have been changed
        assertEquals(identifier+"_researcher", study.getResearcherRole());
        assertNotEquals("http://local-test-junk", study.getStormpathHref());
        verify(cache).getStudy(study.getIdentifier());
        verify(cache).setStudy(study);
        verifyNoMoreInteractions(cache);
        reset(cache);

        studyService.deleteStudy(identifier);
        verify(cache).removeStudy(study.getIdentifier());
        verifyNoMoreInteractions(cache);
        try {
            studyService.getStudy(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        identifier = null;
    }
    
    private Study createStudy() {
        study = new DynamoStudy();
        study.setIdentifier(identifier);
        study.setName("Test of study creation");
        study.setMaxNumOfParticipants(100);
        study.setMinAgeOfConsent(18);
        study.setResearcherRole(identifier+"_researcher");
        study.setStormpathHref("http://dev-test-junk");
        study.setSupportEmail("bridge-testing@sagebase.org");
        return study;
    }

}
