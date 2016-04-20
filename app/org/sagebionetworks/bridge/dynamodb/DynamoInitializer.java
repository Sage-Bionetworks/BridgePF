package org.sagebionetworks.bridge.dynamodb;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.datapipeline.DataPipelineClient;
import com.amazonaws.services.datapipeline.model.ActivatePipelineRequest;
import com.amazonaws.services.datapipeline.model.CreatePipelineRequest;
import com.amazonaws.services.datapipeline.model.CreatePipelineResult;
import com.amazonaws.services.datapipeline.model.PipelineObject;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionRequest;
import com.amazonaws.services.datapipeline.model.PutPipelineDefinitionResult;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoInitializer {

    private static Logger LOG = LoggerFactory.getLogger(DynamoInitializer.class);

    static final Region DYNAMO_REGION = Region.getRegion(Regions.US_EAST_1);
    // Backups go in bucket named org-sagebridge-dynamo-backup-ENV-USER
    private static final String DYNAMO_BACKUP_BUCKET_PREFIX = "org-sagebridge-dynamo-backup-";
    private static final LocalTime BACKUP_SCHEDULED_TIME = new LocalTime(1, 0);
    private static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    private final BridgeConfig bridgeConfig;
    private final AmazonDynamoDB dynamoDBClient;
    private final DataPipelineClient dataPipelineClient;

    @Autowired
    public DynamoInitializer(BridgeConfig bridgeConfig,
                             AmazonDynamoDBClient dynamoDBClient,
                             DataPipelineClient dataPipelineClient) {
        this.bridgeConfig = bridgeConfig;
        this.dynamoDBClient = dynamoDBClient;
        this.dataPipelineClient = dataPipelineClient;
    }

    /**
     * Creates DynamoDB tables, if they do not exist yet. Throws an error if the table exists but the schema (hash key, range key,
     * and secondary indices) does not match.
     */
    public void init(Collection<TableDescription> tables) {
        initTables(tables);
        backupPipelineForTables(tables.stream().map(t -> t.getTableName()).collect(Collectors.toList()));
    }

    void deleteTable(Class<?> table) {
        final String tableName = DynamoUtils.getFullyQualifiedTableName(table, bridgeConfig);
        try {
            DescribeTableResult tableResult = dynamoDBClient.describeTable(tableName);
            TableDescription tableDscr = tableResult.getTable();
            String status = tableDscr.getTableStatus();
            if (TableStatus.DELETING.toString().equalsIgnoreCase(status)) {
                return;
            } else if (!TableStatus.ACTIVE.toString().equalsIgnoreCase(status)) {
                // Must be active to be deleted
                DynamoUtils.waitForActive(dynamoDBClient, tableDscr.getTableName());
            }
            LOG.info("Deleting table " + tableName);
            dynamoDBClient.deleteTable(tableName);
            DynamoUtils.waitForDelete(dynamoDBClient, tableDscr.getTableName());
            LOG.info("Table " + tableName + " deleted.");
        } catch (ResourceNotFoundException e) {
            LOG.warn("Table " + tableName + " does not exist.");
        }
    }

    private void backupPipelineForTables(final Collection<String> tables) {
        Environment env = bridgeConfig.getEnvironment();

        String envAndUser = env.name().toLowerCase() + "-" + bridgeConfig.getUser();

        String pipelineName = "Dynamo backup for bridge: " + envAndUser;
        String pipelineUniqueId = "dynamo-backup-bridge." + envAndUser;
        String s3Bucket = DYNAMO_BACKUP_BUCKET_PREFIX + envAndUser;

        List<PipelineObject> pipelineObjects =
                DynamoDataPipelineHelper.createPipelineObjects(DYNAMO_REGION, tables, s3Bucket, BACKUP_SCHEDULED_TIME, LOCAL_TIME_ZONE);

        LOG.debug("Pipeline objects: " + pipelineObjects.toString());

        //If pipeline with this uniqueId already exists, existing pipeline will be returned
        CreatePipelineResult createPipelineResult =
                dataPipelineClient.createPipeline(new CreatePipelineRequest().withName(pipelineName).withUniqueId(pipelineUniqueId));

        String pipelineId = createPipelineResult.getPipelineId();

        LOG.info("Updating backup pipeline, pipelineId=" + pipelineId);

        PutPipelineDefinitionRequest putPipelineDefinitionRequest =
                new PutPipelineDefinitionRequest().withPipelineId(pipelineId).withPipelineObjects(pipelineObjects);

        PutPipelineDefinitionResult putPipelineDefinitionResult = dataPipelineClient.putPipelineDefinition(putPipelineDefinitionRequest);
        LOG.debug(putPipelineDefinitionResult.toString());

        if (putPipelineDefinitionResult.isErrored()) {
            LOG.error("Failed to update backup pipeline, " +
                    "putPipelineDefinitionRequest=" + putPipelineDefinitionRequest.toString() + ",  " +
                    "putPipelineDefinitionResult=" + putPipelineDefinitionResult);
        }

        // Add PROD later
        if (Environment.UAT.equals(env)) {
            LOG.info("Activating backup pipeline, pipelineId=" + pipelineId);

            dataPipelineClient.activatePipeline(new ActivatePipelineRequest().withPipelineId(pipelineId)).toString();
        }
    }

    private void initTables(final Collection<TableDescription> tables) {
        Map<String, TableDescription> existingTables = DynamoUtils.getExistingTables(dynamoDBClient);
        Environment env = bridgeConfig.getEnvironment();
        if (Environment.UAT.equals(env) || Environment.PROD.equals(env)) {
            StringBuilder builder = new StringBuilder("[");
            for (Map.Entry<String, TableDescription> entry : existingTables.entrySet()) {
                builder.append("(");
                builder.append(entry.getKey());
                builder.append(", ");
                builder.append(entry.getValue().getTableName());
                builder.append(", ");
                builder.append(entry.getValue().getTableStatus());
                builder.append("), ");
            }
            builder.append("]");
            LOG.info("Existing tables: " + builder.toString());
        }
        for (TableDescription table : tables) {
            if (!existingTables.containsKey(table.getTableName())) {
                CreateTableRequest createTableRequest = DynamoUtils.getCreateTableRequest(table);
                LOG.info("Creating table " + table.getTableName());
                dynamoDBClient.createTable(createTableRequest);
            } else {
                final TableDescription existingTable = existingTables.get(table.getTableName());
                DynamoUtils.compareSchema(table, existingTable);
            }
            DynamoUtils.waitForActive(dynamoDBClient, table.getTableName());
        }
        LOG.info("DynamoDB tables are ready.");
    }
}
