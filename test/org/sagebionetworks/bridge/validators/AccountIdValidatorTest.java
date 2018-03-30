package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

public class AccountIdValidatorTest {
    @Test
    public void validAccountIdWithEmail() {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, "email@email.com");
        Validate.entityThrowingException(AccountIdValidator.getInstance(ChannelType.EMAIL), accountId);
    }

    @Test
    public void validAccountIdWithPhone() {
        AccountId accountId = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        Validate.entityThrowingException(AccountIdValidator.getInstance(ChannelType.PHONE), accountId);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void validatorUnsupportedType() {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, "email@email.com");
        Validate.entityThrowingException(AccountIdValidator.getInstance(null), accountId);
    }
    
    @Test
    public void studyRequired() throws Exception {
        AccountId accountId = createId("{'email':'email@email.com'}");
        assertValidatorMessage(AccountIdValidator.getInstance(ChannelType.EMAIL), accountId, "study", "is required");
    }
    
    @Test
    public void emailRequired() throws Exception {
        AccountId accountId = createId("{'study':'api'}");
        assertValidatorMessage(AccountIdValidator.getInstance(ChannelType.EMAIL), accountId, "email", "is required");
    }
    
    @Test
    public void phoneRequired() throws Exception {
        AccountId accountId = createId("{'study':'api'}");
        assertValidatorMessage(AccountIdValidator.getInstance(ChannelType.PHONE), accountId, "phone", "is required");
    }
    
    @Test
    public void phoneValid() throws Exception {
        AccountId accountId = createId("{'study':'api','phone':{'number':'4082588569','regionCode':'MX'}}");
        assertValidatorMessage(AccountIdValidator.getInstance(ChannelType.PHONE), accountId, "phone", "does not appear to be a phone number");
    }
    
    private AccountId createId(String json) throws Exception {
        return BridgeObjectMapper.get().readValue(TestUtils.createJson(json), AccountId.class);        
    }
}
