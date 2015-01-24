package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

public interface UploadValidationHandler {
    void handle(@Nonnull UploadValidationContext context) throws UploadValidationException;
}
