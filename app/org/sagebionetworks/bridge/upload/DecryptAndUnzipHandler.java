package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * Validation handler for decrypting, unzipping, and JSON parsing the upload. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getData}.
 */
// TODO: Separate the decrypt, unzip, and the JSON parsing
@Component
public class DecryptAndUnzipHandler implements UploadValidationHandler {
    private UploadArchiveService uploadArchiveService;

    /** Upload archive service, which handles decrypting and unzipping of files. This is configured by Spring. */
    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        uploadArchiveService.decryptAndUnzip(context.getStudy(), context.getData());
    }
}
