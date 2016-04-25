package org.sagebionetworks.bridge.dynamodb;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.datapipeline.model.ActivatePipelineRequest;
import com.amazonaws.services.datapipeline.model.CreatePipelineRequest;
import com.amazonaws.services.datapipeline.model.CreatePipelineResult;
import com.amazonaws.services.datapipeline.model.DeletePipelineRequest;
import com.amazonaws.services.datapipeline.model.GetPipelineDefinitionRequest;
import com.amazonaws.services.datapipeline.model.GetPipelineDefinitionResult;
import com.amazonaws.services.datapipeline.model.InvalidRequestException;
import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionRequest;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.Sets;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * (Re)creates, updates, and activates data pipeline for backing up dynamo tables for the current environment to S3.
 */
@Component
public class DynamoBackupHandler {
    private static Logger LOG = LoggerFactory.getLogger(DynamoBackupHandler.class);

    public static final String PIPELINE_DESCRIPTION = "Do not modify unless you know what you are doing. This pipeline is automatically " +
            "created and updated by DynamoBackupHandler in Bridge";

    //currently all tables are in the same region
    static final Region DYNAMO_REGION = Region.getRegion(Regions.US_EAST_1);

    // Backups go in bucket named org-sagebridge-dynamo-backup-ENV-USER
    private static final String DYNAMO_BACKUP_BUCKET_PREFIX = "org-sagebridge-dynamo-backup-";
    private static final LocalTime BACKUP_SCHEDULED_TIME = new LocalTime(1, 0);
    private static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    private final BridgeConfig bridgeConfig;
    private final DataPipelineClient dataPipelineClient;

    @Autowired
    public DynamoBackupHandler(BridgeConfig bridgeConfig, DataPipelineClient dataPipelineClient) {
        this.bridgeConfig = bridgeConfig;
        this.dataPipelineClient = dataPipelineClient;
    }

    // Used by tests
    public void deletePipeline() {
        CreatePipelineResult result = dataPipelineClient
                .createPipeline(new CreatePipelineRequest().withName(getPipelineName()).withUniqueId(getPipelineUniqueId()));
        dataPipelineClient.deletePipeline(new DeletePipelineRequest().withPipelineId(result.getPipelineId()));
    }

    public String getPipelineName() {
        Environment env = bridgeConfig.getEnvironment();

        String envAndUser = env.name().toLowerCase() + "-" + bridgeConfig.getUser();

        return "Dynamo backup for bridge: " + envAndUser;
    }

    public String getPipelineUniqueId() {
        Environment env = bridgeConfig.getEnvironment();

        String envAndUser = env.name().toLowerCase() + "-" + bridgeConfig.getUser();

        return "dynamo-backup-bridge." + envAndUser;
    }

    public boolean backupPipelineForTables(Collection<TableDescription> tables) {
        return backupPipelineForTableNames(tables.stream().map(t -> t.getTableName()).collect(Collectors.toList()));
    }

    protected boolean shouldActivatePipeline() {
        Environment env = bridgeConfig.getEnvironment();

        return Environment.UAT.equals(env) || Environment.PROD.equals(env);
    }

