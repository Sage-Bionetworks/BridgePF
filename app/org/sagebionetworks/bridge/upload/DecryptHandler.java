package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * Validation handler for decrypting the upload. This handler reads from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getData}, decrypts it, and writes the decrypted
 * data back to the same field.
 */
@Component
public class DecryptHandler implements UploadValidationHandler {
    private UploadArchiveService uploadArchiveService;

    /** Upload archive service, which handles decrypting and unzipping of files. This is configured by Spring. */
    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        byte[] decryptedData = uploadArchiveService.decrypt(context.getStudy().getIdentifier(), context.getData());
        context.setData(decryptedData);
    }
}
