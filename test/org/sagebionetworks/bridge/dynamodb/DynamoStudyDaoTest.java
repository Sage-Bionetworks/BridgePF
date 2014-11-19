package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.Study2;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Lists;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoStudyDaoTest {

    @Resource
    DynamoStudyDao studyDao;
    
    @Before
    public void before() {
        DynamoInitializer.init(DynamoStudy.class);
        DynamoTestUtil.clearTable(DynamoStudy.class);
    }
    
    @Test
    public void crudOneStudy() {
        Study2 study = createStudy();
        
        study = studyDao.createStudy(study);
        assertNotNull("Study was assigned a version", study.getVersion());
        
        study.setName("This is a test name");
        study.setMaxParticipants(10);
        study = studyDao.updateStudy(study);
        
        study = studyDao.getStudy(study.getIdentifier());
        assertEquals("Name was set", "This is a test name", study.getName());
        assertEquals("Max participants was set", 10, study.getMaxParticipants());
        assertNotNull("Study deployment was set", study.getStormpathHref());
        assertNotNull("Study hostname was set", study.getHostname());
        assertTrue("Contains tracker", study.getTrackerIdentifiers().contains("sage:bp"));
        
        String identifier = study.getIdentifier();
        studyDao.deleteStudy(study.getIdentifier());
        try {
            study = studyDao.getStudy(identifier);
            fail("Should have thrown EntityNotFoundException");
        } catch(EntityNotFoundException e) {
        }
    }
    
    @Test
    public void canRetrieveAllStudies() {
        List<String> identifiers = Lists.newArrayList();
        try {
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            identifiers.add(studyDao.createStudy(createStudy()).getIdentifier());
            
            List<Study2> studies = studyDao.getStudies();
            assertEquals("There are five studies", 5, studies.size());
            
        } finally {
            for (String identifier : identifiers) {
                studyDao.deleteStudy(identifier);
            }
        }
        List<Study2> studies = studyDao.getStudies();
        assertEquals("There are no studies", 0, studies.size());
    }
    
    @Test
    public void willNotSaveTwoStudiesWithSameIdentifier() {
        Study2 study = null;
        try {
            study = createStudy();
            study = studyDao.createStudy(study);
            study.setVersion(null);
            studyDao.createStudy(study);
            fail("Should have thrown entity exists exception");
        } catch(EntityAlreadyExistsException e) {
            
        } finally {
            studyDao.deleteStudy(study.getIdentifier());
        }
    }
    
    @Test(expected=EntityAlreadyExistsException.class)
    public void identifierUniquenessEnforcedByVersionChecks() throws Exception {
        Study2 study = createStudy();
        studyDao.createStudy(study);
        
        study.setVersion(null); // This is now a "new study"
        studyDao.createStudy(study);
    }
    
    private Study2 createStudy() {
        Study2 study = new DynamoStudy();
        study.setIdentifier(TestUtils.randomName());
        study.setMaxParticipants(100);
        study.setMinAgeOfConsent(18);
        study.setName(TestUtils.randomName());
        study.setResearcherRole("researcher");
        study.getTrackerIdentifiers().add("sage:med");
        study.getTrackerIdentifiers().add("sage:bp");
        study.setHostname("test.sagebridge.org");
        study.setStormpathHref("http://test/local/");
        return study;
    }
    
}
