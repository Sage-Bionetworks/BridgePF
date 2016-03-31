package org.sagebionetworks.bridge.dynamodb;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.BasicAWSCredentials;
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
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamoInitializer {

    private static Logger LOG = LoggerFactory.getLogger(DynamoInitializer.class);

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();

    private static final AmazonDynamoDB DYNAMO;
    private static final DataPipelineClient DATA_PIPELINE_CLIENT;

    private static final LocalTime BACKUP_SCHEDULED_TIME = LocalTime.of(1, 0);
    private static final Region DYNAMO_REGION = Region.getRegion(Regions.US_EAST_1);

    // Backups go in bucket named org-sagebridge-dynamo-backup-ENV-USER
    private static final String DYNAMO_BACKUP_BUCKET_PREFIX = "org-sagebridge-dynamo-backup-";

    static {
        String awsKey = CONFIG.getProperty("aws.key");
        String secretKey = CONFIG.getProperty("aws.secret.key");
        BasicAWSCredentials credentials = new BasicAWSCredentials(awsKey, secretKey);
        DYNAMO = new AmazonDynamoDBClient(credentials);
        DATA_PIPELINE_CLIENT = new DataPipelineClient(credentials);
    }

    /**
     * Creates DynamoDB tables, if they do not exist yet, from the annotated types. in the package
     * "org.sagebionetworks.bridge.dynamodb". Throws an error if the table exists but the schema (hash key, range key,
     * and secondary indices) does not match.
     */
    public static void init(String dynamoPackage) {
        AnnotationBasedTableCreator tableCreator = new AnnotationBasedTableCreator(CONFIG);
        List<TableDescription> tables = tableCreator.getTables(dynamoPackage);
        init(tables);
    }

    @SafeVarargs
    public static void init(Class<?>... dynamoTables) {
        AnnotationBasedTableCreator tableCreator = new AnnotationBasedTableCreator(CONFIG);
        List<TableDescription> tables = tableCreator.getTables(dynamoTables);
        init(tables);
    }

    private static void init(Collection<TableDescription> tables) {
        beforeInit();
        initTables(tables);
        backupPipelineForTables(tables);
    }

    /**
     * Actions performed before init(), e.g. for phasing out obsolete schemas.
     */
    static void beforeInit() {
    }

    static void deleteTable(Class<?> table) {
        final String tableName = DynamoUtils.getFullyQualifiedTableName(table, CONFIG);
        try {
            DescribeTableResult tableResult = DYNAMO.describeTable(tableName);
            TableDescription tableDscr = tableResult.getTable();
            String status = tableDscr.getTableStatus();
            if (TableStatus.DELETING.toString().equalsIgnoreCase(status)) {
                return;
            } else if (!TableStatus.ACTIVE.toString().equalsIgnoreCase(status)) {
                // Must be active to be deleted
                DynamoUtils.waitForActive(DYNAMO, tableDscr.getTableName());
            }
            LOG.info("Deleting table " + tableName);
            DYNAMO.deleteTable(tableName);
            DynamoUtils.waitForDelete(DYNAMO, tableDscr.getTableName());
            LOG.info("Table " + tableName + " deleted.");
        } catch (ResourceNotFoundException e) {
            LOG.warn("Table " + tableName + " does not exist.");
        }
    }

    static void backupPipelineForTables(final Collection<TableDescription> tables) {
        Environment env = CONFIG.getEnvironment();

        String envAndUser = env.name().toLowerCase() + "-" + CONFIG.getUser();

        String pipelineName = "Dynamo backup for bridge: " + envAndUser;
        String pipelineUniqueId = "dynamo-backup-bridge." + envAndUser;
        String s3Bucket = DYNAMO_BACKUP_BUCKET_PREFIX + envAndUser;

        List<PipelineObject> pipelineObjects =
                DynamoDataPipelineHelper.createPipelineObjects(DYNAMO_REGION, tables, s3Bucket, BACKUP_SCHEDULED_TIME);

        LOG.debug("Pipeline objects: " + pipelineObjects.toString());

        CreatePipelineResult createPipelineResult = DATA_PIPELINE_CLIENT.createPipeline(new CreatePipelineRequest()
                .withName(pipelineName)
                .withUniqueId(pipelineUniqueId));

        String pipelineId = createPipelineResult.getPipelineId();

        LOG.info("Updating backup pipeline, pipelineId=" + pipelineId);

        PutPipelineDefinitionRequest putPipelineDefinitionRequest =
                new PutPipelineDefinitionRequest().withPipelineId(pipelineId).withPipelineObjects(pipelineObjects);

        PutPipelineDefinitionResult putPipelineDefinitionResult =
                DATA_PIPELINE_CLIENT.putPipelineDefinition(putPipelineDefinitionRequest);
        LOG.debug(putPipelineDefinitionResult.toString());

        if (putPipelineDefinitionResult.isErrored()) {
            LOG.error("Failed to update backup pipeline, " +
                    "putPipelineDefinitionRequest=" + putPipelineDefinitionRequest.toString() + ",  " +
                    "putPipelineDefinitionResult=" + putPipelineDefinitionResult);
        }

        // Add PROD later
        if (Environment.UAT.equals(env)) {
            LOG.info("Activating backup pipeline, pipelineId=" + pipelineId);

            DATA_PIPELINE_CLIENT.activatePipeline(new ActivatePipelineRequest().withPipelineId(pipelineId)).toString();
        }
    }

    private static void initTables(final Collection<TableDescription> tables) {
        Map<String, TableDescription> existingTables = DynamoUtils.getExistingTables(DYNAMO);
        Environment env = CONFIG.getEnvironment();
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
                DYNAMO.createTable(createTableRequest);
            } else {
                final TableDescription existingTable = existingTables.get(table.getTableName());
                DynamoUtils.compareSchema(table, existingTable);
            }
            DynamoUtils.waitForActive(DYNAMO, table.getTableName());
        }
        LOG.info("DynamoDB tables are ready.");
    }
}
