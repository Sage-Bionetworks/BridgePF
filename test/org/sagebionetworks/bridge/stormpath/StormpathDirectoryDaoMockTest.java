package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.application.ApplicationAccountStoreMapping;
import com.stormpath.sdk.application.ApplicationAccountStoreMappingList;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.AccountCreationPolicy;
import com.stormpath.sdk.directory.AccountStore;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.directory.PasswordPolicy;
import com.stormpath.sdk.directory.PasswordStrength;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupCriteria;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.mail.EmailStatus;
import com.stormpath.sdk.mail.ModeledEmailTemplate;
import com.stormpath.sdk.mail.ModeledEmailTemplateList;
import com.stormpath.sdk.resource.ResourceException;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;

public class StormpathDirectoryDaoMockTest {
    private static final org.sagebionetworks.bridge.models.studies.PasswordPolicy DEFAULT_PASSWORD_POLICY =
            org.sagebionetworks.bridge.models.studies.PasswordPolicy.DEFAULT_PASSWORD_POLICY;
    private static final String DIRECTORY_NAME = TestConstants.TEST_STUDY_IDENTIFIER + " (dev)";
    private static final String STORMPATH_APPLICATION_HREF = "http://localhost/stormpath/app/mockApp";
    private static final String EXISTING_STORMPATH_DIRECTORY_HREF = "http://localhost/stormpath/directory/existingDir";
    private static final String NEW_STORMPATH_DIRECTORY_HREF = "http://localhost/stormpath/directory/newDir";
    private static final String STUDY_NAME = "StormpathDirectoryDaoMockTest Study";
    private static final String WEBSERVICES_URL = "http://localhost/bridge";

    private static final String TEMPLATED_RESET_PASSWORD_SUBJECT = "reset password for ${studyName}";
    private static final String RESOLVED_RESET_PASSWORD_EMAIL_SUBJECT = "reset password for " + STUDY_NAME;
    private static final String TEMPLATED_RESET_PASSWORD_BODY = "Click here to reset password for ${studyName}";
    private static final String RESOLVED_RESET_PASSWORD_EMAIL_BODY = "Click here to reset password for " + STUDY_NAME;

    private static final String TEMPLATED_VERIFY_EMAIL_SUBJECT = "verify email for ${studyName}";
    private static final String RESOLVED_VERIFY_EMAIL_SUBJECT = "verify email for " + STUDY_NAME;
    private static final String TEMPLATED_VERIFY_EMAIL_BODY = "Click here to verify email for ${studyName}";
    private static final String RESOLVED_VERIFY_EMAIL_BODY = "Click here to verify email for " + STUDY_NAME;

    private List<Group> createdGroupList;
    private Set<String> createdGroupNameSet;
    private StormpathDirectoryDao dao;
    @SuppressWarnings("FieldCanBeLocal") private ApplicationAccountStoreMapping mockExistingAccountStoreMapping;
    private ApplicationAccountStoreMapping mockNewAccountStoreMapping;
    private Application mockApplication;
    private Client mockClient;
    private Directory mockExistingDirectory;
    private Directory mockNewDirectory;

    @Before
    public void setup() {
        // mock config
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getEnvironment()).thenReturn(Environment.DEV);
        when(mockConfig.getStormpathApplicationHref()).thenReturn(STORMPATH_APPLICATION_HREF);
        when(mockConfig.getWebservicesURL()).thenReturn(WEBSERVICES_URL);

        // mock Stormpath client
        mockClient = mock(Client.class);

        // Mock client instanstiates multiple groups. Keep track of what groups are created.
        createdGroupList = new ArrayList<>();
        createdGroupNameSet = new HashSet<>();
        when(mockClient.instantiate(Group.class)).then(instantiateInvocation -> {
            Group mockCreatedGroup = mock(Group.class);
            createdGroupList.add(mockCreatedGroup);

            when(mockCreatedGroup.setName(any())).then(setNameInvocation -> {
                String groupName = setNameInvocation.getArgumentAt(0, String.class);
                createdGroupNameSet.add(groupName);
                return setNameInvocation.getMock();
            });

            return mockCreatedGroup;
        });

        // mock application
        mockApplication = mock(Application.class);
        when(mockClient.getResource(STORMPATH_APPLICATION_HREF, Application.class)).thenReturn(mockApplication);

