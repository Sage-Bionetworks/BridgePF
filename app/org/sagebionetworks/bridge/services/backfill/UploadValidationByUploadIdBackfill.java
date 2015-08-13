package org.sagebionetworks.bridge.services.backfill;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;

/**
 * <p>
 * Re-drives upload validation for a list of upload IDs. These upload IDs live in a file called
 * upload-validation-backfill-uploadIds in an S3 bucket called org-sagebridge-backfill-prod (replace prod) with your
 * env name.
 * </p>
 * <p>
 * File format is one upload ID per line.
 * </p>
 */
@Component("uploadValidationByUploadIdBackfill")
public class UploadValidationByUploadIdBackfill extends UploadValidationBackfill {
    private static final String UPLOAD_ID_BUCKET = "org-sagebridge-backfill-" + BridgeConfigFactory.getConfig()
            .getEnvironment().name().toLowerCase();
    private static final String UPLOAD_ID_FILENAME = "upload-validation-backfill-uploadIds";

    /** @{inheritDoc} */
    @Override
    protected List<String> getUploadIdList(BackfillTask task, BackfillCallback callback) throws IOException {
        return getS3Helper().readS3FileAsLines(UPLOAD_ID_BUCKET, UPLOAD_ID_FILENAME);
    }
}
