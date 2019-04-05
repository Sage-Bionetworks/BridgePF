package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.SubstudyService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class StudyParticipantValidatorTest {
    
    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2,bluebell");
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TestConstants.TEST_STUDY, "id");
    private Study study;

    private StudyParticipantValidator validator;
    
    @Mock
    private ExternalIdService externalIdService;
    
    @Mock
    private SubstudyService substudyService;
    
    private Substudy substudy;
    
    @Before
    public void before() {
        substudy = Substudy.create();
        
        study = Study.create();
        study.setIdentifier("test-study");
        study.setHealthCodeExportEnabled(true);
        study.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        study.setDataGroups(STUDY_DATA_GROUPS);
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.getUserProfileAttributes().add("phone");
        study.setExternalIdValidationEnabled(false);
    }
    
    @Test
    public void validatesNew() throws Exception {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .build();
        assertValidatorMessage(validator, participant, "StudyParticipant", "email, phone, or externalId is required");
        assertValidatorMessage(validator, participant, "externalId", "is required");
        assertValidatorMessage(validator, participant, "dataGroups", "'badGroup' is not defined for study (use group1, group2, bluebell)");
        assertValidatorMessage(validator, participant, "attributes", "'badValue' is not defined for study (use attr1, attr2, phone)");
        assertValidatorMessage(validator, participant, "password", "must be at least 8 characters");
        assertValidatorMessage(validator, participant, "password", "must contain at least one number (0-9)");
        assertValidatorMessage(validator, participant, "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
        assertValidatorMessage(validator, participant, "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    // Password, email address, and externalId (if being validated) cannot be updated, so these don't need to be validated.
    @Test
    public void validatesUpdate() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        
        Map<String,String> attrs = Maps.newHashMap();
        attrs.put("badValue", "value");
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("badGroup"))
                .withAttributes(attrs)
                .withPassword("bad")
                .build();
        
        try {
            Validate.entityThrowingException(validator, participant);
        } catch(InvalidEntityException e) {
            assertNull(e.getErrors().get("email"));
            assertNull(e.getErrors().get("externalId"));
            assertNull(e.getErrors().get("password"));
        }
        assertValidatorMessage(validator, participant, "dataGroups", "'badGroup' is not defined for study (use group1, group2, bluebell)");
        assertValidatorMessage(validator, participant, "attributes", "'badValue' is not defined for study (use attr1, attr2, phone)");
    }
    
    @Test
    public void validatesIdForNew() {
        // not new, this succeeds
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true); 
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void validatesIdForExisting() {
        // not new, this should fail, as there's no ID in participant.
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false); 
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
    }
    
    @Test
    public void validPasses() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
        Validate.entityThrowingException(validator, withDataGroup("bluebell"));
    }
    
    @Test
    public void emailPhoneOrExternalIdRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withEmail(null), "StudyParticipant", "email, phone, or externalId is required");
    }
    
    @Test
    public void emailCannotBeEmptyString() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withEmail(""), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeBlankString() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withEmail("    \n    \t "), "email", "does not appear to be an email address");
    }
    
    @Test
    public void emailCannotBeInvalid() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withEmail("a"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void externalIdOnlyOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId("external-id").build();
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    
    @Test
    public void emptyStringPasswordRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword(""), "password", "is required");
    }
    
    @Test
    public void nullPasswordOK() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, withPassword(null));
    }
    
    @Test
    public void validEmail() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withEmail("belgium"), "email", "does not appear to be an email address");
    }
    
    @Test
    public void minLength() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword("a1A~"), "password", "must be at least 8 characters");
    }
    
    @Test
    public void numberRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword("aaaaaaaaA~"), "password", "must contain at least one number (0-9)");
    }
    
    @Test
    public void symbolRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword("aaaaaaaaA1"), "password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }
    
    @Test
    public void lowerCaseRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword("AAAAA!A1"), "password", "must contain at least one lowercase letter (a-z)");
    }
    
    @Test
    public void upperCaseRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPassword("aaaaa!a1"), "password", "must contain at least one uppercase letter (A-Z)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSupplied() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withDataGroup("squirrel"), "dataGroups", "'squirrel' is not defined for study (use group1, group2, bluebell)");
    }
    
    @Test
    public void validatePhoneRegionRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPhone("1234567890", null), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRegionIsCode() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPhone("1234567890", "esg"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhoneRequired() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPhone(null, "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhonePattern() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPhone("234567890", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void validatePhone() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withPassword("pAssword1@").withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(validator, participant);
    }
    
    @Test
    public void validateTotallyWrongPhone() {
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, withPhone("this isn't a phone number", "US"), "phone", "does not appear to be a phone number");
    }
    
    @Test
    public void createWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(Optional.of(EXT_ID));
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withExternalId("foo");

        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withExternalId("wrong-external-id");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, participant, "externalId", "is not a valid external ID");
    }
    @Test
    public void createWithExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        StudyParticipant participant = withExternalId("foo");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithoutExternalIdManagedOk() {
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withEmail("email@email.com");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithoutExternalIdManagedInvalid() {
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        StudyParticipant participant = withEmail("email@email.com");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, participant, "externalId", "is required");
    }
    @Test
    public void createWithoutExternalIdManagedButHasRolesOK() {
        study.setExternalIdValidationEnabled(true);
        study.setExternalIdRequiredOnSignup(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().withEmail("email@email.com")
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void createWithoutExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        StudyParticipant participant = withEmail("email@email.com");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithExternalIdManagedOk() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), "foo")).thenReturn(Optional.of(EXT_ID));
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withExternalIdAndId("foo");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithExternalIdManagedInvalid() {
        when(externalIdService.getExternalId(any(), any())).thenReturn(Optional.empty());
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withExternalId("does-not-exist");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        assertValidatorMessage(validator, participant, "externalId", "is not a valid external ID");
    }
    @Test
    public void updateWithExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        StudyParticipant participant = withExternalIdAndId("foo");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithoutExternalIdManagedOk() {
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = withEmailAndId("email@email.com");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void updateWithoutExternalIdUnmanagedOk() {
        study.setExternalIdValidationEnabled(false);
        StudyParticipant participant = withEmailAndId("email@email.com");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void emptyExternalIdInvalidOnCreate() {
        StudyParticipant participant = withExternalId(" ");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        assertValidatorMessage(validator, participant, "externalId", "cannot be blank");
    }
    @Test
    public void emptyExternalIdInvalidOnUpdate() {
        StudyParticipant participant = withExternalId(" ");
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, false);
        assertValidatorMessage(validator, participant, "externalId", "cannot be blank");
    }
    @Test
    public void substudyAllowedIfCallerHasNoSubstudies() {
        // In other words, you can "taint" a user with substudies, putting them in a limited security role.
        StudyParticipant participant = withSubstudies("substudyA", "substudyB");
        
        when(substudyService.getSubstudy(study.getStudyIdentifier(), "substudyA", false)).thenReturn(substudy);
        when(substudyService.getSubstudy(study.getStudyIdentifier(), "substudyB", false)).thenReturn(substudy);
        
        validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
        Validate.entityThrowingException(validator, participant);
    }
    @Test
    public void substudyRequiredIfCallerHasSubstudies() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        try {
            // Once a user has a substudy taint, it must be passed to any accounts that user creates (or a subset of it)
            StudyParticipant participant = withEmail("email@email.com");
            
            validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
            assertValidatorMessage(validator, participant, "substudyIds", "must be assigned to this participant");
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    @Test
    public void invalidSubstudyIfCallerHasSubstudies() { 
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyC")).build());
        try {
            // Nor can the user add a participant to substudies that they do not belong to
            StudyParticipant participant = withSubstudies("substudyA", "substudyB");
            
            validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
            assertValidatorMessage(validator, participant, "substudyIds[substudyB]", "is not a substudy of the caller");
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    @Test
    public void subsetOfsubstudiesOK() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyB", "substudyC")).build());
        try {
            // The user (in three substudies) can create a participant in only one of those substudies
            StudyParticipant participant = withSubstudies("substudyB");
            
            when(substudyService.getSubstudy(study.getStudyIdentifier(), "substudyB", false)).thenReturn(substudy);
            
            validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
            Validate.entityThrowingException(validator, participant);
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    @Test
    public void nonexistentSubstudyIds() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyC")).build());
        try {
            StudyParticipant participant = withSubstudies("substudyA");
            
            validator = new StudyParticipantValidator(externalIdService, substudyService, study, true);
            assertValidatorMessage(validator, participant, "substudyIds[substudyA]", "is not a substudy");
        } finally {
            BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
        }
    }
    
    private StudyParticipant withSubstudies(String... substudyIds) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withSubstudyIds(ImmutableSet.copyOf(substudyIds)).build();
        
    }
    private StudyParticipant withPhone(String phone, String phoneRegion) {
        return new StudyParticipant.Builder().withPhone(new Phone(phone, phoneRegion)).build();
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
