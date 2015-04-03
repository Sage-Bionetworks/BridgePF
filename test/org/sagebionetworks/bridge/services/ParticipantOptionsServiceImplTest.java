package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.junit.Assert.assertEquals;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoParticipantOptions;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.OptionLookup;
import org.sagebionetworks.bridge.models.studies.Study;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class ParticipantOptionsServiceImplTest {

    @Resource
    private ParticipantOptionsService optionsService;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private TestUserAdminHelper helper;
    
    private TestUser testUser;
    
    private Study study;
    
    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoParticipantOptions.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoParticipantOptions.class);
    }

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        testUser = helper.createUser(ParticipantOptionsServiceImplTest.class);
    }

    @After
    public void after() {
        if (testUser != null) {
            helper.deleteUser(study, testUser.getEmail());
        }
    }
    
    @Test
    public void canCrudSharingScopeOption() {
        optionsService.setOption(testUser.getStudyIdentifier(), testUser.getUser().getHealthCode(),
                SharingScope.ALL_QUALIFIED_RESEARCHERS);
        
        String value = optionsService.getOption(testUser.getUser().getHealthCode(), ParticipantOption.SHARING_SCOPE);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), value);
        
        SharingScope sharing = optionsService.getSharingScope(testUser.getUser().getHealthCode());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, sharing);
        
        // After deletion, the default value is no sharing
        optionsService.deleteOption(testUser.getUser().getHealthCode(), ParticipantOption.SHARING_SCOPE);
        sharing = optionsService.getSharingScope(testUser.getUser().getHealthCode());
        assertEquals(SharingScope.NO_SHARING, sharing);
    }
    
    @Test
    public void getDefaultValeFromSingleLookup() {
        String value = optionsService.getOption(testUser.getUser().getHealthCode(), ParticipantOption.EMAIL_NOTIFICATIONS);
        assertEquals("true", value);
    }
    
    @Test
    public void getDefaultValeFromBulkLookup() {
        OptionLookup lookup = optionsService.getOptionForAllStudyParticipants(testUser.getStudyIdentifier(), ParticipantOption.EMAIL_NOTIFICATIONS);
        assertEquals("true", lookup.get(testUser.getUser().getHealthCode()));
        
        assertEquals("true", lookup.get("AAA")); // any value returns the default, not just actual values in map.
    }
    
}
