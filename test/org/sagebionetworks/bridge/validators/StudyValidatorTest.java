package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.OAuthProviderTest;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class StudyValidatorTest {

    private static final StudyValidator INSTANCE = StudyValidator.INSTANCE;
    private static final String CALLBACK_URL = OAuthProviderTest.CALLBACK_URL;
    private static final String APP_ID = "appID";
    private static final String PATHS = "paths";
    private static final String NAMESPACE = "namespace";
    private static final String PACKAGE_NAME = "package_name";
    private static final String FINGERPRINTS = "sha256_cert_fingerprints";
    private static final String TOO_LONG_STRING = "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901";

    private DynamoStudy study;
    
    @Before
    public void createValidStudy() {
        study = TestUtils.getValidStudy(StudyValidatorTest.class);
    }
    
    @Test
    public void acceptsValidStudy() {
        AndroidAppLink androidAppLink = new AndroidAppLink("org.sagebionetworks.bridge", "APP", Lists.newArrayList(
                "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"));
        List<AndroidAppLink> androidAppLinks = Lists.newArrayList(androidAppLink);
        study.setAndroidAppLinks(androidAppLinks);
        
        AppleAppLink appleAppLink = new AppleAppLink("org.sagebionetworks.bridge.APP",
                Lists.newArrayList("/" + study.getIdentifier() + "/*"));
        List<AppleAppLink> appleAppLinks = Lists.newArrayList(appleAppLink);
        study.setAppleAppLinks(appleAppLinks);
        
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    // While 2 is not a good length, we must allow it for legacy reasons.
    @Test
    public void minLengthCannotBeLessThan2() {
        study.setPasswordPolicy(new PasswordPolicy(1, false, false, false, false));
        assertValidatorMessage(INSTANCE, study, "passwordPolicy.minLength", "must be 2-999 characters");
    }
    
    @Test
    public void shortNameTooLong() {
        study.setShortName("ThisNameIsOverTenCharactersLong");
        assertValidatorMessage(INSTANCE, study, "shortName", "must be 10 characters or less");
    }
    
    @Test
    public void sponsorNameRequired() {
        study.setSponsorName("");
        assertValidatorMessage(INSTANCE, study, "sponsorName", "is required");
    }
    
    @Test
    public void minLengthCannotBeMoreThan999() {
        study.setPasswordPolicy(new PasswordPolicy(1000, false, false, false, false));
        assertValidatorMessage(INSTANCE, study, "passwordPolicy.minLength", "must be 2-999 characters");
    }
    
    @Test
    public void resetPasswordMustHaveUrlVariable() {
        study.setResetPasswordTemplate(new EmailTemplate("subject", "no url variable", MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.body", "must contain one of these template variables: ${url}, ${resetPasswordUrl}");
    }
    
    @Test
    public void verifyEmailMustHaveUrlVariable() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "no url variable", MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.body", "must contain one of these template variables: ${url}, ${emailVerificationUrl}");
    }

    @Test
    public void cannotCreateIdentifierWithUppercase() {
        study.setIdentifier("Test");
        assertValidatorMessage(INSTANCE, study, "identifier", "must contain only lower-case letters and/or numbers with optional dashes");
    }

    @Test
    public void cannotCreateInvalidIdentifierWithSpaces() {
        study.setIdentifier("test test");
        assertValidatorMessage(INSTANCE, study, "identifier", "must contain only lower-case letters and/or numbers with optional dashes");
    }

    @Test
    public void identifierCanContainDashes() {
        study.setIdentifier("sage-pd");
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void acceptsEventKeysWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b2"));
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void rejectEventKeysWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b:2"));
        assertValidatorMessage(INSTANCE, study, "activityEventKeys", "must contain only lower-case letters and/or numbers with optional dashes");
    }

    @Test
    public void cannotCreateIdentifierWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b:2"));
        assertValidatorMessage(INSTANCE, study, "activityEventKeys", "must contain only lower-case letters and/or numbers with optional dashes");
    }

    @Test
    public void acceptsMultipleValidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void rejectsInvalidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "supportEmail", "'asdf' is not a valid email address");
    }
    
    @Test
    public void requiresMissingSupportEmail() {
        study.setSupportEmail(null);
        assertValidatorMessage(INSTANCE, study, "supportEmail", "is required");
    }
    
    @Test
    public void acceptsMultipleValidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void rejectsInvalidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "technicalEmail", "'asdf' is not a valid email address");
    }
    
    @Test
    public void requiresMissingTechnicalEmail() {
        study.setTechnicalEmail(null);
        assertValidatorMessage(INSTANCE, study, "technicalEmail", "is required");
    }

    @Test
    public void validFieldDefList() {
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void invalidFieldDef() {
        // This is tested in-depth in UploadFieldDefinitionListValidatorTest. Just test that we catch a non-trivial
        // error here.
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder().withName(null)
                .withType(UploadFieldType.INT).build()));
        assertValidatorMessage(INSTANCE, study, "uploadMetadataFieldDefinitions[0].name", "is required");
    }

    @Test
    public void rejectsInvalidConsentEmailAddresses() {
        study.setConsentNotificationEmail("test@test.com,asdf,test2@test.com");
        assertValidatorMessage(INSTANCE, study, "consentNotificationEmail", "'asdf' is not a valid email address");
    }
    
    @Test
    public void cannotAddConflictingEmailAttribute() {
        study.getUserProfileAttributes().add("email");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'email' conflicts with existing user profile property");
    }
    
    @Test
    public void cannotAddConflictingExternalIdAttribute() {
        study.getUserProfileAttributes().add("externalId");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'externalId' conflicts with existing user profile property");
    }
    
    @Test
    public void userProfileAttributesCannotStartWithDash() {
        study.getUserProfileAttributes().add("-illegal");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'-illegal' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCannotContainSpaces() {
        study.getUserProfileAttributes().add("Game Points");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'Game Points' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void userProfileAttributesCanBeJustADash() {
        study.getUserProfileAttributes().add("_");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCanBeJustADashAndLetter() {
        study.getUserProfileAttributes().add("_A");
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCannotBeEmpty() {
        study.getUserProfileAttributes().add("");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void missingConsentNotificationEmailOK() {
        study.setConsentNotificationEmail(null);
        Validate.entityThrowingException(INSTANCE, study);
    }    
    
    @Test
    public void requiresPasswordPolicy() {
        study.setPasswordPolicy(null);
        assertValidatorMessage(INSTANCE, study, "passwordPolicy", "is required");
    }
    
    @Test
    public void requiresVerifyEmailTemplate() {
        study.setVerifyEmailTemplate(null);
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate", "is required");
    }

    @Test
    public void requiresVerifyEmailTemplateWithSubject() {
        study.setVerifyEmailTemplate(new EmailTemplate("  ", "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.subject", "cannot be blank");
    }

    @Test
    public void requiresSignedConsentTemplateWithSubject() {
        study.setSignedConsentTemplate(new EmailTemplate("  ", "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "signedConsentTemplate.subject", "cannot be blank");
    }
    
    @Test
    public void requiresVerifyEmailTemplateWithBody() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.body", "cannot be blank");
    }

    @Test
    public void requiresSignedConsentTemplateWithBody() {
        study.setSignedConsentTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "signedConsentTemplate.body", "cannot be blank");
    }
    
    @Test
    public void requiresResetPasswordTemplate() {
        study.setResetPasswordTemplate(null);
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate", "is required");
    }

    @Test
    public void requiresResetPasswordTemplateWithSubject() {
        study.setResetPasswordTemplate(new EmailTemplate("  ", "body", MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.subject", "cannot be blank");
    }

    @Test
    public void requiresResetPasswordTemplateWithBody() {
        study.setResetPasswordTemplate(new EmailTemplate("subject", null, MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.body", "cannot be blank");
    }
    
    @Test
    public void emailSignTemplateOKWithTokenOrURL() {
        study.setEmailSignInTemplate(new EmailTemplate("subject", "${token}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);
        
        study.setEmailSignInTemplate(new EmailTemplate("subject", "${url}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void emailSignInTemplateNotRequired() {
        study.setEmailSignInTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void requiresEmailSignInTemplateWithSubject() {
        study.setEmailSignInTemplate(new EmailTemplate(null, "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.subject", "cannot be blank");
    }
    
    @Test
    public void requiresEmailSignInTemplateRequiresToken() {
        study.setEmailSignInTemplate(new EmailTemplate("subject", "body with no token", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.body", "must contain one of these template variables: ${url}, ${emailSignInUrl}, ${token}");
    }    

    @Test
    public void requiresEmailSignInTemplateWithBody() {
        study.setEmailSignInTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.body", "cannot be blank");
    }
    
    @Test
    public void accountExistsTemplateNotRequired() {
        study.setAccountExistsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void requiresAccountExistsTemplateWithSubject() {
        study.setAccountExistsTemplate(new EmailTemplate(null, "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.subject", "cannot be blank");
    }

    @Test
    public void requiresAccountExistsTemplateWithBody() {
        study.setAccountExistsTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.body", "cannot be blank");
    }
    
    @Test
    public void requiresAccountExistsTemplateRequiresURL() {
        study.setAccountExistsTemplate(new EmailTemplate("subject", "body with no url", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.body",
                "must contain one of these template variables: ${url}, ${emailSignInUrl}, ${resetPasswordUrl}");
    }
    
    @Test
    public void requiresAccountExistsTemplateOK() {
        study.setAccountExistsTemplate(new EmailTemplate("subject", "${url}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);
        
        study.setAccountExistsTemplate(new EmailTemplate("subject", "${resetPasswordUrl}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);
        
        study.setAccountExistsTemplate(new EmailTemplate("subject", "${emailSignInUrl}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);

        study.setAccountExistsTemplate(new EmailTemplate("subject", "${emailSignInUrl}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);

        study.setAccountExistsTemplate(new EmailTemplate("subject", "${resetPasswordUrl}", MimeType.HTML));
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void cannotSetMinAgeOfConsentLessThanZero() {
        study.setMinAgeOfConsent(-100);
        assertValidatorMessage(INSTANCE, study, "minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
    }
    
    @Test
    public void cannotSetAccountLimitLessThanZero() {
        study.setAccountLimit(-100);
        assertValidatorMessage(INSTANCE, study, "accountLimit", "must be zero (no limit set) or higher");
    }
    
    @Test
    public void shortListOfDataGroupsOK() {
        study.setDataGroups(Sets.newHashSet("beta_users", "production_users", "testers", "internal"));
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void longListOfDataGroupsInvalid() {
        // Make 25 data groups, each with at least 10 chars in length. This will be long enough to hit the limit.
        Set<String> dataGroupSet = new TreeSet<>();
        for (int i = 0; i < 25; i++) {
            dataGroupSet.add("data-group-" + i);
        }
        study.setDataGroups(dataGroupSet);
        assertValidatorMessage(INSTANCE, study, "dataGroups", "will not export to Synapse (string is over 250 characters: '" +
                BridgeUtils.COMMA_SPACE_JOINER.join(dataGroupSet) + "')");
    }
    
    @Test
    public void dataGroupCharactersRestricted() {
        study.setDataGroups(Sets.newHashSet("Liège"));
        assertValidatorMessage(INSTANCE, study, "dataGroups", "contains invalid tag 'Liège' (only letters, numbers, underscore and dash allowed)");
    }

    @Test
    public void publicStudyWithoutExternalIdValidationIsValid() {
        study.setExternalIdValidationEnabled(false);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void publicStudyWithoutExternalIdOnSignUpIsValid() {
        study.setExternalIdRequiredOnSignup(false);
        Validate.entityThrowingException(INSTANCE, study);
    }

    @Test
    public void nonPublicStudiesMustEnableExternalIdValdation() {
        study.setEmailVerificationEnabled(false);
        study.setExternalIdValidationEnabled(false);
        assertValidatorMessage(INSTANCE, study, "externalIdValidationEnabled",
                "cannot be disabled if email verification has been disabled");
    }
    
    @Test
    public void nonPublicStudiesMustRequireExternalIdOnSignUp() {
        study.setEmailVerificationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        assertValidatorMessage(INSTANCE, study, "externalIdRequiredOnSignup",
                "cannot be disabled if email verification has been disabled");
    } 

    @Test
    public void oauthProviderRequiresClientId() {
        OAuthProvider provider = new OAuthProvider(null, "secret", "endpoint", CALLBACK_URL);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].clientId", "is required");
    }

    @Test
    public void oauthProviderRequiresSecret() {
        OAuthProvider provider = new OAuthProvider("clientId", null, "endpoint", CALLBACK_URL);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].secret", "is required");
    }
    
    @Test
    public void oauthProviderRequiresEndpoint() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", null, CALLBACK_URL);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].endpoint", "is required");
    }
    
    @Test
    public void oauthProviderRequiresCallbackUrl() {
        OAuthProvider provider = new OAuthProvider("clientId", "secret", "endpoint", null);
        study.getOAuthProviders().put("vendor", provider);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor].callbackUrl", "is required");
    }
    
    @Test
    public void oauthProviderRequired() {
        study.getOAuthProviders().put("vendor", null);
        assertValidatorMessage(INSTANCE, study, "oauthProviders[vendor]", "is required");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeNull() {
        study.getAppleAppLinks().add(null);
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]","cannot be null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink(null, Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+APP_ID,"cannot be blank or null");
    }
    
    @Test
    public void appleAppLinkAppIdCannotBeDuplicated() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeNull() {
        study.getAppleAppLinks().add(new AppleAppLink("A", null));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathsCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS,"cannot be null or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeNull() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", null)));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }
    
    @Test
    public void appleAppLinkPathCannotBeEmpty() {
        study.getAppleAppLinks().add(new AppleAppLink("A", Lists.newArrayList("*", "")));
        assertValidatorMessage(INSTANCE, study, "appleAppLinks[0]."+PATHS+"[1]","cannot be blank or empty");
    }

    @Test
    public void androidAppLinkNamespaceCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink(null, "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkNamespaceCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+NAMESPACE,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", null, Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkPackageNameCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+PACKAGE_NAME,"cannot be blank or null");
    }
    
    @Test
    public void androidAppLinkIdentifiersCannotBeDuplicated() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("fingerprint")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks","cannot contain duplicate entries");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", null));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintsCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList()));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS,"cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeNull() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList((String)null)));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }

    @Test
    public void androidAppLinkFingerprintCannotBeEmpty() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("  ")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","cannot be null or empty");
    }
    
    @Test
    public void androidAppLinkFingerprintCannotBeInvalid() {
        study.getAndroidAppLinks().add(new AndroidAppLink("appId", "packageName", Lists.newArrayList("asdf")));
        assertValidatorMessage(INSTANCE, study, "androidAppLinks[0]."+FINGERPRINTS+"[0]","is not a SHA 256 fingerprint");
    }
    
    @Test
    public void installAppLinksCannotBeNull() {
        study.getInstallLinks().put("foo", "");
        assertValidatorMessage(INSTANCE, study, "installLinks", "cannot be blank");
    }
    
    @Test
    public void installAppLinksCannotExceedSMSLength() {
        String msg = "";
        for (int i=0; i < BridgeConstants.SMS_CHARACTER_LIMIT; i++) {
            msg += "A";
        }
        msg += "A";
        study.getInstallLinks().put("foo", msg);
        assertValidatorMessage(INSTANCE, study, "installLinks", "cannot be longer than "+BridgeConstants.SMS_CHARACTER_LIMIT+" characters");
    }
    
    @Test
    public void resetPasswordSmsTemplateCanBeNull() {
        study.setResetPasswordSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void phoneSignInSmsTemplateCanBeNull() {
        study.setPhoneSignInSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void appInstallLinkSmsTemplateCanBeNull() {
        study.setAppInstallLinkSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void verifyPhoneSmsTemplateCanBeNull() {
        study.setVerifyPhoneSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void signedConsentSmsTemplateCanBeNull() {
        study.setSignedConsentSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void accountExistsSmsTemplateCanBeNull() {
        study.setAccountExistsSmsTemplate(null);
        Validate.entityThrowingException(INSTANCE, study);
    }
    
    @Test
    public void resetPasswordSmsTemplateMessageRequired() {
        study.setResetPasswordSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "resetPasswordSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void phoneSignInSmsTemplateMessageRequired() {
        study.setPhoneSignInSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "phoneSignInSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void appInstallLinkSmsTemplateMessageRequired() {
        study.setAppInstallLinkSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "appInstallLinkSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void verifyPhoneSmsTemplateMessageRequired() {
        study.setVerifyPhoneSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "verifyPhoneSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void signedConsentSmsTemplateMessageRequired() {
        study.setSignedConsentSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "signedConsentSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void accountExistsSmsTemplateMessageRequired() {
        study.setAccountExistsSmsTemplate(new SmsTemplate(null));
        assertValidatorMessage(INSTANCE, study, "accountExistsSmsTemplate.message", "cannot be blank");
    }
    
    @Test
    public void resetPasswordSmsTemplateHasMaxLength() {
        study.setResetPasswordSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "resetPasswordSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void phoneSignInSmsTemplateHasMaxLength() {
        study.setPhoneSignInSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "phoneSignInSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void appInstallLinkSmsTemplateHasMaxLength() {
        study.setAppInstallLinkSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "appInstallLinkSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void verifyPhoneSmsTemplateHasMaxLength() {
        study.setVerifyPhoneSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "verifyPhoneSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void signedConsentSmsTemplateHasMaxLength() {
        study.setSignedConsentSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "signedConsentSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void accountExistsSmsTemplateHasMaxLength() {
        study.setAccountExistsSmsTemplate(new SmsTemplate(TOO_LONG_STRING));
        assertValidatorMessage(INSTANCE, study, "accountExistsSmsTemplate.message", "cannot be more than 160 characters");
    }
    
    @Test
    public void resetPasswordSmsTemplateRequiresTemplateVar() {
        study.setResetPasswordSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "resetPasswordSmsTemplate.message", "must contain one of these template variables: ${url}, ${resetPasswordUrl}");
    }
    
    @Test
    public void phoneSignInSmsTemplateRequiresTemplateVar() {
        study.setPhoneSignInSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "phoneSignInSmsTemplate.message", "must contain one of these template variables: ${token}");
    }
    
    @Test
    public void appInstallLinkSmsTemplateRequiresTemplateVar() {
        study.setAppInstallLinkSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "appInstallLinkSmsTemplate.message", "must contain one of these template variables: ${url}, ${appInstallUrl}");
    }
    
    @Test
    public void verifyPhoneSmsTemplateRequiresTemplateVar() {
        study.setVerifyPhoneSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "verifyPhoneSmsTemplate.message", "must contain one of these template variables: ${token}");
    }
    
    @Test
    public void signedConsentSmsTemplateRequiresTemplateVar() {
        study.setSignedConsentSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "signedConsentSmsTemplate.message", "must contain one of these template variables: ${consentUrl}");
    }
    
    @Test
    public void accountExistsSmsTemplateRequiresTemplateVar() {
        study.setAccountExistsSmsTemplate(new SmsTemplate("content"));
        assertValidatorMessage(INSTANCE, study, "accountExistsSmsTemplate.message", "must contain one of these template variables: ${token}, ${resetPasswordUrl}");
    }
    
    @Test
    public void signedConsentTemplateRequiresNoTemplateVars() {
        study.setSignedConsentTemplate(new EmailTemplate("subject", "no template var", MimeType.TEXT));
        Validate.entityThrowingException(INSTANCE, study);
    }
}
