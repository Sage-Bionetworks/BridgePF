package org.sagebionetworks.bridge.services;

import javax.annotation.Nonnull;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Implementation of {@link InstantExportService} that connects to the Bridge Exporter Service via SQS.
 */
@Component
public class InstantExportViaSqsService implements InstantExportService{
    private static final Logger logger = LoggerFactory.getLogger(InstantExportViaSqsService.class);

    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    // constants - these are package scoped so unit tests can access them
    static final String CONFIG_KEY_EXPORTER_SQS_QUEUE_URL = "exporter.request.sqs.queue.url";
    static final String REQUEST_KEY_STUDY_WHITE_LIST = "studyWhitelist";
    static final String REQUEST_KEY_EXPORT_TYPE = "exportType";
    static final String REQUEST_TAG = "BridgePF Instant Export Request";

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
    public void export(@Nonnull StudyIdentifier studyIdentifier)
            throws JsonProcessingException {

        String studyId = studyIdentifier.getIdentifier();

        // wrap msg as nested json node
        ObjectNode requestNode = JSON_OBJECT_MAPPER.createObjectNode();

        ArrayNode studyWhitelist = JSON_OBJECT_MAPPER.createArrayNode();
        studyWhitelist.add(studyId);

        requestNode.set(REQUEST_KEY_STUDY_WHITE_LIST, studyWhitelist);
        requestNode.put(REQUEST_KEY_EXPORT_TYPE, "INSTANT");
        requestNode.put("tag", REQUEST_TAG + "-requested on: " + DateTime.now() + ", study to export: " + studyWhitelist.toString());

        String requestJson = JSON_OBJECT_MAPPER.writeValueAsString(requestNode);

        // send to SQS
        String queueUrl = bridgeConfig.getProperty(CONFIG_KEY_EXPORTER_SQS_QUEUE_URL);
        SendMessageResult sqsResult = sqsClient.sendMessage(queueUrl, requestJson);
        logger.info("Sent exporting request to SQS for hash[username]=" + ", study=" + studyId +
                "; received message ID=" +
                sqsResult.getMessageId());
    }
}
