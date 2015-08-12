package org.sagebionetworks.bridge.services.backfill;

import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

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
        // get file of list of upload IDs
        S3Object uploadIdFile = getS3Client().getObject(UPLOAD_ID_BUCKET, UPLOAD_ID_FILENAME);
        try (BufferedReader uploadIdReader = new BufferedReader(new InputStreamReader(uploadIdFile.getObjectContent(),
                Charsets.UTF_8))) {
            return CharStreams.readLines(uploadIdReader);
        }
    }
}
