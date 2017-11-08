package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class StudyValidatorTest {

    private static final StudyValidator INSTANCE = StudyValidator.INSTANCE;
    private DynamoStudy study;
    
    @Before
    public void createValidStudy() {
        study = TestUtils.getValidStudy(StudyValidatorTest.class);
    }
    
    @Test
    public void acceptsValidStudy() {
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    // While 2 is not a good length, we must allow it for legacy reasons.
    @Test
    public void minLengthCannotBeLessThan2() {
        study.setPasswordPolicy(new PasswordPolicy(1, false, false, false, false));
        assertValidatorMessage(INSTANCE, study, "passwordPolicy.minLength", "must be 2-999 characters");
    }
    
    @Test
    public void shortNameRequired() {
        study.setShortName("");
        assertValidatorMessage(INSTANCE, study, "shortName", "is required");
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
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.body", "must contain the ${url} template variable");
    }
    
    @Test
    public void verifyEmailMustHaveUrlVariable() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "no url variable", MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.body", "must contain the ${url} template variable");
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test
    public void acceptsEventKeysWithColons() {
        study.setActivityEventKeys(Sets.newHashSet("a-1", "b2"));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCanBeJustADashAndLetter() {
        study.getUserProfileAttributes().add("_A");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void userProfileAttributesCannotBeEmpty() {
        study.getUserProfileAttributes().add("");
        assertValidatorMessage(INSTANCE, study, "userProfileAttributes", "'' must contain only digits, letters, underscores and dashes, and cannot start with a dash");
    }
    
    @Test
    public void requiresMissingConsentNotificationEmail() {
        study.setConsentNotificationEmail(null);
        assertValidatorMessage(INSTANCE, study, "consentNotificationEmail", "is required");
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
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.subject", "is required");
    }

    @Test
    public void requiresVerifyEmailTemplateWithBody() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "verifyEmailTemplate.body", "is required");
    }

    @Test
    public void requiresResetPasswordTemplate() {
        study.setResetPasswordTemplate(null);
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate", "is required");
    }

    @Test
    public void requiresResetPasswordTemplateWithSubject() {
        study.setResetPasswordTemplate(new EmailTemplate("  ", "body", MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.subject", "is required");
    }

    @Test
    public void requiresResetPasswordTemplateWithBody() {
        study.setResetPasswordTemplate(new EmailTemplate("subject", null, MimeType.TEXT));
        assertValidatorMessage(INSTANCE, study, "resetPasswordTemplate.body", "is required");
    }
    
    @Test
    public void emailSignInTemplateNotRequired() {
        study.setEmailSignInTemplate(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test
    public void requiresEmailSignInTemplateWithSubject() {
        study.setEmailSignInTemplate(new EmailTemplate(null, "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.subject", "is required");
    }

    @Test
    public void requiresEmailSignInTemplateWithBody() {
        study.setEmailSignInTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.body", "is required");
    }
    
    @Test
    public void requiresEmailSignInTemplateRequiresToken() {
        study.setEmailSignInTemplate(new EmailTemplate("subject", "body with no token", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "emailSignInTemplate.body", "must contain the ${token} template variable");
    }
    
    @Test
    public void accountExistsTemplateNotRequired() {
        study.setAccountExistsTemplate(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test
    public void requiresAccountExistsTemplateWithSubject() {
        study.setAccountExistsTemplate(new EmailTemplate(null, "body", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.subject", "is required");
    }

    @Test
    public void requiresAccountExistsTemplateWithBody() {
        study.setAccountExistsTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.body", "is required");
    }
    
    @Test
    public void requiresAccountExistsTemplateRequiresURL() {
        study.setAccountExistsTemplate(new EmailTemplate("subject", "body with no url", MimeType.HTML));
        assertValidatorMessage(INSTANCE, study, "accountExistsTemplate.body", "must contain the ${url} template variable");
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
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void longListOfDataGroupsInvalid() {
        study.setDataGroups(Sets.newTreeSet(Lists.newArrayList("Antwerp", "Ghent", "Charleroi", "Liege", "Brussels-City", "Bruges", "Schaerbeek", "Anderlecht", "Namur", "Leuven", "Mons", "Molenbeek-Saint-Jean")));
        assertValidatorMessage(INSTANCE, study, "dataGroups", "will not export to Synapse (string is over 100 characters: 'Anderlecht, Antwerp, Bruges, Brussels-City, Charleroi, Ghent, Leuven, Liege, Molenbeek-Saint-Jean, Mons, Namur, Schaerbeek')");
    }
    
    @Test
    public void dataGroupCharactersRestricted() {
        study.setDataGroups(Sets.newHashSet("Liège"));
        assertValidatorMessage(INSTANCE, study, "dataGroups", "contains invalid tag 'Liège' (only letters, numbers, underscore and dash allowed)");
    }

    @Test
    public void publicStudyWithoutExternalIdValidationIsValid() {
        study.setExternalIdValidationEnabled(false);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void publicStudyWithoutExternalIdOnSignUpIsValid() {
        study.setExternalIdRequiredOnSignup(false);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void nonPublicStudiesMustEnableExternalIdValdation() {
        study.setEmailVerificationEnabled(false);
        study.setExternalIdValidationEnabled(false);
        assertValidatorMessage(INSTANCE, study, "externalIdValidationEnabled", "cannot be disabled if email verification has been disabled");
    }
    
    @Test
    public void nonPublicStudiesMustRequireExternalIdOnSignUp() {
        study.setEmailVerificationEnabled(false);
        study.setExternalIdRequiredOnSignup(false);
        assertValidatorMessage(INSTANCE, study, "externalIdRequiredOnSignup", "cannot be disabled if email verification has been disabled");
    }    
}
