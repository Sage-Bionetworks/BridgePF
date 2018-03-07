package org.sagebionetworks.bridge.upload;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.file.FileHelper;
import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * Validation handler for unzipping the upload. This handler reads decrypted from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getDecryptedDataFile}, unzips it, and writes the
 * unzipped data to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUnzippedDataFileMap}.
 */
@Component
public class UnzipHandler implements UploadValidationHandler {
    private FileHelper fileHelper;
    private UploadArchiveService uploadArchiveService;

    /** File helper, used to create the files to unzip to and to get file streams. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** Upload archive service, which handles decrypting and unzipping of files. This is configured by Spring. */
    @Autowired
    public final void setUploadArchiveService(UploadArchiveService uploadArchiveService) {
        this.uploadArchiveService = uploadArchiveService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        Map<String, File> unzippedDataFileMap = new HashMap<>();
        try (InputStream zippedFileInputStream = fileHelper.getInputStream(context.getDecryptedDataFile())) {
            uploadArchiveService.unzip(zippedFileInputStream,
                    entryName -> {
                        File unzippedFile = fileHelper.newFile(context.getTempDir(), entryName);
                        unzippedDataFileMap.put(entryName, unzippedFile);
                        try {
                            return fileHelper.getOutputStream(unzippedFile);
                        } catch (FileNotFoundException ex) {
                            // BiConsumer doesn't throw, so wrap this in a RuntimeException.
                            throw new RuntimeException(ex);
                        }
                    },
                    (entryName, outputStream) -> {
                        try {
                            outputStream.close();
                        } catch (IOException ex) {
                            // BiConsumer doesn't throw, so wrap this in a RuntimeException.
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (IOException ex) {
            throw new UploadValidationException("Error unzipping file: " + ex.getMessage(), ex);
        }
        context.setUnzippedDataFileMap(unzippedDataFileMap);
    }
}
