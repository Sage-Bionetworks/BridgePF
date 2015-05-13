package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("StudyConsent")
public interface StudyConsent extends BridgeEntity {

    /**
     * The study associated with this consent.
     */
    public String getStudyKey();

    /**
     * Timestamp when this consent is created.
     */
    public long getCreatedOn();

    /**
     * Whether the consent is active.
     */
    public boolean getActive();

    /**
     * Where to find the consent document.
     */
    public String getPath();

    /**
     * Get the path to the storage of the consent document (probably in S3).
     * @return
     */
    public String getStoragePath();

}
