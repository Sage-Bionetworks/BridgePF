package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Type-safe object representing the study identifier token.
 */
@JsonDeserialize(as=StudyIdentifierImpl.class)
@BridgeTypeName("StudyIdentifier")
public interface StudyIdentifier {
    
    String getIdentifier();

}
