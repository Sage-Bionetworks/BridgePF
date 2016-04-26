package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class StudyParticipantValidatorTest {
    
    private static final Set<String> STUDY_PROFILE_ATTRS = BridgeUtils.commaListToOrderedSet("attr1,attr2");
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2,bluebell");
    
    private Study study;

    private StudyParticipantValidator validator;
    
    @Before
    public void before() {
        study = new DynamoStudy();
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
        validator = new StudyParticipantValidator(study, true);
        study.setExternalIdValidationEnabled(true);
        
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
            assertError(e, "email", 0, " is required");
            assertError(e, "externalId", 0, " cannot be null or blank");
            assertError(e, "dataGroups", 0, " 'badGroup' is not defined for study (use group1, group2, bluebell)");
            assertError(e, "attributes", 0, " 'badValue' is not defined for study (use attr1, attr2, phone)");
            assertError(e, "password", 0, " must be at least 8 characters");
            assertError(e, "password", 1, " must contain at least one number (0-9)");
            assertError(e, "password", 2, " must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
            assertError(e, "password", 3, " must contain at least one uppercase letter (A-Z)");
        }
    }
    
    // Password, email address, and externalId (if being validated) cannot be updated, so these don't need to be validated.
    @Test
    public void validatesUpdate() {
        validator = new StudyParticipantValidator(study, false);
        
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
            assertError(e, "dataGroups", 0, " 'badGroup' is not defined for study (use group1, group2, bluebell)");
            assertError(e, "attributes", 0, " 'badValue' is not defined for study (use attr1, attr2, phone)");
        }
    }
    
    @Test
    public void validPasses() {
        validator = new StudyParticipantValidator(study, true);
        Validate.entityThrowingException(validator, withEmail("email@email.com"));
        Validate.entityThrowingException(validator, withDataGroup("bluebell"));
    }
    
    @Test
    public void emailRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withEmail(null), "email", "email is required");
    }
    
    @Test
    public void passwordRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword(""), "password", "password is required");
    }
    
    @Test
    public void validEmail() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withEmail("belgium"), "email", "email must be a valid email address");
    }
    
    @Test
    public void minLength() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword("a1A~"), "password", "password must be at least 8 characters");
    }
    
    @Test
    public void numberRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword("aaaaaaaaA~"), "password", "password must contain at least one number (0-9)");
    }
    
    @Test
    public void symbolRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword("aaaaaaaaA1"), "password", 
            "password must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
    }
    
    @Test
    public void lowerCaseRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword("AAAAA!A1"), "password", "password must contain at least one lowercase letter (a-z)");
    }
    
    @Test
    public void upperCaseRequired() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withPassword("aaaaa!a1"), "password", "password must contain at least one uppercase letter (A-Z)");
    }
    
    @Test
    public void validatesDataGroupsValidIfSupplied() {
        validator = new StudyParticipantValidator(study, true);
        assertCorrectMessage(withDataGroup("squirrel"), "dataGroups", "dataGroups 'squirrel' is not defined for study (use group1, group2, bluebell)");
    }
    
    private void assertCorrectMessage(StudyParticipant participant, String fieldName, String message) {
        try {
            Validate.entityThrowingException(validator, participant);
            fail("should have thrown exception");
        } catch(InvalidEntityException e) {
            List<String> errors = e.getErrors().get(fieldName);
            assertFalse(errors == null || errors.isEmpty());
            String error = errors.get(0);
            assertEquals(message, error);
        }
    }
    
    private StudyParticipant withPassword(String password) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword(password).build();
    }
    
    private StudyParticipant withEmail(String email) {
        return new StudyParticipant.Builder().withEmail(email).withPassword("aAz1%_aAz1%").build();
    }
    
    private StudyParticipant withDataGroup(String dataGroup) {
        return new StudyParticipant.Builder().withEmail("email@email.com").withPassword("aAz1%_aAz1%")
                .withDataGroups(Sets.newHashSet(dataGroup)).build();
    }
    
    private void assertError(InvalidEntityException e, String fieldName, int index, String errorMsg) {
        assertEquals(fieldName+errorMsg, e.getErrors().get(fieldName).get(index));
    }
    
    
}
