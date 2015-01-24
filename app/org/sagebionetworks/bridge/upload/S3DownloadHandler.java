package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;
import javax.annotation.Resource;
import java.io.IOException;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.s3.S3Helper;

@Component
public class S3DownloadHandler implements UploadValidationHandler {
    private static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    private S3Helper s3Helper;

    @Resource(name = "s3Helper")
    public void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        try {
            byte[] s3Bytes = s3Helper.readS3FileAsBytes(UPLOAD_BUCKET, context.getUpload().getObjectId());
            context.setData(s3Bytes);
        } catch (IOException ex) {
            throw new UploadValidationException(ex);
        }
    }
}
