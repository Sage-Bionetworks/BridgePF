package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsType;
import org.sagebionetworks.bridge.time.DateUtils;

public class DynamoSmsMessageTest {
    private static final String HEALTH_CODE = "health-code";
    private static final String MESSAGE_BODY = "lorem ipsum";
    private static final String MESSAGE_ID = "my-message-id";
    private static final String PHONE_NUMBER = "+12065550123";

    private static final String SENT_ON_STRING = "2018-10-17T13:53:07.883Z";
    private static final long SENT_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SENT_ON_STRING);

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"phoneNumber\":\"" + PHONE_NUMBER + "\",\n" +
                "   \"sentOn\":\"" + SENT_ON_STRING + "\",\n" +
                "   \"healthCode\":\"" + HEALTH_CODE + "\",\n" +
                "   \"messageBody\":\"" + MESSAGE_BODY + "\",\n" +
                "   \"messageId\":\"" + MESSAGE_ID + "\",\n" +
                "   \"smsType\":\"" + SmsType.PROMOTIONAL.getValue().toLowerCase() + "\",\n" +
                "   \"studyId\":\"" + TestConstants.TEST_STUDY_IDENTIFIER + "\"\n" +
                "}";

        // Convert to POJO.
        SmsMessage smsMessage = BridgeObjectMapper.get().readValue(jsonText, SmsMessage.class);
        assertEquals(PHONE_NUMBER, smsMessage.getPhoneNumber());
        assertEquals(SENT_ON_MILLIS, smsMessage.getSentOn());
        assertEquals(HEALTH_CODE, smsMessage.getHealthCode());
        assertEquals(MESSAGE_BODY, smsMessage.getMessageBody());
        assertEquals(MESSAGE_ID, smsMessage.getMessageId());
        assertEquals(SmsType.PROMOTIONAL, smsMessage.getSmsType());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, smsMessage.getStudyId());

        // Convert back to JSON node.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(smsMessage, JsonNode.class);
        assertEquals(PHONE_NUMBER, jsonNode.get("phoneNumber").textValue());
        assertEquals(SENT_ON_STRING, jsonNode.get("sentOn").textValue());
        assertEquals(HEALTH_CODE, jsonNode.get("healthCode").textValue());
        assertEquals(MESSAGE_BODY, jsonNode.get("messageBody").textValue());
        assertEquals(MESSAGE_ID, jsonNode.get("messageId").textValue());
        assertEquals(SmsType.PROMOTIONAL.getValue().toLowerCase(), jsonNode.get("smsType").textValue());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, jsonNode.get("studyId").textValue());
    }
}
