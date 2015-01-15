package org.sagebionetworks.bridge.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.crypto.PemUtils;
import org.sagebionetworks.bridge.models.upload.ArchiveEntry;
import org.sagebionetworks.bridge.services.UploadArchiveService;

/**
 * <p>
 * Quick and dirty upload downloader.
 * </p>
 * <p>
 * Usage: play "run-main org.sagebionetworks.bridge.util.BridgeUploadDownloader [ISO start datetime] [ISO end
 * datetime]"
 * </p>
 * <p>
 * If no datetimes were given, it defaults to the last 24 hours.
 * </p>
 */
public class BridgeUploadDownloader {
    public static void main(String[] args) {
        // parse start and end time
        DateTime startTime;
        DateTime endTime;
        if (args.length == 2) {
            startTime = DateTime.parse(args[0]);
            endTime = DateTime.parse(args[1]);
        } else if (args.length == 0) {
            endTime = DateTime.now();
            startTime = endTime.minusDays(1);
        } else {
            System.out.println("Usage: play \"run-main org.sagebionetworks.bridge.util.BridgeUploadDownloader [ISO start datetime] [ISO end datetime]\"");
            System.out.println("If no datetimes were given, it defaults to the last 24 hours.");
            System.exit(1);
            return;
        }

        // Make tmp directory (if it doesn't exist). This has to be in the source root, since we're probably running
        // this script in Vagrant.
        File tmpDir = new File("tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdir();
        }

        // old code copied from Eric's stuff
        final BridgeConfig config = BridgeConfigFactory.getConfig();
        final String key = config.getProperty("aws.key");
        final String secret = config.getProperty("aws.secret.key");
        final AmazonS3 s3Client = new AmazonS3Client(new BasicAWSCredentials(key, secret));
        Map<String, String> certPems = getPems(s3Client, "org-sagebridge-upload-cms-cert-uat");
        Map<String, String> privPems = getPems(s3Client, "org-sagebridge-upload-cms-priv-uat");
        Map<String, UploadArchiveService> services = new HashMap<>();
        for (String study : certPems.keySet()) {
            String certPem = certPems.get(study);
            String privPem = privPems.get(study);
            UploadArchiveService service = new UploadArchiveService(
                    PemUtils.loadCertificateFromPem(certPem),
                    PemUtils.loadPrivateKeyFromPem(privPem));
            services.put(study, service);
        }
        System.out.println(services.size() + " services loaded.");
        ObjectMapper mapper = new ObjectMapper();
        List<UploadObject> uploads = getUploads(s3Client, "org-sagebridge-upload-uat", startTime, endTime);
        System.out.println("Found " + uploads.size() + " uploads.");
        for (UploadObject uploadObj : uploads) {
            for (String study : services.keySet()) {
                UploadArchiveService service = services.get(study);
                try {
                    List<ArchiveEntry> archives = service.decryptAndUnzip(uploadObj.upload);
                    ObjectNode json = mapper.createObjectNode();
                    for (ArchiveEntry archive : archives) {
                        json.putPOJO(archive.getName(), archive.getContent());
                    }
                    File file = new File(tmpDir, study + "-" + uploadObj.timestamp + ".json");
                    mapper.writeValue(file, json);
                } catch (Exception e) {
                    System.out.println(study + " " + e.getMessage());
                }
            }
        }
    }

    private static Map<String, String> getPems(final AmazonS3 s3Client, final String bucket) {
        Map<String, String> pems = new HashMap<>();
        ObjectListing objListing = s3Client.listObjects(bucket);
        List<S3ObjectSummary> objList = objListing.getObjectSummaries();
        for (S3ObjectSummary obj : objList) {
            // filter out test pems
            if (obj.getKey().startsWith("sdk-")) {
                continue;
            }

            S3Object s3Obj = s3Client.getObject(obj.getBucketName(), obj.getKey());
            try (InputStream is = s3Obj.getObjectContent()) {
                String pem = IOUtils.toString(is);
                String key = s3Obj.getKey();
                key = key.substring(0, key.indexOf("."));
                pems.put(key, pem);
                System.out.println("Added pem for " + key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return pems;
    }

    private static List<UploadObject> getUploads(final AmazonS3 s3Client, final String bucket,
            final DateTime startTime, final DateTime endTime) {
        List<UploadObject> uploads = new ArrayList<>();
        ObjectListing objListing = s3Client.listObjects(bucket);
        do {
            List<S3ObjectSummary> objList = objListing.getObjectSummaries();
            for (S3ObjectSummary obj : objList) {
                try (S3Object s3Obj = s3Client.getObject(obj.getBucketName(), obj.getKey());
                        InputStream is = s3Obj.getObjectContent()) {
                    ObjectMetadata metadata = s3Obj.getObjectMetadata();
                    final Date timestamp = metadata.getLastModified();
                    if (!timestamp.before(startTime.toDate()) && !timestamp.after(endTime.toDate())) {
                        byte[] upload = IOUtils.toByteArray(is);
                        uploads.add(new UploadObject(upload, timestamp.getTime()));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } while (objListing.isTruncated() && (objListing = s3Client.listObjects(new ListObjectsRequest()
                .withBucketName(bucket)
                .withMarker(objListing.getNextMarker()))) != null);
        return uploads;
    }

    private static class UploadObject {
        private byte[] upload;
        private long timestamp;

        private UploadObject(byte[] upload, long timestamp) {
            this.upload = upload;
            this.timestamp = timestamp;
        }
    }
}
