package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.JedisOps;
import org.sagebionetworks.bridge.redis.RedisKey;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class StudyEnrollmentServiceTest {
    
    private static final String NUM_PARTICIPANTS_KEY = RedisKey.NUM_OF_PARTICIPANTS.getRedisKey("test");
    
    @Resource
    private StudyEnrollmentService studyEnrollmentService;
    
    @Resource
    private ConsentService userConsentService;
    
    @Resource
    private StudyService studyService;
    
    @Resource
    private JedisOps jedisOps;

    @Resource
    private SubpopulationService subpopService;
    
    @Resource
    private TestUserAdminHelper helper;

    @Before
    public void before() {
        jedisOps.del(NUM_PARTICIPANTS_KEY);
        assertNull(jedisOps.get(NUM_PARTICIPANTS_KEY));
    }
    
    @After
    public void after() {
        jedisOps.del(NUM_PARTICIPANTS_KEY);
    }
    
    @Test
    public void enforcesStudyEnrollmentLimit() {
        User user = new User();
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study.setIdentifier("test");
        study.setMaxNumOfParticipants(2);

        boolean limit = studyEnrollmentService.isStudyAtEnrollmentLimit(study);
        assertFalse("No limit reached", limit);
        studyEnrollmentService.incrementStudyEnrollment(study, user);
        studyEnrollmentService.incrementStudyEnrollment(study, user);
        assertTrue("Limit reached", studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("2", jedisOps.get(NUM_PARTICIPANTS_KEY));
    }
    
    @Test
    public void studyEnrollmentNotIncrementedOnSubsequentConsents() {
        User user = new User();
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study.setIdentifier("test");
        study.setMaxNumOfParticipants(2);

        studyEnrollmentService.incrementStudyEnrollment(study, user);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        
        ConsentStatus consent2 = new ConsentStatus.Builder().withName("Consent")
                .withGuid(SubpopulationGuid.create("guid2")).withConsented(true).withRequired(true)
                .withSignedMostRecentConsent(true).build();
        // On subsequent consents, user is not added and enrollment is not increased
        
        Map<SubpopulationGuid, ConsentStatus> map = TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT, consent2);
        user.setConsentStatuses(map);
        studyEnrollmentService.incrementStudyEnrollment(study, user);
        studyEnrollmentService.incrementStudyEnrollment(study, user);
        studyEnrollmentService.incrementStudyEnrollment(study, user);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("1", jedisOps.get(NUM_PARTICIPANTS_KEY));
    }
    
    @Test
    public void decrementingStudyWorks() {
        User user = new User();
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_UNSIGNED));
        
        jedisOps.del(NUM_PARTICIPANTS_KEY);
        jedisOps.setnx(NUM_PARTICIPANTS_KEY, "2");
        
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study.setIdentifier("test");
        study.setMaxNumOfParticipants(2);

        studyEnrollmentService.decrementStudyEnrollment(study, user);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        assertFalse(studyEnrollmentService.isStudyAtEnrollmentLimit(study));
        assertEquals("0", jedisOps.get(NUM_PARTICIPANTS_KEY));
    }
    
    @Test
    public void studyEnrollmentNotDecrementedUntilLastWithdrawal() {
        User user = new User();
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        
        jedisOps.del(NUM_PARTICIPANTS_KEY);
        jedisOps.setnx(NUM_PARTICIPANTS_KEY, "2");
        
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study.setIdentifier("test");
        study.setMaxNumOfParticipants(2);

        // With a signed consent, this does not decrement, because user is still in study
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        assertEquals("2", jedisOps.get(NUM_PARTICIPANTS_KEY));
        
        // With no signed consents, this will decrement.
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_UNSIGNED));
        studyEnrollmentService.decrementStudyEnrollment(study, user);
        assertEquals("1", jedisOps.get(NUM_PARTICIPANTS_KEY));
    }
    
    @Test
    public void getNumberOfParticipants() {
        Study study = TestUtils.getValidStudy(ConsentServiceImplTest.class);
        study = studyService.createStudy(study);
        
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Group 1");
        subpopService.createSubpopulation(study, subpop1);
        
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Group 2");
        subpopService.createSubpopulation(study, subpop2);
        
        TestUserAdminHelper.Builder builder = helper.getBuilder(StudyServiceImplTest.class).withStudy(study)
                .withConsent(true);
        
        TestUser user1 = builder.withGuid(subpop1.getGuid()).build();
        TestUser user2 = builder.withGuid(subpop1.getGuid()).build();
        TestUser user3 = builder.withGuid(subpop1.getGuid()).build();
        // and user2 is also in the other subpop2, but will still be counted correctly.
        userConsentService.consentToResearch(study, subpop2.getGuid(), user2.getUser(), 
                new ConsentSignature.Builder().withBirthdate("1980-01-01").withName("Name").build(), SharingScope.NO_SHARING, false);
        try {

            long count = studyEnrollmentService.getNumberOfParticipants(study);
            assertEquals(3, count);
            
        } finally {
            helper.deleteUser(user1);
            helper.deleteUser(user2);
            helper.deleteUser(user3);
            studyService.deleteStudy(study.getIdentifier());
        }
    }

}
