package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.io.File;

import org.sagebionetworks.bridge.file.FileHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.s3.S3Helper;

/**
 * Validation handler for downloading the upload from S3. This handler reads
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getUpload} and writes the downloaded data (as a
 * byte array) to {@link org.sagebionetworks.bridge.upload.UploadValidationContext#setDataFile}.
 */
@Component
public class S3DownloadHandler implements UploadValidationHandler {
    private static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    private FileHelper fileHelper;
    private S3Helper s3Helper;

    /** File helper, used to manage the temp file that we download the S3 file into. */
    @Autowired
    public final void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    /** S3 helper, for downloading bytes from S3. This is configured by Spring. */
    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) {
        // Temp file name in the form "[uploadId]-encrypted"
        String destFilename = context.getUploadId() + "-encrypted";
        File destFile = fileHelper.newFile(context.getTempDir(), destFilename);
        s3Helper.downloadS3File(UPLOAD_BUCKET, context.getUpload().getObjectId(), destFile);
        context.setDataFile(destFile);
    }
}
