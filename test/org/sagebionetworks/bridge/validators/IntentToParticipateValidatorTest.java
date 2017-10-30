package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.validators.IntentToParticipateValidator.INSTANCE;

import org.junit.Test;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;

public class IntentToParticipateValidatorTest {

    private static final String STUDY = "studyId";
    private static final String EMAIL = "email@email.com";
    private static final Phone PHONE = new Phone("4082588569", "US");
    private static final String SUBPOP_GUID = "subpopGuid";
    private static final SharingScope SCOPE = SharingScope.SPONSORS_AND_PARTNERS;
    private static final ConsentSignature SIGNATURE = new ConsentSignature.Builder()
            .withName("Test Name")
            .withBirthdate("1985-02-02")
            .build();
    
    private IntentToParticipate.Builder builder() {
        return new IntentToParticipate.Builder()
                .withStudy(STUDY)
                .withSubpopGuid(SUBPOP_GUID)
                .withScope(SCOPE)
                .withConsentSignature(SIGNATURE);
    }
    
    @Test
    public void validWithEmail() {
        IntentToParticipate intent = builder().withEmail(EMAIL).build();
        Validate.entityThrowingException(INSTANCE, intent);
    }

    @Test
    public void validWithPhone() {
        IntentToParticipate intent = builder().withPhone(PHONE).build();
        Validate.entityThrowingException(INSTANCE, intent);
    }
    
    @Test
    public void invalidWithBothEmailAndPhone() {
        IntentToParticipate intent = builder().withEmail(EMAIL).withPhone(PHONE).build();
        assertValidatorMessage(INSTANCE, intent, "IntentToParticipate",
                "must include email or phone, but not both");
    }
    
    @Test
    public void emailOrPhoneRequired() {
        IntentToParticipate intent = builder().build();
        assertValidatorMessage(INSTANCE, intent, "IntentToParticipate", "must include email or phone");
    }
    
    @Test
    public void studyRequired() {
        IntentToParticipate intent = builder().withEmail(EMAIL).withStudy(null).build();
        assertValidatorMessage(INSTANCE, intent, "study", "is required");
    }
    
    @Test
    public void subpopRequired() {
        IntentToParticipate intent = builder().withEmail(EMAIL).withSubpopGuid("").build();
        assertValidatorMessage(INSTANCE, intent, "subpopGuid", "is required");
    }

    @Test
    public void scopeRequired() {
        IntentToParticipate intent = builder().withEmail(EMAIL).withScope(null).build();
        assertValidatorMessage(INSTANCE, intent, "scope", "is required");
    }
    
    @Test
    public void signatureRequired() {
        IntentToParticipate intent = builder().withEmail(EMAIL).withConsentSignature(null).build();
        assertValidatorMessage(INSTANCE, intent, "consentSignature", "is required");
    }
    
    @Test
    public void phoneIsInvalid() {
        IntentToParticipate intent = builder().withEmail(null).withPhone(new Phone("333333333", "US")).build();
        assertValidatorMessage(INSTANCE, intent, "phone", "does not appear to be a phone number");
    }
}
