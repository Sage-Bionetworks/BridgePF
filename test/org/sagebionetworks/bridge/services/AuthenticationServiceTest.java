package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.DefaultStudyBootstrapper;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUserAdminHelper;
import org.sagebionetworks.bridge.TestUserAdminHelper.TestUser;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class AuthenticationServiceTest {
    
    private static final String PASSWORD = "P@ssword`1";
    private static final Set<String> ORIGINAL_DATA_GROUPS = Sets.newHashSet("group1");
    private static final Set<String> UPDATED_DATA_GROUPS = Sets.newHashSet("sdk-int-1","sdk-int-2","group1");
    
    @Resource
    private CacheProvider cacheProvider;

    @Resource
    private AuthenticationService authService;

    @Resource
    private AccountDao accountDao;
    
    @Resource
    private StudyService studyService;

    @Resource
    private TestUserAdminHelper helper;
    
    @Resource
    private SubpopulationService subpopService;
    
    @Resource
    private ParticipantService participantService;
    
    @Resource
    private UserAdminService userAdminService;
    
    @Resource
    private AccountWorkflowService accountWorkflowService;
    
    @Resource
    private IntentService intentService;
    
    private Study study;
    
    private TestUser testUser;
    
    @Before
    public void before() {
        study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        study.getInstallLinks().put(OperatingSystem.ANDROID, "TEST");
        studyService.updateStudy(study, true);
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        if (testUser != null && testUser.getId() != null) {
            helper.deleteUser(testUser);
        }
    }
    
    private void initTestUser() {
        testUser = helper.getBuilder(AuthenticationServiceTest.class).build();
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoEmail() throws Exception {
        authService.signIn(study, TEST_CONTEXT, new SignIn.Builder().withStudy(study.getIdentifier()).withPassword("bar").build());
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        authService.signIn(study, TEST_CONTEXT, new SignIn.Builder().withStudy(study.getIdentifier()).withEmail("foobar").build());
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        authService.signIn(study, TEST_CONTEXT,
                new SignIn.Builder().withStudy(study.getIdentifier()).withEmail("foobar").withPassword("bar").build());
    }
    
    @Test
    public void signUpWithIntentToParticipate() throws Exception {
        String email = TestUtils.makeRandomTestEmail(AuthenticationServiceTest.class);
        StudyParticipant participant = new StudyParticipant.Builder()
                .withPhone(TestConstants.PHONE)
                .withEmail(email)
                .withPassword(PASSWORD).build();
        IdentifierHolder holder = null;
        try {
            IntentToParticipate intent = new IntentToParticipate.Builder()
                    .withScope(SharingScope.NO_SHARING)
                    .withPhone(TestConstants.PHONE)
                    .withStudyId(TestConstants.TEST_STUDY_IDENTIFIER)
                    .withOsName(OperatingSystem.ANDROID)
                    .withConsentSignature(new ConsentSignature.Builder()
                            .withName("Name")
                            .withBirthdate("1970-01-01").build())
                    .withSubpopGuid(TestConstants.TEST_STUDY_IDENTIFIER).build();
            intentService.submitIntentToParticipate(intent);
            
            study.setAutoVerificationEmailSuppressed(true);
            study.setAutoVerificationPhoneSuppressed(true);
            holder = authService.signUp(study, participant);
            
            Account account = accountDao.getAccount(
                    AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, holder.getIdentifier()));
            account.setPhoneVerified(true);
            account.setEmailVerified(true);
            account.setStatus(AccountStatus.ENABLED);
            accountDao.updateAccount(account);
            
            CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(TestConstants.TEST_STUDY)
                    .build();
            
            // You should be able to sign in, and be consented. No exception.
            SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                    .withEmail(email).withPassword(PASSWORD).build();
            authService.signIn(study, context, signIn);
        } finally {
            if (holder != null) {
                userAdminService.deleteUser(study, holder.getIdentifier());
            }
        }
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        initTestUser();
        UserSession newSession = authService.getSession(testUser.getSessionToken());
        assertEquals("Email is for test2 user", newSession.getParticipant().getEmail(), testUser.getEmail());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(testUser.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        initTestUser();
        String sessionToken = testUser.getSessionToken();
        UserSession newSession = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
        assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getParticipant().getEmail());
        assertNotEquals("Should creating a new session.", sessionToken, newSession.getSessionToken());
    }

    @Test
    public void signInSetsSharingScope() {
        initTestUser();
        UserSession newSession = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
        assertEquals(SharingScope.NO_SHARING, newSession.getParticipant().getSharingScope()); // this is the default.
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("anytoken");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        initTestUser();
        UserSession newSession = authService.getSession(testUser.getSessionToken());

        assertEquals("Email is for test2 user", testUser.getEmail(), newSession.getParticipant().getEmail());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(newSession.getSessionToken()));
    }

    @Test(expected = NullPointerException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        authService.requestResetPassword(study, false, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).build();
        authService.requestResetPassword(study, false, signIn);
    }
    
    @Test(expected = BadRequestException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        authService.resetPassword(new PasswordReset("newpassword", "resettoken", "api"));
    }

    @Test
    public void canResendEmailVerification() throws Exception {
        testUser = helper.getBuilder(AuthenticationServiceTest.class).withConsent(false).withSignIn(false).build();
        AccountId accountId = AccountId.forEmail(testUser.getStudy().getIdentifier(), testUser.getEmail());
        authService.resendVerification(ChannelType.EMAIL, accountId);
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.RESEARCHER).build();
        // Can no longer delete an account without getting a session, and the assigned ID, first, so there's
        // no way to use finally here if sign in fails for some reason.
        UserSession session = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
        helper.deleteUser(testUser.getStudy(), session.getId());
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(Roles.ADMIN)).build());
        
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).withRoles(Roles.ADMIN).build();
        UserSession session = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
        helper.deleteUser(testUser.getStudy(), session.getId());
    }

    @Test
    public void testSignOut() {
        initTestUser();
        final String sessionToken = testUser.getSessionToken();
        final String userId = testUser.getId();
        authService.signOut(testUser.getSession());
        assertNull(cacheProvider.getUserSession(sessionToken));
        assertNull(cacheProvider.getUserSessionByUserId(userId));
    }

    @Test
    public void testSignOutWhenSignedOut() {
        initTestUser();
        final String sessionToken = testUser.getSessionToken();
        final String userId = testUser.getId();
        authService.signOut(testUser.getSession());
        authService.signOut(testUser.getSession());
        assertNull(cacheProvider.getUserSession(sessionToken));
        assertNull(cacheProvider.getUserSessionByUserId(userId));
    }
    
    // This test combines test of dataGroups, languages, and other data that can be set.
    @Test
    public void signUpDataExistsOnSignIn() {
        study.setExternalIdValidationEnabled(false);
        StudyParticipant participant = TestUtils.getStudyParticipant(AuthenticationServiceTest.class);
        IdentifierHolder holder = null;
        try {
            holder = authService.signUp(study, participant);
            
            StudyParticipant persisted = participantService.getParticipant(study, holder.getIdentifier(), false);
            assertEquals(participant.getFirstName(), persisted.getFirstName());
            assertEquals(participant.getLastName(), persisted.getLastName());
            assertEquals(participant.getEmail(), persisted.getEmail());
            assertEquals(participant.getExternalId(), persisted.getExternalId());
            assertEquals(participant.getSharingScope(), persisted.getSharingScope());
            assertTrue(persisted.isNotifyByEmail());
            assertNotNull(persisted.getId());
            assertEquals(participant.getDataGroups(), persisted.getDataGroups());
            assertEquals(participant.getAttributes().get("can_be_recontacted"), persisted.getAttributes().get("can_be_recontacted"));
            assertEquals(participant.getLanguages(), persisted.getLanguages());
        } finally {
            if (holder != null) {
                userAdminService.deleteUser(study, holder.getIdentifier());    
            }
        }
    }
    
    @Test
    public void signUpWillCreateDataGroups() {
        String email = TestUtils.makeRandomTestEmail(AuthenticationServiceTest.class); 
        Set<String> groups = Sets.newHashSet("group1");
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(email).withPassword("P@ssword1").withDataGroups(groups).build();
        IdentifierHolder holder = null;
        try {
            holder = authService.signUp(study, participant);
            
            Account account = accountDao.getAccount(AccountId.forId(study.getIdentifier(), holder.getIdentifier()));
            assertEquals(groups, account.getDataGroups());
        } finally {
            if (holder != null) {
                userAdminService.deleteUser(study, holder.getIdentifier());    
            }
        }
    }
    
    @Test
    public void userCreatedWithDataGroupsHasThemOnSignIn() throws Exception {
        int numOfGroups = DefaultStudyBootstrapper.TEST_DATA_GROUPS.size();
        testUser = helper.getBuilder(AuthenticationServiceTest.class).withConsent(true)
                .withDataGroups(DefaultStudyBootstrapper.TEST_DATA_GROUPS).build();

        UserSession session = authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
        // Verify we created a list and the anticipated group was not null; the test user will 
        // have one extra data group, because "test_user" is added to each user.
        assertEquals(numOfGroups, session.getParticipant().getDataGroups().size()); 
        assertEquals(DefaultStudyBootstrapper.TEST_DATA_GROUPS, session.getParticipant().getDataGroups());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void invalidDataGroupsAreRejected() throws Exception {
        Set<String> dataGroups = Sets.newHashSet("bugleboy");
        helper.getBuilder(AuthenticationServiceTest.class).withConsent(false).withSignIn(false)
                .withDataGroups(dataGroups).build();
    }
    
    // Account enumeration security. Verify the service is quite (throws no exceptions) when we don't
    // recognize an account.

    @Test
    public void secondSignUpTriggersResetPasswordInstead() {
        // First sign up
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(false).withSignIn(false).build();
        
        AccountWorkflowService accountWorkflowServiceSpy = spy(accountWorkflowService);
        authService.setAccountWorkflowService(accountWorkflowServiceSpy);

        // Second sign up
        authService.signUp(testUser.getStudy(), testUser.getStudyParticipant());
        
        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        verify(accountWorkflowServiceSpy).notifyAccountExists(eq(testUser.getStudy()), accountIdCaptor.capture());
        assertEquals(testUser.getStudyIdentifier().getIdentifier(), accountIdCaptor.getValue().getStudyId());
        assertEquals(testUser.getId(), accountIdCaptor.getValue().getId());
    }
    
    @Test
    public void resendEmailVerificationLooksSuccessfulWhenNoAccount() throws Exception {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, "notarealaccount@sagebase.org");
        authService.resendVerification(ChannelType.EMAIL, accountId);
    }
    
    @Test
    public void requestResetPasswordLooksSuccessfulWhenNoAccount() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail("notarealaccount@sagebase.org")
                .build();
        authService.requestResetPassword(study, false, signIn);
    }
    
    // Consent statuses passed on to sessionInfo
    
    @Test
    public void consentStatusesPresentInSession() throws Exception {
        // User is consenting
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();

        JsonNode info = UserSessionInfo.toJSON(testUser.getSession());
        
        TypeReference<Map<SubpopulationGuid,ConsentStatus>> tRef = new TypeReference<Map<SubpopulationGuid,ConsentStatus>>() {};
        Map<SubpopulationGuid,ConsentStatus> statuses = BridgeObjectMapper.get().readValue(info.get("consentStatuses").toString(), tRef); 
        
        ConsentStatus status = statuses.get(SubpopulationGuid.create(testUser.getStudyIdentifier().getIdentifier()));
        assertTrue(status.isConsented());
        assertEquals(testUser.getStudyIdentifier().getIdentifier(), status.getSubpopulationGuid());
    }
    
    @Test
    public void existingLanguagePreferencesAreLoaded() {
        List<String> LANGS = ImmutableList.of("en","es");
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withLanguages(LANGS).withSignIn(true).build();
        
        authService.signOut(testUser.getSession());
        
        Study study = studyService.getStudy(testUser.getStudyIdentifier());
        CriteriaContext context = testUser.getCriteriaContext();
        
        UserSession session = authService.signIn(study, context, testUser.getSignIn());
        assertEquals(LANGS, session.getParticipant().getLanguages());
    }
    
    @Test
    public void languagePreferencesAreRetrievedFromContext() {
        List<String> LANGS = ImmutableList.of("fr","es");
        testUser = helper.getBuilder(AuthenticationServiceTest.class)
                .withConsent(true).withSignIn(true).build();
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(testUser.getStudyParticipant())
                .withLanguages(LANGS).build();
        
        testUser.getSession().setParticipant(participant);
        CriteriaContext context = testUser.getCriteriaContext();
        
        authService.signOut(testUser.getSession());
        
        Study study = studyService.getStudy(testUser.getStudyIdentifier());
        
        // If the languages are in the session after signing in, they've been persisted in the account table,
        // there isn't anything further to test (as there was when these were stored in a separate table).
        UserSession session = authService.signIn(study, context, testUser.getSignIn());
        assertEquals(LANGS, session.getParticipant().getLanguages());
    }
    
    @Test
    public void updateSession() {
        testUser = helper.getBuilder(AuthenticationServiceTest.class).withConsent(false)
                .withDataGroups(ORIGINAL_DATA_GROUPS).withSignIn(false).build();
        String userId = testUser.getId();
        
        // Update the data groups
        StudyParticipant participant = participantService.getParticipant(study, userId, false);
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(participant)
                .withDataGroups(UPDATED_DATA_GROUPS).withId(userId).build();
        participantService.updateParticipant(study, updated);
        
        // Now update the session, these changes should be reflected
        CriteriaContext context = new CriteriaContext.Builder().withStudyIdentifier(study.getStudyIdentifier())
                .withUserId(userId).build();
        Set<String> retrievedSessionDataGroups = authService.getSession(study, context)
                .getParticipant().getDataGroups();

        assertEquals(UPDATED_DATA_GROUPS, retrievedSessionDataGroups);
    }
    
    @Test
    public void signUpWillNotSetRoles() {
        String email = TestUtils.makeRandomTestEmail(AuthenticationServiceTest.class);
        Set<Roles> roles = Sets.newHashSet(Roles.DEVELOPER, Roles.RESEARCHER);
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(email).withPassword(PASSWORD).withRoles(roles).build();
        IdentifierHolder holder = null;
        try {
            holder = authService.signUp(study, participant);    
            participant = participantService.getParticipant(study, holder.getIdentifier(), false);
            assertTrue(participant.getRoles().isEmpty());
        } finally {
            if (holder != null) {
                userAdminService.deleteUser(study, holder.getIdentifier());    
            }
        }
    }
    
    @Test
    public void signInRefreshesSessionChangingTokens() {
        testUser = helper.getBuilder(AuthenticationServiceTest.class).withConsent(false).withSignIn(false).build();
        
        // User's ID ties this record to the newly signed in user, which contains only an ID. So the rest of the 
        // session should be initialized from scratch.
        StudyParticipant oldRecord = new StudyParticipant.Builder()
                .withHealthCode("oldHealthCode")
                .withId(testUser.getId()).build();
        UserSession cachedSession = new UserSession(oldRecord);
        cachedSession.setSessionToken("cachedSessionToken");
        cachedSession.setInternalSessionToken("cachedInternalSessionToken");
        cacheProvider.setUserSession(cachedSession);
        
        UserSession session = null;
        try {
            authService.signIn(testUser.getStudy(), TEST_CONTEXT, testUser.getSignIn());
            fail("expected exception");
        } catch(ConsentRequiredException e) {
            session = e.getUserSession();
        }
        assertNotEquals(cachedSession.getSessionToken(), session.getSessionToken());
        assertNotEquals(cachedSession.getInternalSessionToken(), session.getInternalSessionToken());
        // but the rest is updated.  
        assertEquals(testUser.getStudyParticipant().getEmail(), session.getParticipant().getEmail());
        assertEquals(testUser.getStudyParticipant().getFirstName(), session.getParticipant().getFirstName());
        assertEquals(testUser.getStudyParticipant().getLastName(), session.getParticipant().getLastName());
        assertEquals(testUser.getStudyParticipant().getHealthCode(), session.getHealthCode());
        // etc.
    }
}
