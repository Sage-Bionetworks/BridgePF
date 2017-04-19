package org.sagebionetworks.bridge.services.backfill;

import java.io.IOException;
import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.backfill.BackfillTask;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.UploadService;

/**
 * Created by liujoshua on 7/27/16.
 */
@Component
public class UploadCompletedBackfill extends AsyncBackfillTemplate {
    private static final int LOCK_EXPIRY_IN_SECONDS = 60 * 10;

    private static final double UPLOAD_CHECKS_PER_SECOND = 10.0;
    private static final double UPLOAD_COMPLETIONS_PER_SECOND = 2.0;

    private static final String CONFIG_KEY_UPLOAD_BUCKET = "upload.bucket";
    private static final String UPLOAD_PREFIX_BUCKET =
            "org-sagebridge-backfill-" + BridgeConfigFactory.getConfig().getEnvironment().name().toLowerCase();
    private static final String UPLOAD_PREFIX_FILENAME = "upload-validation-backfill-uploadPrefixes";

    private RateLimiter checkRateLimiter = RateLimiter.create(UPLOAD_CHECKS_PER_SECOND);
    private RateLimiter completionRateLimiter = RateLimiter.create(UPLOAD_COMPLETIONS_PER_SECOND);

    private UploadService uploadService;
    private HealthCodeDao healthCodeDao;
    private AmazonS3 s3Client;
    private S3Helper s3Helper;
    private String uploadBucket;

    @Autowired
    final void setConfig(BridgeConfig config) {
        uploadBucket = config.getProperty(CONFIG_KEY_UPLOAD_BUCKET);
    }

    @Resource(name = "s3Client")
    public void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Autowired
    public void setUploadService(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Autowired
    public void setHealthCodeDao(HealthCodeDao healthCodeDao) {
        this.healthCodeDao = healthCodeDao;
    }
    
    @Override
    int getLockExpireInSeconds() {
        return LOCK_EXPIRY_IN_SECONDS;
    }

    @Override
    void doBackfill(BackfillTask task, BackfillCallback callback) {
        List<String> prefixes;
        try {
            prefixes = s3Helper.readS3FileAsLines(UPLOAD_PREFIX_BUCKET, UPLOAD_PREFIX_FILENAME);
        } catch (IOException ex) {
            // doBackfill() super class doesn't declare exceptions. Wrap this in a RuntimeException.
            throw new RuntimeException(ex);
        }

        for (String prefix : prefixes) {
            for (S3ObjectSummary objectSummary : S3Objects.withPrefix(s3Client, uploadBucket, prefix)) {
                checkRateLimiter.acquire();

                String key = objectSummary.getKey();

                Upload upload = uploadService.getUpload(key);

                if (upload.canBeValidated()) {
                    completionRateLimiter.acquire();

                    String studyId = healthCodeDao.getStudyIdentifier(upload.getHealthCode());
                    uploadService.uploadComplete(new StudyIdentifierImpl(studyId), UploadCompletionClient.S3_SWEEPER, upload);

                    recordMessage(task, callback, "File with key '" + key + "' marked as complete");
                } else {
                    recordMessage(task, callback, "File with key '" + key + "' skipped");
                }
            }
        }
    }
}
