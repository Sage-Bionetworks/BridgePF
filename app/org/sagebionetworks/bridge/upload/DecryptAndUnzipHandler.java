package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.services.UploadArchiveService;

// TODO: Separate the decrypt, unzip, and the JSON parsing
@Component
public class DecryptAndUnzipHandler implements UploadValidationHandler {
    private UploadArchiveService uploadArchiveService;

    @Autowired
    public void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        try {
            uploadArchiveService.decryptAndUnzip(context.getStudy(), context.getData());

            // TODO: write decrypted and unzipped results back into the context
        } catch (BridgeServiceException ex) {
            throw new UploadValidationException(ex);
        }
    }
}