        // mock existing account store mapping
        mockExistingAccountStoreMapping = mockAccountStoreMapping(EXISTING_STORMPATH_DIRECTORY_HREF);
        List<ApplicationAccountStoreMapping> accountStoreMappingJavaList = ImmutableList.of(
                mockExistingAccountStoreMapping);
        ApplicationAccountStoreMappingList mockAccountStoreMappingList = mock(
                ApplicationAccountStoreMappingList.class);
        when(mockAccountStoreMappingList.iterator()).thenReturn(accountStoreMappingJavaList.iterator());
        when(mockApplication.getAccountStoreMappings(StormpathDirectoryDao.ASM_CRITERIA)).thenReturn(
                mockAccountStoreMappingList);

        // mock new account store mapping - This doesn't exist yet, but is returned by client.instantiate().
        mockNewAccountStoreMapping = mockAccountStoreMapping(NEW_STORMPATH_DIRECTORY_HREF);
        when(mockClient.instantiate(ApplicationAccountStoreMapping.class)).thenReturn(mockNewAccountStoreMapping);
        when(mockApplication.createAccountStoreMapping(mockNewAccountStoreMapping)).thenReturn(
                mockNewAccountStoreMapping);

        // mock existing directory
        mockExistingDirectory = mockDirectory(EXISTING_STORMPATH_DIRECTORY_HREF, true);
        when(mockClient.getResource(EXISTING_STORMPATH_DIRECTORY_HREF, Directory.class)).thenReturn(
                mockExistingDirectory);

        // mock new directory - Stormpath throws a 404. Amazingly, even the exception needs to be mocked.
        mockNewDirectory = mockDirectory(NEW_STORMPATH_DIRECTORY_HREF, false);
        ResourceException directory404Exception = mock(ResourceException.class);
        when(directory404Exception.getCode()).thenReturn(404);
        when(mockClient.getResource(NEW_STORMPATH_DIRECTORY_HREF, Directory.class)).thenThrow(directory404Exception);
        when(mockClient.instantiate(Directory.class)).thenReturn(mockNewDirectory);
        when(mockClient.createDirectory(mockNewDirectory)).thenReturn(mockNewDirectory);

