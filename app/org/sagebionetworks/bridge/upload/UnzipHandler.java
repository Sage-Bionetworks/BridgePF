package org.sagebionetworks.bridge.upload;

import java.util.Map;
import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * Validation handler for unzipping the upload. This handler reads decrypted from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getDecryptedData}, unzips it, and writes the
 * unzipped data to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataMap}.
 */
@Component
public class UnzipHandler implements UploadValidationHandler {
    private UploadArchiveService uploadArchiveService;

    /** Upload archive service, which handles decrypting and unzipping of files. This is configured by Spring. */
    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        Map<String, byte[]> unzippedDataMap = uploadArchiveService.unzip(context.getDecryptedData());
        context.setUnzippedDataMap(unzippedDataMap);
    }
}
