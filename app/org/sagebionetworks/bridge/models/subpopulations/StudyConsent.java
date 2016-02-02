package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("StudyConsent")
@JsonDeserialize(as = DynamoStudyConsent1.class)
public interface StudyConsent extends BridgeEntity {

    /**
     * The subpopulation associated with this consent.
     */
    String getSubpopulationGuid();

    /**
     * Timestamp when this consent is created.
     */
    long getCreatedOn();

    /**
     * Whether the consent is active.
     */
    boolean getActive();

    /**
     * Get the path to the storage of the consent document (probably in S3).
     */
    String getStoragePath();

}
