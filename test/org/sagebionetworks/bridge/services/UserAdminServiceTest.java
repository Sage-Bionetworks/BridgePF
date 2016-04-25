package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoExternalIdentifier;
import org.sagebionetworks.bridge.dynamodb.DynamoTestUtil;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent3;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.Lists;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class UserAdminServiceTest {

    // Decided not to use the helper class for this test because so many edge conditions are
    // being tested here.

    @Resource
    AuthenticationService authService;

    @Resource
    BridgeConfig bridgeConfig;

    @Resource
    StudyService studyService;

    @Resource
    UserAdminService userAdminService;
    
    @Resource
    ExternalIdService externalIdService;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    private Study study;

    private StudyParticipant participant;

    private User testUser;

    @BeforeClass
    public static void initialSetUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent3.class);
    }

    @AfterClass
    public static void finalCleanUp() {
        DynamoTestUtil.clearTable(DynamoUserConsent3.class);
    }

    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        study.setExternalIdValidationEnabled(true);
        String email = TestUtils.makeRandomTestEmail(UserAdminServiceTest.class);
        participant = new StudyParticipant.Builder().withEmail(email).withPassword("P4ssword!").build();

        SignIn signIn = new SignIn(bridgeConfig.getProperty("admin.email"), bridgeConfig.getProperty("admin.password"));
        authService.signIn(study, TEST_CONTEXT, signIn).getUser();
    }

    @After
    public void after() {
        if (testUser != null) {
            userAdminService.deleteUser(study, testUser.getId());
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void deletedUserHasBeenDeleted() {
        testUser = userAdminService.createUser(participant, study, null, true, true).getUser();

        userAdminService.deleteUser(study, testUser.getId());

        // This should fail with a 404.
        authService.signIn(study, TEST_CONTEXT, new SignIn(participant.getEmail(), participant.getPassword()));
    }

    @Test
    public void canCreateUserWithoutConsentingOrSigningUserIn() {
        UserSession session1 = userAdminService.createUser(participant, study, null, false, false);
        assertFalse(session1.isAuthenticated());

        UserSession session = authService.signIn(study, TEST_CONTEXT, new SignIn(participant.getEmail(),
                participant.getPassword()));
        testUser = session.getUser();
        assertFalse(testUser.doesConsent());
    }

    // Next two test the same thing in two different ways.
    public void cannotCreateTheSameUserTwice() {
        testUser = userAdminService.createUser(participant, study, null, true, true).getUser();
        testUser = userAdminService.createUser(participant, study, null, true, true).getUser();
    }
    
    @Test
    public void cannotCreateUserWithSameEmail() {
        testUser = userAdminService.createUser(participant, study, null, true, false).getUser();
        try {
            userAdminService.createUser(participant, study, null, false, false);
            fail("Sign up with email already in use should throw an exception");
        } catch(EntityAlreadyExistsException e) { 
            assertEquals("Account already exists.", e.getMessage());
        }
    }

    @Test
    public void testDeleteUserWhenSignedOut() {
        UserSession session = userAdminService.createUser(participant, study, null, true, true);
        authService.signOut(session);
        assertNull(authService.getSession(session.getSessionToken()));
        // Shouldn't crash
        userAdminService.deleteUser(study, session.getUser().getId());
        assertNull(authService.getSession(session.getSessionToken()));
    }

    @Test
    public void testDeleteUserThatHasBeenDeleted() {
        UserSession session = userAdminService.createUser(participant, study, null, true, true);
        userAdminService.deleteUser(study, session.getUser().getId());
        assertNull(authService.getSession(session.getSessionToken()));
        // Delete again shouldn't crash
        userAdminService.deleteUser(study, session.getUser().getId());
        assertNull(authService.getSession(session.getSessionToken()));
    }
    
    @Test
    public void creatingUserThenDeletingRemovesExternalIdAssignment() {
        List<String> idForTest = Lists.newArrayList("AAA");
        externalIdService.addExternalIds(study, idForTest);
        try {
            UserSession session = userAdminService.createUser(participant, study, null, true, true);
            
            externalIdService.assignExternalId(study, "AAA", session.getUser().getHealthCode());

            DynamoExternalIdentifier identifier = getDynamoExternalIdentifier(session);
            assertEquals(session.getUser().getHealthCode(), identifier.getHealthCode());
            
            // Now delete the user, and the assignment should then be free;
            userAdminService.deleteUser(study, session.getUser().getId());
            
            identifier = getDynamoExternalIdentifier(session);
            assertNull(identifier.getHealthCode());
            
            // Now this works
            externalIdService.assignExternalId(study, "AAA", session.getUser().getHealthCode());
        } finally {
            // this is a cheat, for sure, but allow deletion
            study.setExternalIdValidationEnabled(false);
            externalIdService.deleteExternalIds(study, idForTest);
        }
    }

    private DynamoExternalIdentifier getDynamoExternalIdentifier(UserSession session) {
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(study.getStudyIdentifier(), "AAA");
        return mapper.load(keyObject);
    }
}
