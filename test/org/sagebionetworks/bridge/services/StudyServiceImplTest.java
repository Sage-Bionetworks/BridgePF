package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.Study2;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.stormpath.sdk.client.Client;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyServiceImplTest {

    @Resource
    StudyServiceImpl studyService;
    
    @Resource
    Client stormpathClient;
    
    private Study2 study;
    
    @After
    public void after() {
        try {
            studyService.deleteStudy(study.getIdentifier());
        } catch(Throwable t) {
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
        study = new DynamoStudy();
        study.setIdentifier("belgium");
        study.setName("Belgium Waffles [Test]");
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
        study = new DynamoStudy();
        study.setIdentifier("sage-test");
        study.setName("Test of study creation");
        study.setMaxParticipants(100);
        study.setMinAgeOfConsent(18);
        study.setResearcherRole("foo_manchu");
        study.setStormpathUrl(Environment.LOCAL, "crap");
        study.setStormpathUrl(Environment.DEV, "crap");
        
        study = studyService.createStudy(study);
        assertNotNull("Version has been set", study.getVersion());
        
        study = studyService.getStudy2ByIdentifier("sage-test");
        assertEquals("sage-test", study.getIdentifier());
        assertEquals("Test of study creation", study.getName());
        assertEquals(100, study.getMaxParticipants());
        assertEquals(18, study.getMinAgeOfConsent());
        // yeah this is always set a specific string, you can't edit it.
        assertEquals("sage-test_researcher", study.getResearcherRole()); 
        assertNotEquals("crap", study.getStormpathUrl());

        studyService.deleteStudy(study.getIdentifier());
        try {
            studyService.getStudy2ByIdentifier(study.getIdentifier());
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void canChangeStudyName() {
        study = new DynamoStudy();
        study.setIdentifier("aaa");
        study.setName("Test of identifier changes");
        studyService.createStudy(study);
        
        studyService.changeStudyId(study.getIdentifier(), "ccc");
        
        study = studyService.getStudy2ByIdentifier("ccc");
        try {
            studyService.getStudy2ByIdentifier("aaa");
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
    }
    
}
