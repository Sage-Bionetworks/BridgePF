package org.sagebionetworks.bridge.models.upload;

public interface UploadFieldDefinition {
    String getName();

    boolean isRequired();

    UploadFieldType getType();
}
