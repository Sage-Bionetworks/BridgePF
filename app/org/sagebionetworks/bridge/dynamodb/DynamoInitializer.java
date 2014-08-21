package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;

public class DynamoInitializer {

    private static Logger logger = LoggerFactory.getLogger(DynamoInitializer.class);

    // DynamoDB Free tier
    private static final Long READ_CAPACITY = Long.valueOf(10);
    private static final Long WRITE_CAPACITY = Long.valueOf(5);

    private static final BridgeConfig CONFIG = BridgeConfigFactory.getConfig();

    private static final AmazonDynamoDB DYNAMO;
    static {
        String awsKey = CONFIG.getProperty("aws.key");
        String secretKey = CONFIG.getProperty("aws.secret.key");
        DYNAMO = new AmazonDynamoDBClient(new BasicAWSCredentials(awsKey, secretKey));
    }

    /**
     * Creates DynamoDB tables, if they do not exist yet, from the annotated types.
     * in the package "org.sagebionetworks.bridge.dynamodb". Throws an error
     * if the table exists but the schema (hash key, range key, and secondary indices)
     * does not match.
     */
    public static void init(String dynamoPackage) {
        beforeInit();
        List<TableDescription> tables = getAnnotatedTables(dynamoPackage);
        initTables(tables);
    }

    /**
     * For phasing out obsolete schemas.
     */
    static void beforeInit() {
        deleteTable("StudyConsent");
        deleteTable("UserConsent1");
    }

    static void deleteTable(String table) {
        table = getTableName(table);
        try {
            DescribeTableResult tableResult = DYNAMO.describeTable(table);
            TableDescription tableDscr = tableResult.getTable();
            String status = tableDscr.getTableStatus();
            if (TableStatus.DELETING.toString().equalsIgnoreCase(status)) {
                return;
            } else if (!TableStatus.ACTIVE.toString().equalsIgnoreCase(status)) {
                waitForActive(tableDscr);
            }
            logger.info("Deleting table " + table);
            DYNAMO.deleteTable(table);
            waitForDelete(tableDscr);
            logger.info("Table " + table + " deleted.");
        } catch(ResourceNotFoundException e) {
            logger.warn("Table " + table + " does not exist.");
        }
    }

    /**
     * Prefix the table name with '{env}-' and '{user}-'.
     */
    static String getTableName(String table) {
        Environment env = CONFIG.getEnvironment();
        return env.getEnvName() + "-" + CONFIG.getUser() + "-" + table;
    }