        // set up dao
        dao = new StormpathDirectoryDao();
        dao.setBridgeConfig(mockConfig);
        dao.setStormpathClient(mockClient);
    }

    private static ApplicationAccountStoreMapping mockAccountStoreMapping(String href) {
        // mock Account Store
        AccountStore mockAccountStore = mock(AccountStore.class);
        when(mockAccountStore.getHref()).thenReturn(href);

        // mock Account Store Mapping
        ApplicationAccountStoreMapping mockAccountStoreMapping = mock(ApplicationAccountStoreMapping.class);
        when(mockAccountStoreMapping.getAccountStore()).thenReturn(mockAccountStore);

        return mockAccountStoreMapping;
    }

    private static Directory mockDirectory(String href, boolean createGroups) {
        // mock directory
        Directory mockDirectory = mock(Directory.class);
        when(mockDirectory.getHref()).thenReturn(href);

        // mock password policy
        PasswordPolicy mockPasswordPolicy = mock(PasswordPolicy.class);
        when(mockDirectory.getPasswordPolicy()).thenReturn(mockPasswordPolicy);

        ModeledEmailTemplateList mockResetEmailTemplateList = mockEmailTemplateList();
        when(mockPasswordPolicy.getResetEmailTemplates()).thenReturn(mockResetEmailTemplateList);

        // mock password strength
        PasswordStrength mockPasswordStrength = mock(PasswordStrength.class);
        when(mockPasswordPolicy.getStrength()).thenReturn(mockPasswordStrength);

        // mock account creation policy
        AccountCreationPolicy mockAccountCreationPolicy = mock(AccountCreationPolicy.class);
        when(mockDirectory.getAccountCreationPolicy()).thenReturn(mockAccountCreationPolicy);

        ModeledEmailTemplateList mockVerifyEmailTemplateList = mockEmailTemplateList();
        when(mockAccountCreationPolicy.getAccountVerificationEmailTemplates()).thenReturn(mockVerifyEmailTemplateList);

        // It's really hard to create a mock for a GroupList query, so just have Directory.getGroups() return a generic
        // group list (or an empty list).

        // mock group (if applicable)
        List<Group> groupJavaList = new ArrayList<>();
        if (createGroups) {
            groupJavaList.add(mock(Group.class));
        }

        // mock group list
        GroupList mockGroupList = mock(GroupList.class);
        when(mockGroupList.iterator()).thenReturn(groupJavaList.iterator());

        // mock query and add to directory
        when(mockDirectory.getGroups(any(GroupCriteria.class))).thenReturn(mockGroupList);

        // mock Directory.createGroup(). The method should return the passed in object.
        when(mockDirectory.createGroup(any(Group.class))).then(invocation -> invocation.getArgumentAt(0, Group.class));

        return mockDirectory;
    }

    private static ModeledEmailTemplateList mockEmailTemplateList() {
        // mock template list
        ModeledEmailTemplateList mockEmailTemplateList = mock(ModeledEmailTemplateList.class);

        // mock template
        ModeledEmailTemplate mockEmailTemplate = mock(ModeledEmailTemplate.class);
        List<ModeledEmailTemplate> emailTemplateJavaList = ImmutableList.of(mockEmailTemplate);

        // We use an Answer to generate a new iterator. This is so one part of the test can't "use up" the iterator and
        // crash another part of the test with NoSuchMethodException.
        when(mockEmailTemplateList.iterator()).thenAnswer(invocation -> emailTemplateJavaList.iterator());

        // We need the template that doesn't contain "Stormpath", because of Stormpath reasons...
        when(mockEmailTemplate.getHtmlBody()).thenReturn("dummy HTML body that we're looking for");

        return mockEmailTemplateList;
    }

    private static ModeledEmailTemplate getSingletonEmailTemplate(ModeledEmailTemplateList emailTemplateList) {
        // This only works because in mockEmailTemplateList() we set up the email template list to only have one value,
        // accessible through the iterator.
        return emailTemplateList.iterator().next();
    }

    private Study getStudyForTest(String href) {
        // Basic study args
        Study study = TestUtils.getValidStudy(this.getClass());
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setName(STUDY_NAME);
        study.setStormpathHref(href);

        // Reset password email template
        EmailTemplate resetPasswordTemplate = new EmailTemplate(TEMPLATED_RESET_PASSWORD_SUBJECT,
                TEMPLATED_RESET_PASSWORD_BODY, MimeType.TEXT);
        study.setResetPasswordTemplate(resetPasswordTemplate);

        // Verify email template
        EmailTemplate verifyEmailTemplate = new EmailTemplate(TEMPLATED_VERIFY_EMAIL_SUBJECT,
                TEMPLATED_VERIFY_EMAIL_BODY, MimeType.TEXT);
        study.setVerifyEmailTemplate(verifyEmailTemplate);

        return study;
    }

    @Test
    public void create() {
        // execute
        Study study = getStudyForTest(NEW_STORMPATH_DIRECTORY_HREF);
        String createdDirHref = dao.createDirectoryForStudy(study);
        assertEquals(NEW_STORMPATH_DIRECTORY_HREF, createdDirHref);

        // validate back-ends
        // verify created directory
        verify(mockClient).instantiate(Directory.class);
        verify(mockNewDirectory).setName(DIRECTORY_NAME);
        verify(mockClient).createDirectory(mockNewDirectory);

        // verify password policy and account creation policy
        verifyPasswordPolicyForDirectory(study, mockNewDirectory);
        verifyAccountCreationPolicy(study, mockNewDirectory);

        // verify created account store mapping
        verify(mockClient).instantiate(ApplicationAccountStoreMapping.class);
        verify(mockNewAccountStoreMapping).setAccountStore(mockNewDirectory);
        verify(mockNewAccountStoreMapping).setApplication(mockApplication);
        verify(mockNewAccountStoreMapping).setDefaultAccountStore(false);
        verify(mockNewAccountStoreMapping).setDefaultGroupStore(false);
        verify(mockNewAccountStoreMapping).setListIndex(10);
        verify(mockApplication).createAccountStoreMapping(mockNewAccountStoreMapping);

        // verify created groups
        for (Roles oneRole : Roles.values()) {
            assertTrue(createdGroupNameSet.contains(oneRole.name().toLowerCase()));
        }
        for (Group oneCreatedGroup : createdGroupList) {
            verify(mockNewDirectory).createGroup(oneCreatedGroup);
        }
    }

    @Test
    public void update() {
        // execute
        Study study = getStudyForTest(EXISTING_STORMPATH_DIRECTORY_HREF);
        dao.updateDirectoryForStudy(study);

        // validate
        verifyPasswordPolicyForDirectory(study, mockExistingDirectory);
        verifyAccountCreationPolicy(study, mockExistingDirectory);
    }

    @Test
    public void getExisting() {
        Study study = getStudyForTest(EXISTING_STORMPATH_DIRECTORY_HREF);
        Directory directory = dao.getDirectoryForStudy(study);
        assertSame(mockExistingDirectory, directory);
    }

    @Test
    public void getNonExisting() {
        Study study = getStudyForTest(NEW_STORMPATH_DIRECTORY_HREF);
        Directory directory = dao.getDirectoryForStudy(study);
        assertNull(directory);
    }

    @Test
    public void delete() {
        Study study = getStudyForTest(EXISTING_STORMPATH_DIRECTORY_HREF);
        dao.deleteDirectoryForStudy(study);
        verify(mockExistingDirectory).delete();
    }

    private static void verifyPasswordPolicyForDirectory(Study study, Directory directory) {
        PasswordPolicy passwordPolicy = directory.getPasswordPolicy();
        verify(passwordPolicy).setResetEmailStatus(EmailStatus.ENABLED);
        verify(passwordPolicy).setResetSuccessEmailStatus(EmailStatus.DISABLED);
        verify(passwordPolicy).save();

        // verify reset password template
        ModeledEmailTemplate resetPasswordTemplate = getSingletonEmailTemplate(passwordPolicy
                .getResetEmailTemplates());
        verify(resetPasswordTemplate).setFromName(study.getSponsorName());
        verify(resetPasswordTemplate).setFromEmailAddress(study.getSupportEmail());
        verify(resetPasswordTemplate).setSubject(RESOLVED_RESET_PASSWORD_EMAIL_SUBJECT);
        verify(resetPasswordTemplate).setMimeType(com.stormpath.sdk.mail.MimeType.PLAIN_TEXT);
        verify(resetPasswordTemplate).setTextBody(RESOLVED_RESET_PASSWORD_EMAIL_BODY);
        verify(resetPasswordTemplate).setHtmlBody(RESOLVED_RESET_PASSWORD_EMAIL_BODY);
        verify(resetPasswordTemplate).setLinkBaseUrl(WEBSERVICES_URL + "/mobile/resetPassword.html?study=" +
                TestConstants.TEST_STUDY_IDENTIFIER);
        verify(resetPasswordTemplate).save();

        // verify password strength
        PasswordStrength passwordStrength = passwordPolicy.getStrength();
        verify(passwordStrength).setMaxLength(org.sagebionetworks.bridge.models.studies.PasswordPolicy
                .FIXED_MAX_LENGTH);
        verify(passwordStrength).setMinDiacritic(0);
        verify(passwordStrength).setMinLength(DEFAULT_PASSWORD_POLICY.getMinLength());
        verify(passwordStrength).setMinNumeric(1);
        verify(passwordStrength).setMinSymbol(1);
        verify(passwordStrength).setMinLowerCase(1);
        verify(passwordStrength).setMinUpperCase(1);
        verify(passwordStrength).save();
    }

    private static void verifyAccountCreationPolicy(Study study, Directory directory) {
        AccountCreationPolicy accountCreationPolicy = directory.getAccountCreationPolicy();
        verify(accountCreationPolicy).setVerificationEmailStatus(EmailStatus.ENABLED);
        verify(accountCreationPolicy).setVerificationSuccessEmailStatus(EmailStatus.DISABLED);
        verify(accountCreationPolicy).setWelcomeEmailStatus(EmailStatus.DISABLED);
        verify(accountCreationPolicy).save();

        // verify verify email template
        ModeledEmailTemplate verifyEmailTemplate = getSingletonEmailTemplate(accountCreationPolicy
                .getAccountVerificationEmailTemplates());
        verify(verifyEmailTemplate).setFromName(study.getSponsorName());
        verify(verifyEmailTemplate).setFromEmailAddress(study.getSupportEmail());
        verify(verifyEmailTemplate).setSubject(RESOLVED_VERIFY_EMAIL_SUBJECT);
        verify(verifyEmailTemplate).setMimeType(com.stormpath.sdk.mail.MimeType.PLAIN_TEXT);
        verify(verifyEmailTemplate).setTextBody(RESOLVED_VERIFY_EMAIL_BODY);
        verify(verifyEmailTemplate).setHtmlBody(RESOLVED_VERIFY_EMAIL_BODY);
        verify(verifyEmailTemplate).setLinkBaseUrl(WEBSERVICES_URL + "/mobile/verifyEmail.html?study=" +
                TestConstants.TEST_STUDY_IDENTIFIER);
        verify(verifyEmailTemplate).save();
    }
}
