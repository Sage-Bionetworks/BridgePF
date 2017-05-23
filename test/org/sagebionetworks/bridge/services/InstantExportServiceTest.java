package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.CONFIG_KEY_EXPORTER_SQS_QUEUE_URL;
import static org.sagebionetworks.bridge.services.InstantExportViaSqsService.REQUEST_TAG;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.config.BridgeConfig;

public class InstantExportServiceTest {
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();

    private BridgeConfig mockConfig;
    private AmazonSQSClient mockSqsClient;
    private ArgumentCaptor<String> sqsMessageCaptor;

    @Before
    public void before() throws Exception {
        // mock config
        mockConfig = mock(BridgeConfig.class);
        when(mockConfig.getProperty(CONFIG_KEY_EXPORTER_SQS_QUEUE_URL)).thenReturn("dummy-sqs-url");

        // mock SQS
        mockSqsClient = mock(AmazonSQSClient.class);
        SendMessageResult mockSqsResult = new SendMessageResult().withMessageId("dummy-message-id");
        sqsMessageCaptor = ArgumentCaptor.forClass(String.class);
        when(mockSqsClient.sendMessage(eq("dummy-sqs-url"), sqsMessageCaptor.capture())).thenReturn(mockSqsResult);
    }

    @Test
    public void testWithLastExportDateTime() throws Exception {
        // set up test service
        InstantExportViaSqsService testService = new InstantExportViaSqsService();
        testService.setBridgeConfig(mockConfig);
        testService.setSqsClient(mockSqsClient);

        // execute
        testService.export(TEST_STUDY);

        // Validate SQS args.
        verify(mockSqsClient).sendMessage(eq("dummy-sqs-url"), any());
        String sqsMessageText = sqsMessageCaptor.getValue();

        JsonNode sqsMessageNode = JSON_OBJECT_MAPPER.readTree(sqsMessageText);

        // first assert parent node
        assertEquals(3, sqsMessageNode.size());

        // then assert args
        assertEquals("INSTANT", sqsMessageNode.get("exportType").textValue());
        assertEquals(REQUEST_TAG, sqsMessageNode.get("tag").textValue());
        JsonNode studyWhitelist = sqsMessageNode.path("studyWhitelist");
        assertEquals(1, studyWhitelist.size());
        if (studyWhitelist.isArray()) {
            JsonNode objNode = studyWhitelist.get(0);
            assertEquals(TEST_STUDY_IDENTIFIER, objNode.textValue());
        } else {
            fail("Study whitelist is not an array.");
        }
    }
}
