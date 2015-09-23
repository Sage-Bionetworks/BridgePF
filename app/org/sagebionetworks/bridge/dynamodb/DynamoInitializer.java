package org.sagebionetworks.bridge.dynamodb;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;

public class DynamoInitializer {

    private static Logger logger = LoggerFactory.getLogger(DynamoInitializer.class);

    static final long DEFAULT_READ_CAPACITY = 10;
    static final long DEFAULT_WRITE_CAPACITY = 10;

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();

    private static final AmazonDynamoDB DYNAMO;
    static {
        String awsKey = CONFIG.getProperty("aws.key");
        String secretKey = CONFIG.getProperty("aws.secret.key");
        DYNAMO = new AmazonDynamoDBClient(new BasicAWSCredentials(awsKey, secretKey));
    }

    /**
     * Creates DynamoDB tables, if they do not exist yet, from the annotated types. in the package
     * "org.sagebionetworks.bridge.dynamodb". Throws an error if the table exists but the schema (hash key, range key,
     * and secondary indices) does not match.
     */
    public static void init(String dynamoPackage) {
        AnnotationBasedTableCreator tableCreator = new AnnotationBasedTableCreator(CONFIG);
        List<TableDescription> tables = tableCreator.getTables(dynamoPackage);
        beforeInit();
        initTables(tables);
    }

    @SafeVarargs
    public static void init(Class<?>... dynamoTables) {
        AnnotationBasedTableCreator tableCreator = new AnnotationBasedTableCreator(CONFIG);
        List<TableDescription> tables = tableCreator.getTables(dynamoTables);
        beforeInit();
        initTables(tables);
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
            logger.info("Deleting table " + tableName);
            DYNAMO.deleteTable(tableName);
            DynamoUtils.waitForDelete(DYNAMO, tableDscr.getTableName());
            logger.info("Table " + tableName + " deleted.");
        } catch (ResourceNotFoundException e) {
            logger.warn("Table " + tableName + " does not exist.");
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
            logger.info("Existing tables: " + builder.toString());
        }
        for (TableDescription table : tables) {
            if (!existingTables.containsKey(table.getTableName())) {
                CreateTableRequest createTableRequest = DynamoUtils.getCreateTableRequest(table);
                logger.info("Creating table " + table.getTableName());
                DYNAMO.createTable(createTableRequest);
            } else {
                final TableDescription existingTable = existingTables.get(table.getTableName());
                DynamoUtils.compareSchema(table, existingTable);
            }
            DynamoUtils.waitForActive(DYNAMO, table.getTableName());
        }
        logger.info("DynamoDB tables are ready.");
    }
}
