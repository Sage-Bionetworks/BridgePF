package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.*;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.DateRange;

public class UserDataDownloadViaSqsServiceTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    @Test
    public void test() throws Exception {

        // main test strategy is to validate that the args get transformed and sent to SQS as expected

        // mock config
        BridgeConfig mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(CONFIG_KEY_UDD_SQS_QUEUE_URL)).thenReturn("dummy-sqs-url");

        // mock SQS
        AmazonSQSClient mockSqsClient = mock(AmazonSQSClient.class);
        SendMessageResult mockSqsResult = new SendMessageResult().withMessageId("dummy-message-id");
        ArgumentCaptor<String> sqsMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSqsClient.sendMessage(eq("dummy-sqs-url"), sqsMessageCaptor.capture())).thenReturn(mockSqsResult);

        // set up test service
        UserDataDownloadViaSqsService testService = new UserDataDownloadViaSqsService();
        testService.setBridgeConfig(mockConfig);
        testService.setSqsClient(mockSqsClient);

        // test inputs
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-15"), LocalDate.parse("2015-08-19"));

        // execute
        testService.requestUserData(TEST_STUDY, "test-username@email.com", dateRange);

        // Validate SQS args.
        String sqsMessageText = sqsMessageCaptor.getValue();

        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);

        // first assert parent node
        assertEquals(sqsMessageNode.size(), 2);
        assertEquals(sqsMessageNode.get("service").asText(), UDD_SERVICE_TITLE);

        // then assert body node
        JsonNode msgBody = sqsMessageNode.path("body");
        assertEquals(msgBody.size(), 4);

        assertEquals("api", msgBody.get(REQUEST_KEY_STUDY_ID).textValue());
        assertEquals("test-username@email.com", msgBody.get(REQUEST_KEY_USERNAME).textValue());
        assertEquals("2015-08-15", msgBody.get(REQUEST_KEY_START_DATE).textValue());
        assertEquals("2015-08-19", msgBody.get(REQUEST_KEY_END_DATE).textValue());
    }
}
