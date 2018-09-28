package org.sagebionetworks.bridge.util;

import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

import org.joda.time.format.ISODateTimeFormat;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoUpload2;
import org.sagebionetworks.bridge.upload.DecryptHandler;
import org.sagebionetworks.bridge.upload.S3DownloadHandler;
import org.sagebionetworks.bridge.upload.UnzipHandler;
import org.sagebionetworks.bridge.upload.UploadValidationContext;

/**
 * <p>
 * Bulk download utility. This downloads from s3 and decrypts (if it's decrypted) and writes the file to disk. This is
 * basically a "do as much as you can" tool parallel to the BridgeUploadDownloader to quick check files, taking in a
 * list of S3 files instead of a date range.
 * </p>
 * <p>
 * Usage: play "run-main org.sagebionetworks.bridge.util.BulkDownloadUtil [S3 key1] [[S3 key2] [S3 key3] ...]"
 * </p>
 * <p>
 * You'll also need to override the upload bucket, the CMS cert bucket, and CMS priv key bucket in your configs.
 * </p>
 */
public class BulkDownloadUtil {
    @SuppressWarnings("resource")
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println(
                    "Usage: play \"run-main org.sagebionetworks.bridge.util.BulkDownloadUtil [S3 key1] [[S3 key2] [S3" +
                            " key3] ...]\"");
            System.exit(1);
            return;
        }
        String[] s3KeyArr = args;

        // Make tmp directory (if it doesn't exist). This has to be in the source root, since we're probably running
        // this script in Vagrant.
        File tmpDir = new File("tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdir();
        }

        System.out.println(String.format("Downloading %s files for S3 keys (%s)", s3KeyArr.length,
                COMMA_SPACE_JOINER.join(s3KeyArr)));

        // spring beans
        AbstractApplicationContext springCtx = new ClassPathXmlApplicationContext("application-context.xml");
        springCtx.registerShutdownHook();

        AmazonDynamoDBClient ddbClient = springCtx.getBean(AmazonDynamoDBClient.class);
        S3DownloadHandler s3DownloadHandler = springCtx.getBean(S3DownloadHandler.class);
        DecryptHandler decryptHandler = springCtx.getBean(DecryptHandler.class);
        UnzipHandler unzipHandler = springCtx.getBean(UnzipHandler.class);

        // DDB mappers
        DynamoDBMapperConfig uploadMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(new DynamoDBMapperConfig.TableNameOverride("prod-heroku-Upload2")).build();
        DynamoDBMapper uploadMapper = new DynamoDBMapper(ddbClient, uploadMapperConfig);

        DynamoDBMapperConfig healthCodeMapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(
                DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(new DynamoDBMapperConfig.TableNameOverride("prod-heroku-HealthCode")).build();
        DynamoDBMapper healthCodeMapper = new DynamoDBMapper(ddbClient, healthCodeMapperConfig);

        // get uploads
        List<UploadObject> uploads = getUploads(uploadMapper, healthCodeMapper, s3KeyArr);
        System.out.println("Found " + uploads.size() + " uploads.");

        // process uploads
        for (UploadObject uploadObj : uploads) {
            // Artificial study, because upload validation handler chain takes in study but only looks at the study ID.
            DynamoStudy study = new DynamoStudy();
            study.setIdentifier(uploadObj.studyId);

            UploadValidationContext ctx = new UploadValidationContext();
            ctx.setStudy(study);
            ctx.setUpload(uploadObj.metadata);

            // Make temp dir within temp dir.
            File uploadTmpDir = new File(tmpDir, uploadObj.metadata.getUploadId());
            ctx.setTempDir(uploadTmpDir);

            // use handlers to process uploads
            try {
                s3DownloadHandler.handle(ctx);
            } catch (Exception ex) {
                System.out.println(String.format(
                        "Error downloading file %s from S3 with uploadId %s from study %s, healthCode %s, timestamp " +
                                "%s: %s",
                        uploadObj.metadata.getFilename(), uploadObj.metadata.getUploadId(), uploadObj.studyId,
                        uploadObj.metadata.getHealthCode(),
                        uploadObj.metadata.getUploadDate().toString(ISODateTimeFormat.date()), ex.getMessage()));
                continue;
            }

            try {
                decryptHandler.handle(ctx);
            } catch (Exception ex) {
                System.out.println(String.format(
                        "Error decrypting file %s with uploadId %s from study %s, healthCode %s, timestamp %s: %s",
                        uploadObj.metadata.getFilename(), uploadObj.metadata.getUploadId(), uploadObj.studyId,
                        uploadObj.metadata.getHealthCode(),
                        uploadObj.metadata.getUploadDate().toString(ISODateTimeFormat.date()), ex.getMessage()));
                System.out.println("Falling back to non-decrypted data.");
                ctx.setDecryptedDataFile(ctx.getDataFile());
            }

            try {
                unzipHandler.handle(ctx);
            } catch (Exception ex) {
                System.out.println(String.format(
                        "Error unzipping file %s with uploadId %s from study %s, healthCode %s, timestamp %s: %s",
                        uploadObj.metadata.getFilename(), uploadObj.metadata.getUploadId(), uploadObj.studyId,
                        uploadObj.metadata.getHealthCode(),
                        uploadObj.metadata.getUploadDate().toString(ISODateTimeFormat.date()), ex.getMessage()));
                System.out.println("Will write zipped file to disk.");
            }
        }
    }

    private static List<UploadObject> getUploads(DynamoDBMapper uploadMapper, DynamoDBMapper healthCodeMapper,
            String[] s3KeyArr) {
        // S3 keys are Upload IDs
        List<Object> uploadKeyList = new ArrayList<>();
        for (String oneS3Key : s3KeyArr) {
            DynamoUpload2 oneUploadKey = new DynamoUpload2();
            oneUploadKey.setUploadId(oneS3Key);
            uploadKeyList.add(oneUploadKey);
        }

        // query DDB for uploads
        Map<String, List<Object>> batchLoadResultMap = uploadMapper.batchLoad(uploadKeyList);
        List<DynamoUpload2> uploadMetadataList = new ArrayList<>();
        for (List<Object> oneResultList : batchLoadResultMap.values()) {
            for (Object oneResult : oneResultList) {
                if (!(oneResult instanceof DynamoUpload2)) {
                    System.out.println(String.format("DDB returned object of type %s instead of DynamoUpload2",
                            oneResult.getClass().getName()));
                    continue;
                }
                uploadMetadataList.add((DynamoUpload2) oneResult);
            }
        }
        System.out.println(String.format("Got %s results from DDB Upload table", uploadMetadataList.size()));

        System.out.println("Downloading files from S3 and cross-referencing study ID from health code...");
        List<UploadObject> uploads = new ArrayList<>();
        for (DynamoUpload2 oneUploadMetadata : uploadMetadataList) {
            String studyId = oneUploadMetadata.getStudyId();

            uploads.add(new UploadObject(oneUploadMetadata, studyId));
        }
        return uploads;
    }

    private static class UploadObject {
        private final DynamoUpload2 metadata;
        private final String studyId;

        private UploadObject(DynamoUpload2 metadata, String studyId) {
            this.metadata = metadata;
            this.studyId = studyId;
        }
    }
}
