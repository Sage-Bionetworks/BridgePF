package org.sagebionetworks.bridge.services;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Implementation of {@link UserDataDownloadService} that connects to the Bridge User Data Download Service via SQS.
 */
@Component
public class UserDataDownloadViaSqsService implements UserDataDownloadService {
    private static final Logger logger = LoggerFactory.getLogger(UserDataDownloadViaSqsService.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    // constants - these are package scoped so unit tests can access them
    static final String CONFIG_KEY_UDD_SQS_QUEUE_URL = "udd.sqs.queue.url";
    static final String REQUEST_KEY_BODY = "body";
    static final String REQUEST_KEY_END_DATE = "endDate";
    static final String REQUEST_KEY_START_DATE = "startDate";
    static final String REQUEST_KEY_SERVICE = "service";
    static final String REQUEST_KEY_STUDY_ID = "studyId";
    static final String REQUEST_KEY_USER_ID = "userId";
    static final String UDD_SERVICE_TITLE = "UDD";

    private BridgeConfig bridgeConfig;
    private AmazonSQSClient sqsClient;

    /** Bridge config, used to get the SQS queue URL. */
    @Autowired
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
    }

    /** SQS client. */
    @Autowired
    public final void setSqsClient(AmazonSQSClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    /** {@inheritDoc} */
    @Override
    public void requestUserData(StudyIdentifier studyIdentifier, String userId, DateRange dateRange)
            throws JsonProcessingException {
        String studyId = studyIdentifier.getIdentifier();
        String startDateStr = dateRange.getStartDate().toString();
        String endDateStr = dateRange.getEndDate().toString();

        // wrap msg as nested json node
        ObjectNode requestNode = JSON_OBJECT_MAPPER.createObjectNode();
        requestNode.put(REQUEST_KEY_STUDY_ID, studyId);
        requestNode.put(REQUEST_KEY_USER_ID, userId);
        requestNode.put(REQUEST_KEY_START_DATE, startDateStr);
        requestNode.put(REQUEST_KEY_END_DATE, endDateStr);

        ObjectNode requestMsg = JSON_OBJECT_MAPPER.createObjectNode();
        requestMsg.put(REQUEST_KEY_SERVICE, UDD_SERVICE_TITLE);
        requestMsg.set(REQUEST_KEY_BODY, requestNode);

        String requestJson = JSON_OBJECT_MAPPER.writeValueAsString(requestMsg);

        // send to SQS
        String queueUrl = bridgeConfig.getProperty(CONFIG_KEY_UDD_SQS_QUEUE_URL);
        SendMessageResult sqsResult = sqsClient.sendMessage(queueUrl, requestJson);
        logger.info("Sent request to SQS for userId=" + userId + ", study=" + studyId +
                ", startDate=" + startDateStr + ", endDate=" + endDateStr + "; received message ID=" +
                sqsResult.getMessageId());
    }
}
