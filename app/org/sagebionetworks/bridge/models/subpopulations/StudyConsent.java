package org.sagebionetworks.bridge.models.subpopulations;

import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("StudyConsent")
public interface StudyConsent extends BridgeEntity {
    /** Creates a StudyConsent instance. */
    static StudyConsent create() {
        return new DynamoStudyConsent1();
    }

    /**
     * The subpopulation associated with this consent.
     */
    String getSubpopulationGuid();

    /** @see #getSubpopulationGuid */
    void setSubpopulationGuid(String subpopulationGuid);

    /**
     * Timestamp when this consent is created.
     */
    long getCreatedOn();

    /** @see #getCreatedOn */
    void setCreatedOn(long createdOn);

    /**
     * Get the path to the storage of the consent document (probably in S3).
     */
    String getStoragePath();

    /** @see #getStoragePath */
    void setStoragePath(String storagePath);
}