    /**
     * Converts the annotated DynamoDBTable types to a list of TableDescription.
     */
    static List<TableDescription> getAnnotatedTables(final String dynamoPackage) {
        List<TableDescription> tables = new ArrayList<TableDescription>();
        List<Class<?>> classes = loadDynamoTableClasses(dynamoPackage);
        for (Class<?> clazz : classes) {
            final List<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
            final List<AttributeDefinition> attributes = new ArrayList<AttributeDefinition>();
            final List<GlobalSecondaryIndexDescription> globalIndices = new ArrayList<GlobalSecondaryIndexDescription>();
            final List<LocalSecondaryIndexDescription> localIndices = new ArrayList<LocalSecondaryIndexDescription>();
            Method[] methods = clazz.getDeclaredMethods();
            KeySchemaElement hashKey = null;
            for (Method method : methods) {
                if (method.isAnnotationPresent(DynamoDBHashKey.class)) {
                    // Hash key
                    DynamoDBHashKey hashKeyAttr = method.getAnnotation(DynamoDBHashKey.class);
                    String attrName = hashKeyAttr.attributeName();
                    if (attrName == null || attrName.isEmpty()) {
                        attrName = getAttributeName(method);
                    }
                    hashKey = new KeySchemaElement(attrName, KeyType.HASH);
                    keySchema.add(0, hashKey);
                    ScalarAttributeType attrType = getAttributeType(method);
                    AttributeDefinition attribute = new AttributeDefinition(attrName, attrType);
                    attributes.add(attribute);
                } else if (method.isAnnotationPresent(DynamoDBRangeKey.class)) {
                    // Range key
                    DynamoDBRangeKey rangeKeyAttr = method.getAnnotation(DynamoDBRangeKey.class);
                    String attrName = rangeKeyAttr.attributeName();
                    if (attrName == null || attrName.isEmpty()) {
                        attrName = getAttributeName(method);
                    }
                    KeySchemaElement rangeKey = new KeySchemaElement(attrName, KeyType.RANGE);
                    keySchema.add(rangeKey);
                    ScalarAttributeType attrType = getAttributeType(method);
                    AttributeDefinition attribute = new AttributeDefinition(attrName, attrType);
                    attributes.add(attribute);
                }
            }
            if (hashKey == null) {
                throw new RuntimeException("Missing hash key for DynamoDBTable " + clazz);
            }
            // TODO: Global secondary indices
            // Local secondary indices
            for (Method method : methods) {
                if (method.isAnnotationPresent(DynamoDBIndexRangeKey.class)) {
                    DynamoDBIndexRangeKey indexKey = method.getAnnotation(DynamoDBIndexRangeKey.class);
                    String attrName = indexKey.attributeName();
                    if (attrName == null || attrName.isEmpty()) {
                        attrName = getAttributeName(method);
                    }
                    ScalarAttributeType attrType = getAttributeType(method);
                    AttributeDefinition attribute = new AttributeDefinition(attrName, attrType);
                    attributes.add(attribute);
                    String indexName = indexKey.localSecondaryIndexName();
                    LocalSecondaryIndexDescription localIndex = new LocalSecondaryIndexDescription()
                            .withIndexName(indexName)
                            .withKeySchema(hashKey, new KeySchemaElement(attrName, KeyType.RANGE))
                            .withProjection(new Projection().withProjectionType(ProjectionType.ALL));
                    localIndices.add(localIndex);
                }
            }
            final String tableName = getTableName(
                    clazz.getAnnotation(DynamoDBTable.class).tableName());
            // Create the table description
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
        return tables;
    }

    /**
     * Uses reflection to get all the annotated DynamoDBTable.
     */
    static List<Class<?>> loadDynamoTableClasses(final String dynamoPackage) {
        List<Class<?>> classes = new ArrayList<Class<?>>();
        try {
            ImmutableSet<ClassInfo> classSet = ClassPath
                    .from(DynamoTable.class.getClassLoader())
                    .getTopLevelClasses(dynamoPackage);
            for (ClassInfo classInfo : classSet) {
                Class<?> clazz = classInfo.load();
                if (clazz.isAnnotationPresent(DynamoDBTable.class)) {
                    classes.add(clazz);
                }
            }
            return classes;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets attribute name from the method.
     */
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
        } else if (JsonNode.class.equals(clazz)) {
            return ScalarAttributeType.S;
        } else if (Long.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (long.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (Integer.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (int.class.equals(clazz)) {
            return ScalarAttributeType.N;
        } else if (Boolean.class.equals(clazz)) {
            return ScalarAttributeType.B;
        }
        throw new RuntimeException("Unsupported return type " + clazz + " of method " + method.getName());
    }

    static void initTables(final List<TableDescription> tables) {
        Map<String, TableDescription> existingTables = getExistingTables();
        for (TableDescription table : tables) {
            if (!existingTables.containsKey(table.getTableName())) {
                CreateTableRequest createTableRequest = getCreateTableRequest(table);
                logger.info("Creating table " + table.getTableName());
                DYNAMO.createTable(createTableRequest);
            } else {
                compareSchema(table, existingTables.get(table.getTableName()));
            }
            waitForActive(table);
        }
        logger.info("All DynamoDB tables are ready.");
    }

    static Map<String, TableDescription> getExistingTables() {
        Map<String, TableDescription> existingTables = new HashMap<String, TableDescription>();
        ListTablesResult listResult = DYNAMO.listTables();
        for (String tableName : listResult.getTableNames()) {
            DescribeTableResult describeResult = DYNAMO.describeTable(new DescribeTableRequest(tableName));
            TableDescription table = describeResult.getTable();
            existingTables.put(tableName, table);
        }
        return existingTables;
    }

    static CreateTableRequest getCreateTableRequest(TableDescription table) {
        CreateTableRequest request = new CreateTableRequest()
                .withTableName(table.getTableName())
                .withKeySchema(table.getKeySchema())
                .withAttributeDefinitions(table.getAttributeDefinitions());
        // ProvisionedThroughputDescription -> ProvisionedThroughput
        ProvisionedThroughput throughput = new ProvisionedThroughput(
                table.getProvisionedThroughput().getReadCapacityUnits(),
                table.getProvisionedThroughput().getWriteCapacityUnits());
        request.setProvisionedThroughput(throughput);
        // TODO: GlobalSecondaryIndexDescription -> GlobalSecondaryIndex
        // LocalSecondaryIndexDescription -> LocalSecondaryIndex
        List<LocalSecondaryIndex> localIndices = new ArrayList<LocalSecondaryIndex>();
        List<LocalSecondaryIndexDescription> localIndexDescs = table.getLocalSecondaryIndexes();
        for (LocalSecondaryIndexDescription localIndexDesc : localIndexDescs) {
            LocalSecondaryIndex localIndex = new LocalSecondaryIndex()
                    .withIndexName(localIndexDesc.getIndexName())
                    .withKeySchema(localIndexDesc.getKeySchema())
                    .withProjection(localIndexDesc.getProjection());
            localIndices.add(localIndex);
        }
        if (localIndices.size() > 0) {
            request.setLocalSecondaryIndexes(localIndices);
        }
        return request;
    }

    /**
     * Compares hash key, range key, secondary indices of the two tables. Throws an exception
     * if there is difference.
     */
    static void compareSchema(TableDescription table1, TableDescription table2) {
        if (table1.getTableName().equals(table2.getTableName())) {
            compareKeySchema(table1, table2);
            compareLocalIndices(table1, table2);
        }
    }

    static void compareKeySchema(TableDescription table1, TableDescription table2) {
        List<KeySchemaElement> keySchema1 = table1.getKeySchema();
        List<KeySchemaElement> keySchema2 = table2.getKeySchema();
        compareKeySchema(keySchema1, keySchema2);
    }

    private static void compareKeySchema(List<KeySchemaElement> keySchema1, List<KeySchemaElement> keySchema2) {
        if (keySchema1.size() != keySchema2.size()) {
            throw new RuntimeException("Key schemas have different number of key elements.");
        }
        Map<String, KeySchemaElement> keySchemaMap1 = new HashMap<String, KeySchemaElement>();
        for (KeySchemaElement ele1 : keySchema1) {
            keySchemaMap1.put(ele1.getAttributeName(), ele1);
        }
        for (KeySchemaElement ele2 : keySchema2) {
            KeySchemaElement ele1 = keySchemaMap1.get(ele2.getAttributeName());
            if (ele1 == null) {
                throw new RuntimeException("Missing key " + ele2.getAttributeName() + " in schema 1.");
            }
            if (!ele1.equals(ele2)) {
                throw new RuntimeException("Different key schema for key " + ele2.getAttributeName());
            }
        }
    }

    static void compareGlobalIndices(TableDescription table1, TableDescription table2) {
        // TODO
    }

    static void compareLocalIndices(TableDescription table1, TableDescription table2) {
        List<LocalSecondaryIndexDescription> indices1 = table1.getLocalSecondaryIndexes();
        List<LocalSecondaryIndexDescription> indices2 = table2.getLocalSecondaryIndexes();
        // Check for size first
        if (indices1 == null || indices1.size() == 0) {
            if (indices2 != null && indices2.size() > 0) {
                throw new RuntimeException("Table " + table1.getTableName() +
                        " is changing the number of local indices.");
            }
            if (indices2 == null) {
                return;
            }
        }
        if (indices2 == null || indices2.size() == 0) {
            if (indices1 != null && indices1.size() > 0) {
                throw new RuntimeException("Table " + table1.getTableName() +
                        " is changing the number of local indices.");
            }
            if (indices1 == null) {
                return;
            }
        }
        if (indices1.size() != indices2.size()) {
            throw new RuntimeException("Table " + table1.getTableName() +
                    " is changing the number of local indices.");
        }
        // Check one by one
        Map<String, LocalSecondaryIndexDescription> indexMap1 = new HashMap<String, LocalSecondaryIndexDescription>();
        for (LocalSecondaryIndexDescription index1 : indices1) {
            indexMap1.put(index1.getIndexName(), index1);
        }
        for (LocalSecondaryIndexDescription index2 : indices2) {
            LocalSecondaryIndexDescription index1 = indexMap1.get(index2.getIndexName());
            if (index1 == null) {
                throw new RuntimeException("Table " + table1.getTableName() +
                        " is changing the local indices.");
            }
            compareKeySchema(index1.getKeySchema(), index2.getKeySchema());
            if (!index1.getProjection().equals(index2.getProjection())) {
                throw new RuntimeException("Table " + table1.getTableName() +
                        " local index " + index2.getIndexName() + " is changing the projection.");
            }
        }
    }

    static void waitForActive(TableDescription table) {
        while (!TableStatus.ACTIVE.name().equalsIgnoreCase(table.getTableStatus())) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw new RuntimeException("Shouldn't be interrupted.", e);
            }
            DescribeTableResult describeResult = DYNAMO.describeTable(
                    new DescribeTableRequest(table.getTableName()));
            table = describeResult.getTable();
        }
    }

    static void waitForDelete(TableDescription table) {
        while (true) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                throw new RuntimeException("Shouldn't be interrupted.", e);
            }
            try {
                DescribeTableResult describeResult = DYNAMO.describeTable(
                        new DescribeTableRequest(table.getTableName()));
                table = describeResult.getTable();
            } catch (ResourceNotFoundException e) {
                return;
            }
        }
    }
}
