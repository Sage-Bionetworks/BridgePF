package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Set;

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
    private static final Set<String> STUDY_DATA_GROUPS = BridgeUtils.commaListToOrderedSet("group1,group2");
    private static final Map<String,String> ATTRS = Maps.newHashMap();
    static {
        ATTRS.put("phone", "123456789");    
    }
    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
        STUDY.setHealthCodeExportEnabled(true);
        STUDY.setUserProfileAttributes(STUDY_PROFILE_ATTRS);
        STUDY.setDataGroups(STUDY_DATA_GROUPS);
        STUDY.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        STUDY.getUserProfileAttributes().add("phone");
        STUDY.setExternalIdValidationEnabled(true);
    }

    private StudyParticipantValidator validator;
    
    @Test
    public void validatesNew() throws Exception {
        validator = new StudyParticipantValidator(STUDY, true);
        
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
            assertError(e, "dataGroups", 0, " 'badGroup' is not defined for study (use group1, group2)");
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
        validator = new StudyParticipantValidator(STUDY, false);
        
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
            assertError(e, "dataGroups", 0, " 'badGroup' is not defined for study (use group1, group2)");
            assertError(e, "attributes", 0, " 'badValue' is not defined for study (use attr1, attr2, phone)");
        }
    }
    
    private void assertError(InvalidEntityException e, String fieldName, int index, String errorMsg) {
        assertEquals(fieldName+errorMsg, e.getErrors().get(fieldName).get(index));
    }
    
    
}
