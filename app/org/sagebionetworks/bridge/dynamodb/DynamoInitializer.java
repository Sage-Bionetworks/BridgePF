package org.sagebionetworks.bridge.dynamodb;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;

public class DynamoInitializer {

    private static Logger logger = LoggerFactory.getLogger(DynamoInitializer.class);

    // DynamoDB Free tier
    private static final Long READ_CAPACITY = Long.valueOf(10);
    private static final Long WRITE_CAPACITY = Long.valueOf(5);

    /**
     * Creates DynamoDB tables, if they do not exist yet, from the annotated types.
     * Throws an error if the table exists but the schema does not match.
     */
    public static void init() {
        List<TableDescription> tables = parseTables();
        initTables(tables);
    }

    static List<TableDescription> parseTables() {
        final List<TableDescription> tables = new ArrayList<TableDescription>();
        for (Class<?> clazz : DynamoTable.class.getClasses()) {
            if (clazz.isAnnotationPresent(DynamoDBTable.class)) {
                final List<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
                final List<AttributeDefinition> attributes = new ArrayList<AttributeDefinition>();
                final List<GlobalSecondaryIndexDescription> globalIndices = new ArrayList<GlobalSecondaryIndexDescription>();
                final List<LocalSecondaryIndexDescription> localIndices = new ArrayList<LocalSecondaryIndexDescription>();
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(DynamoDBHashKey.class)) {
                        DynamoDBHashKey hashKeyAttr = method.getAnnotation(DynamoDBHashKey.class);
                        String attrName = hashKeyAttr.attributeName();
                        if (attrName == null || attrName.isEmpty()) {
                            attrName = getAttributeName(method);
                        }
                        KeySchemaElement hashKey = new KeySchemaElement(attrName, KeyType.HASH);
                        keySchema.add(hashKey);
                    } else if (method.isAnnotationPresent(DynamoDBRangeKey.class)) {
                        DynamoDBRangeKey rangeKeyAttr = method.getAnnotation(DynamoDBRangeKey.class);
                        String attrName = rangeKeyAttr.attributeName();
                        if (attrName == null || attrName.isEmpty()) {
                            attrName = getAttributeName(method);
                        }
                        KeySchemaElement rangeKey = new KeySchemaElement(attrName, KeyType.RANGE);
                        keySchema.add(rangeKey);
                    } else if (method.isAnnotationPresent(DynamoDBIndexRangeKey.class)) {
                        DynamoDBIndexRangeKey indexKey = method.getAnnotation(DynamoDBIndexRangeKey.class);
                        String attrName = indexKey.attributeName();
                        if (attrName == null || attrName.isEmpty()) {
                            attrName = getAttributeName(method);
                        }
                        // TODO
                        indexKey.localSecondaryIndexName();
                        indexKey.globalSecondaryIndexName();
                    } else if (method.isAnnotationPresent(DynamoDBAttribute.class)) {
                        DynamoDBAttribute attr = method.getAnnotation(DynamoDBAttribute.class);
                        String attrName = attr.attributeName();
                        if (attrName == null || attrName.isEmpty()) {
                            attrName = getAttributeName(method);
                        }
                        ScalarAttributeType attrType = getAttributeType(method);
                        AttributeDefinition attribute = new AttributeDefinition(attrName, attrType);
                        attributes.add(attribute);
                    }
                }
                final BridgeConfig config = BridgeConfigFactory.getConfig();
                final String tableName = config.getEnvironment() + "-" + config.getUser() + "-" +
                        clazz.getAnnotation(DynamoDBTable.class).tableName();
                final TableDescription table = (new TableDescription())
                        .withTableName(tableName)
                        .withKeySchema(keySchema)
                        .withAttributeDefinitions(attributes)
                        .withGlobalSecondaryIndexes(globalIndices)
                        .withLocalSecondaryIndexes(localIndices)
                        .withProvisionedThroughput((new ProvisionedThroughputDescription())
                                .withReadCapacityUnits(READ_CAPACITY)
                                .withWriteCapacityUnits(WRITE_CAPACITY));
                tables.add(table);
            }
        }
        return tables;
    }

    static String getAttributeName(Method method) {
        String attrName = method.getName();
        if (attrName.startsWith("get")) {
            attrName = attrName.substring("get".length());
            if (attrName.length() > 0) {
                // Make sure the first letter is lower case
                char[] chars = attrName.toCharArray();
                chars[0] = Character.toLowerCase(chars[0]);
                attrName = new String(chars);
            }
        }
        return attrName;
    }

    static ScalarAttributeType getAttributeType(Method method) {
        Class<?> clazz = method.getReturnType();
        if (String.class.equals(clazz)) {
            return ScalarAttributeType.S;
        } else if (Long.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (Integer.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (Boolean.class.equals(clazz)) {
            return ScalarAttributeType.B;
        }
        throw new RuntimeException("Unsupported return type of method " + method.getName());
    }

    static void initTables(final List<TableDescription> tables) {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String awsKey = config.getProperty("aws.key");
        String secretKey = config.getProperty("aws.secret.key");
        AmazonDynamoDB dynamo = new AmazonDynamoDBClient(new BasicAWSCredentials(awsKey, secretKey));
        Map<String, TableDescription> existingTables = getExistingTables(dynamo);
        for (TableDescription table : tables) {
            if (!existingTables.containsKey(table.getTableName())) {
                CreateTableRequest createTableRequest = getCreateTableRequest(table);
                logger.info("Creating table " + table.getTableName());
                dynamo.createTable(createTableRequest);
            } else {
                checkSchema(table, existingTables.get(table.getTableName()));
            }
        }
        // Wait for tables to be ready (status is ACTIVE)
        existingTables = getExistingTables(dynamo);
        logger.info("All DynamoDB tables are ready.");
    }

    static Map<String, TableDescription> getExistingTables(AmazonDynamoDB dynamo) {
        Map<String, TableDescription> existingTables = new HashMap<String, TableDescription>();
        ListTablesResult listResult = dynamo.listTables();
        for (String tableName : listResult.getTableNames()) {
            DescribeTableResult describeResult = dynamo.describeTable(new DescribeTableRequest(tableName));
            TableDescription table = describeResult.getTable();
            while (!TableStatus.ACTIVE.name().equalsIgnoreCase(table.getTableStatus())) {
                describeResult = dynamo.describeTable(new DescribeTableRequest(tableName));
                table = describeResult.getTable();
            }
            existingTables.put(tableName, table);
            List<LocalSecondaryIndexDescription> indices = table.getLocalSecondaryIndexes();
            for (LocalSecondaryIndexDescription index : indices) {
                System.out.println(index.getIndexName());
                System.out.println(index.getProjection());
                System.out.println(index.getKeySchema());
            }
        }
        return existingTables;
    }

    static CreateTableRequest getCreateTableRequest(TableDescription table) {
        // TODO
        return null;
    }

    static void checkSchema(TableDescription table1, TableDescription table2) {
        // TODO
    }
}
