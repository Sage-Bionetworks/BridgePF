package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.CONFIG_KEY_UDD_SQS_QUEUE_URL;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_END_DATE;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_START_DATE;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_STUDY_ID;
import static org.sagebionetworks.bridge.services.UserDataDownloadViaSqsService.REQUEST_KEY_USERNAME;

import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class UserDataDownloadViaSqsServiceTest {
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
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("test-study");
        User user = new User();
        user.setUsername("test-username");
        DateRange dateRange = new DateRange(LocalDate.parse("2015-08-15"), LocalDate.parse("2015-08-19"));

        // execute
        testService.requestUserData(studyIdentifier, user, dateRange);

        // Validate SQS args. It's a JSON struct, but we can convert it into a map.
        String sqsMessageText = sqsMessageCaptor.getValue();
        Map<String, Object> sqsMessageMap = BridgeObjectMapper.get().readValue(sqsMessageText,
                JsonUtils.TYPE_REF_RAW_MAP);

        assertEquals("test-study", sqsMessageMap.get(REQUEST_KEY_STUDY_ID));
        assertEquals("test-username", sqsMessageMap.get(REQUEST_KEY_USERNAME));
        assertEquals("2015-08-15", sqsMessageMap.get(REQUEST_KEY_START_DATE));
        assertEquals("2015-08-19", sqsMessageMap.get(REQUEST_KEY_END_DATE));
    }
}
