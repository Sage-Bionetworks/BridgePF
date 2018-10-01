package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class StudyParticipantValidatorTest {
    
    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2,bluebell");
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TestConstants.TEST_STUDY, "id");
    private Study study;

    private StudyParticipantValidator createValidator;
    
    private StudyParticipantValidator updateValidator;
    
    @Mock
    private ExternalIdService externalIdService;
    
    private Account account;
    
    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier("test-study");
        study.setHealthCodeExportEnabled(true);
        study.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        study.setDataGroups(STUDY_DATA_GROUPS);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.getUserProfileAttributes().add("phone");
        study.setExternalIdValidationEnabled(false);
        
        account = Account.create();
        createValidator = new StudyParticipantValidator(externalIdService, study, null);
        updateValidator = new StudyParticipantValidator(externalIdService, study, account);
    }
    
    @Test
    public void validatesNew() throws Exception {
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .build();
        
        assertValidatorMessage(createValidator, participant, "externalId", "is required");
        assertValidatorMessage(createValidator, participant, "externalId", "is required");
        assertValidatorMessage(createValidator, participant, "dataGroups", "'badGroup' is not defined for study (use group1, group2, bluebell)");
        assertValidatorMessage(createValidator, participant, "attributes", "'badValue' is not defined for study (use attr1, attr2, phone)");
        assertValidatorMessage(createValidator, participant, "password", "must be at least 8 characters");
        assertValidatorMessage(createValidator, participant, "password", "must contain at least one number (0-9)");
        assertValidatorMessage(createValidator, participant, "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
        assertValidatorMessage(createValidator, participant, "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    // Password, email address, and externalId (if being validated) cannot be updated, so these don't need to be validated.
    @Test
    public void validatesUpdate() {
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .withPhone(new Phone("badphone", "12"))
                .build();
        
        assertValidatorMessage(createValidator, participant, "dataGroups", "'badGroup' is not defined for study (use group1, group2, bluebell)");
        assertValidatorMessage(createValidator, participant, "attributes", "'badValue' is not defined for study (use attr1, attr2, phone)");
        assertValidatorMessage(createValidator, participant, "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatesIdForNew() {
        // New, this succeeds
        Validate.entityThrowingException(createValidator, withEmail("email@email.com"));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validatesIdForExisting() {
        // not new, this should fail, as there's no ID in participant.
        Validate.entityThrowingException(updateValidator, withEmail("email@email.com"));
    }
    
    @Test
    public void validPasses() {
        Validate.entityThrowingException(createValidator, withEmail("email@email.com"));
        Validate.entityThrowingException(createValidator, withDataGroup("bluebell"));
    }
    
    @Test
    public void emailPhoneOrExternalIdRequiredOnCreate() {
        assertValidatorMessage(createValidator, withEmail(null), "StudyParticipant", "email, phone, or externalId is required");
    }
    
    @Test
    public void emailPhoneOrExternalIdRequiredOnUpdate() {
        assertValidatorMessage(updateValidator, withEmail(null), "StudyParticipant", "email, phone, or externalId is required");
    }
    
    @Test
    public void emailCannotBeEmptyStringOnCreate() {
        assertValidatorMessage(createValidator, withEmail(""), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeEmptyStringOnUpdate() {
        assertValidatorMessage(updateValidator, withEmail(""), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeBlankStringOnCreate() {
        assertValidatorMessage(createValidator, withEmail("    \n    \t "), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeBlankStringOnUpdate() {
        assertValidatorMessage(updateValidator, withEmail("    \n    \t "), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeInvalidOnCreate() {
        assertValidatorMessage(createValidator, withEmail("a"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeInvalidOnUpdate() {
        assertValidatorMessage(createValidator, withEmail("a"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void externalIdOnlyOKOnCreate() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("external-id").build();
        Validate.entityThrowingException(createValidator, participant);
    }
    
    @Test
    public void externalIdOnlyOKOnUpdate() {
        StudyParticipant participant = new StudyParticipant.Builder().withId("id").withExternalId("external-id").build();
        Validate.entityThrowingException(updateValidator, participant);
    }
    
    @Test
    public void idRequiredOnUpdate() {
        assertValidatorMessage(updateValidator, withExternalId("externalId"), "id", "is required");
    }
    
    @Test
    public void validEmailOnCreate() {
        assertValidatorMessage(createValidator, withEmail("belgium"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void validEmailOnUpdate() {
        assertValidatorMessage(updateValidator, withEmail("belgium"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emptyStringPasswordRequired() {
        assertValidatorMessage(createValidator, withPassword(""), "password", "is required");
    }
    
    @Test
    public void nullPasswordOK() {
        Validate.entityThrowingException(createValidator, withPassword(null));
    }
    
    @Test
    public void minLength() {
        assertValidatorMessage(createValidator, withPassword("a1A~"), "password", "must be at least 8 characters");
    }
    
    @Test
    public void numberRequired() {
        assertValidatorMessage(createValidator, withPassword("aaaaaaaaA~"), "password", "must contain at least one number (0-9)");
    }
    
    @Test
    public void symbolRequired() {
        assertValidatorMessage(createValidator, withPassword("aaaaaaaaA1"), "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }
    
    @Test
    public void lowerCaseRequired() {
        assertValidatorMessage(createValidator, withPassword("AAAAA!A1"), "password", "must contain at least one lowercase letter (a-z)");
    }
    
    @Test
    public void upperCaseRequired() {
        assertValidatorMessage(createValidator, withPassword("aaaaa!a1"), "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSuppliedOnCreate() {
        assertValidatorMessage(createValidator, withDataGroup("squirrel"), "dataGroups", "'squirrel' is not defined for study (use group1, group2, bluebell)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSuppliedOnUpdate() {
        assertValidatorMessage(updateValidator, withDataGroup("squirrel"), "dataGroups", "'squirrel' is not defined for study (use group1, group2, bluebell)");
    }
    
    @Test
    public void validatePhoneRegionRequiredOnCreate() {
        assertValidatorMessage(createValidator, withPhone("1234567890", null), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRegionRequiredOnUpdate() {
        assertValidatorMessage(updateValidator, withPhone("1234567890", null), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRegionIsCodeOnCreate() {
        assertValidatorMessage(createValidator, withPhone("1234567890", "esg"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRegionIsCodeOnUpdate() {
        assertValidatorMessage(updateValidator, withPhone("1234567890", "esg"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRequired() {
        assertValidatorMessage(createValidator, withPhone(null, "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhonePatternOnCreate() {
        assertValidatorMessage(createValidator, withPhone("234567890", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhonePatternOnUpdate() {
        assertValidatorMessage(updateValidator, withPhone("234567890", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneOnCreate() {
        Validate.entityThrowingException(createValidator, withPhone(TestConstants.PHONE));
    }
    
    @Test
    public void validatePhoneOnUpdate() {
        StudyParticipant participant = new StudyParticipant.Builder().withId("id").withPhone(TestConstants.PHONE)
                .build();
        Validate.entityThrowingException(updateValidator, participant);
    }
    
    @Test
    public void validateTotallyWrongPhoneOnCreate() {
        assertValidatorMessage(createValidator, withPhone("this isn't a phone number", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validateTotallyWrongPhoneOnUpdate() {
        assertValidatorMessage(updateValidator, withPhone("this isn't a phone number", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void createWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);

        Validate.entityThrowingException(createValidator, withExternalId("foo"));
    }
    @Test
    public void createWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);
        
        assertValidatorMessage(createValidator, withExternalId("wrong-external-id"), "externalId", "is not a valid external ID");
    }
    @Test
    public void createWithExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        
        Validate.entityThrowingException(createValidator, withExternalId("foo"));
    }
    @Test
    public void createWithoutExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);
        
        Validate.entityThrowingException(createValidator, withEmail("email@email.com"));
    }
    @Test
    public void createWithoutExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        
        assertValidatorMessage(createValidator, withEmail("email@email.com"), "externalId", "is required");
    }
    @Test
    public void createWithoutExternalIdManagedButHasRolesOK() {
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        
        Validate.entityThrowingException(createValidator, participant);
    }
    @Test
    public void createWithoutExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        
        Validate.entityThrowingException(createValidator, withEmail("email@email.com"));
    }
    @Test
    public void updateWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);

        Validate.entityThrowingException(updateValidator, withExternalIdAndId("foo"));
    }
    @Test
    public void updateWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);
        
        assertValidatorMessage(updateValidator, withExternalId("does-not-exist"), "externalId", "is not a valid external ID");
    }
    @Test
    public void updateWithExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        
        Validate.entityThrowingException(updateValidator, withExternalIdAndId("foo"));
    }
    @Test
    public void updateWithoutExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(EXT_ID);
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withEmailAndId("email@email.com");
        
        Validate.entityThrowingException(updateValidator, participant);
    }
    @Test
    public void updateWithoutExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        
        Validate.entityThrowingException(updateValidator, withEmailAndId("email@email.com"));
    }
    @Test
    public void updateManagedExternalIdCannotDelete() {
        study.setExternalIdValidationEnabled(true);
        account.setExternalId("external-id");
        
        assertValidatorMessage(updateValidator, withExternalId(null), "externalId", "cannot be deleted");
    }
    @Test
    public void updateExternalIdCanBeDeleted() {
        study.setExternalIdValidationEnabled(false);
        account.setExternalId("external-id");
        
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE).withId("id").build();
        Validate.entityThrowingException(createValidator, participant);
    }
    @Test
    public void emptyExternalIdInvalidOnCreate() {
        assertValidatorMessage(updateValidator, withExternalId(" "), "externalId", "cannot be blank");
    }
    @Test
    public void emptyExternalIdInvalidOnUpdate() {
        assertValidatorMessage(updateValidator, withExternalId(" "), "externalId", "cannot be blank");
    }
    @Test
    public void externalIdRequiredUnmanagedOnCreate() {
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(true);
        
        assertValidatorMessage(createValidator, withEmail("email@email.com"), "externalId", "is required");
    }
    @Test
    public void externalIdUnmanagedNotRequiredOnUpdate() {
        // The existing account has an external ID, the submitted account removes it, this is OK because it is 
        // not managed, it was only required on sign up. This is just our choice on how the externalIdRequiredOnSignUp 
        // flag works, it could work differently.
        account.setExternalId("externalId");
        
        study.setExternalIdValidationEnabled(false);
        study.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com").withId("id").build();
        Validate.entityThrowingException(updateValidator, participant);
    }
    
    private StudyParticipant withPhone(String phone, String phoneRegion) {
        return new StudyParticipant.Builder().withPhone(new Phone(phone, phoneRegion)).build();
    }
    
    private StudyParticipant withPhone(Phone phone) {
        return new StudyParticipant.Builder().withPhone(phone).build();
    }
    
    private StudyParticipant withPassword(String password) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword(password).build();
    }
    
    private StudyParticipant withEmail(String email) {
        return new StudyParticipant.Builder().withEmail(email).withPassword("aAz1%_aAz1%").build();
    }
    
    private StudyParticipant withEmailAndId(String email) {
        return new StudyParticipant.Builder().withId("id").withEmail(email).withPassword("aAz1%_aAz1%").build();
    }
    
    private StudyParticipant withExternalId(String externalId) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withExternalId(externalId).build();
    }
    
    private StudyParticipant withExternalIdAndId(String externalId) {
        return new StudyParticipant.Builder().withId("id").withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withExternalId(externalId).build();
    }
    
    private StudyParticipant withDataGroup(String dataGroup) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withDataGroups(Sets.newHashSet(dataGroup)).build();
    }
}