    public boolean backupPipelineForTableNames(Collection<String> tableNames) {
        Environment env = bridgeConfig.getEnvironment();

        String envAndUser = env.name().toLowerCase() + "-" + bridgeConfig.getUser();

        String pipelineName = getPipelineName();
        String pipelineUniqueId = getPipelineUniqueId();
        String s3Bucket = DYNAMO_BACKUP_BUCKET_PREFIX + envAndUser;

        CreatePipelineResult createPipelineResult;
        GetPipelineDefinitionResult currentPipeline;

        try {
            //If pipeline with this uniqueId already exists, existing pipeline will be returned
            createPipelineResult = dataPipelineClient
                    .createPipeline(new CreatePipelineRequest().withName(pipelineName).withUniqueId(pipelineUniqueId)
                            .withDescription(PIPELINE_DESCRIPTION));

            currentPipeline = dataPipelineClient
                    .getPipelineDefinition(new GetPipelineDefinitionRequest().withPipelineId(createPipelineResult.getPipelineId()));
        } catch (Exception e) {
            LOG.error("Could not get the backup pipeline", e);
            return false;
        }

        List<PipelineObject> existingPipelineObjects = currentPipeline.getPipelineObjects();

        Optional<PipelineObject> existingSchedule = existingPipelineObjects.stream().filter(o -> "Schedule".equals(o.getId())).findAny();

        List<PipelineObject> desiredPipelineObjects;

        if (existingSchedule.isPresent()) {
            desiredPipelineObjects =
                    DynamoDataPipelineHelper.createPipelineObjects(DYNAMO_REGION, tableNames, s3Bucket, existingSchedule.get());
        } else {
            desiredPipelineObjects = DynamoDataPipelineHelper
                    .createPipelineObjects(DYNAMO_REGION, tableNames, s3Bucket, BACKUP_SCHEDULED_TIME, LOCAL_TIME_ZONE);
        }

        Set<String> existingTables = getDynamoTableNames(existingPipelineObjects);
        Set<String> desiredTables = getDynamoTableNames(desiredPipelineObjects);

        if (existingTables.equals(desiredTables)) {
            return true;
        }

        // if we are removing tables, we need to delete the existing pipeline and create a new one since objects cannot be removed from a
        // previously activated pipeline
        if (!Sets.difference(existingTables, desiredTables).isEmpty()) {
            try {
                dataPipelineClient.deletePipeline(new DeletePipelineRequest().withPipelineId(createPipelineResult.getPipelineId()));
                currentPipeline = null;

                createPipelineResult = dataPipelineClient
                        .createPipeline(new CreatePipelineRequest().withName(pipelineName).withUniqueId(pipelineUniqueId));
            } catch (Exception e) {
                LOG.error("Could not create delete/recreate backup pipeline", e);
                return false;
            }
        }
        if (!safeUpdatePipeline(createPipelineResult.getPipelineId(), desiredPipelineObjects)) {
            return false;
        }

        if (shouldActivatePipeline()) {
            return safeActivatePipeline(createPipelineResult.getPipelineId());
        }

        return true;
    }

    private Set<String> getDynamoTableNames(Collection<PipelineObject> pipelineObjects) {
        return pipelineObjects.stream().filter(o -> {
            Optional<String> type = DynamoDataPipelineHelper.getStringValue(o, "type");
            return type.isPresent() && DynamoDataPipelineHelper.PipelineObjectType.DYNAMO_DATA_NODE.equals(type.get());
        }).map(o -> DynamoDataPipelineHelper.getStringValue(o, "tableName").get()).collect(Collectors.toCollection(TreeSet::new));
    }

    private boolean safeUpdatePipeline(String pipelineId, Collection<PipelineObject> pipelineObjects) {
        LOG.info("Updating backup pipeline, pipelineId=" + pipelineId);

        PutPipelineDefinitionRequest putPipelineDefinitionRequest =
                new PutPipelineDefinitionRequest().withPipelineId(pipelineId).withPipelineObjects(pipelineObjects);

        try {
            PutPipelineDefinitionResult putPipelineDefinitionResult =
                    dataPipelineClient.putPipelineDefinition(putPipelineDefinitionRequest);
            if (putPipelineDefinitionResult.isErrored()) {
                LOG.error("Failed to update backup pipeline, " +
                        "putPipelineDefinitionRequest=" + putPipelineDefinitionRequest + ",  " +
                        "putPipelineDefinitionResult=" + putPipelineDefinitionResult);

                return false;
            }
        } catch (Exception e) {
            LOG.error("Failed to update backup pipeline", e);
            return false;
        }

        return true;
    }

    private boolean safeActivatePipeline(String pipelineId) {
        LOG.info("Activating backup pipeline, pipelineId=" + pipelineId);
        try {
            dataPipelineClient.activatePipeline(new ActivatePipelineRequest().withPipelineId(pipelineId)).toString();
        } catch (InvalidRequestException e) {
            LOG.warn("Failed to activate backup pipeline", e);
            return false;
        }
        return true;
    }

}
