package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.test.HealthDataRecordTest;
import org.sagebionetworks.bridge.exceptions.BridgeInitializationException;

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.Projection;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.google.common.collect.ImmutableList;

public class DynamoInitializerTest {

    private static final String PACKAGE = "org.sagebionetworks.bridge.dynamodb.test";

    @Test
    public void testGetAnnotatedTables() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        assertNotNull(tables);
        assertEquals(1, tables.size());
        Map<String, TableDescription> tableMap = new HashMap<String, TableDescription>();
        for (TableDescription table : tables) {
            tableMap.put(table.getTableName(), table);
        }
        BridgeConfig config = BridgeConfigFactory.getConfig();
        Environment env = config.getEnvironment();
        String tableName = env.name().toLowerCase() + "-" + config.getUser() + "-" + "HealthDataRecord";
        TableDescription table = tableMap.get(tableName);
        assertNotNull(table);
        assertEquals(2, table.getKeySchema().size());
        assertEquals(5, table.getAttributeDefinitions().size());
        assertEquals(2, table.getLocalSecondaryIndexes().size());
        assertEquals(1, table.getGlobalSecondaryIndexes().size());
        assertEquals(30L, table.getProvisionedThroughput().getReadCapacityUnits().longValue());
        assertEquals(50L, table.getProvisionedThroughput().getWriteCapacityUnits().longValue());
    }

    @Test
    public void testLoadDynamoTableClasses() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        assertNotNull(classes);
        assertEquals(1, classes.size());
        Set<String> classSet = new HashSet<String>();
        for (Class<?> clazz : classes) {
            classSet.add(clazz.getName());
        }
        assertTrue(classSet.contains("org.sagebionetworks.bridge.dynamodb.test.HealthDataRecordTest"));
    }

    @Test
    public void testGetAttributeName() throws Exception {
        Method method = HealthDataRecordTest.class.getMethod("getStartDate");
        assertEquals("startDate", DynamoInitializer.getAttributeName(method));
        method = HealthDataRecordTest.class.getMethod("hashCode");
        assertEquals("hashCode", DynamoInitializer.getAttributeName(method));
    }

    @Test
    public void testGetAttributeType() throws Exception {
        Method method = HealthDataRecordTest.class.getMethod("getStartDate");
        assertEquals(ScalarAttributeType.N, DynamoInitializer.getAttributeType(method));
        method = HealthDataRecordTest.class.getMethod("getData");
        assertEquals(ScalarAttributeType.S, DynamoInitializer.getAttributeType(method));
    }

    @Test
    public void testGetCreateTableRequest() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table = tables.get(0);
        CreateTableRequest request = DynamoInitializer.getCreateTableRequest(table);
        assertNotNull(request);

        // KeySchema
        List<KeySchemaElement> keySchema = request.getKeySchema();
        assertNotNull(keySchema);
        assertEquals(2, keySchema.size());
        Map<String, KeySchemaElement> keyElements = new HashMap<String, KeySchemaElement>();
        for (KeySchemaElement ele : keySchema) {
            keyElements.put(ele.getAttributeName(), ele);
        }
        assertEquals("HASH", keyElements.get("key").getKeyType());
        assertEquals("RANGE", keyElements.get("recordId").getKeyType());
        assertEquals("HASH", keySchema.get(0).getKeyType()); // The first key must be the hashkey

        // Local indices
        List<LocalSecondaryIndex> localIndices = request.getLocalSecondaryIndexes();
        assertNotNull(localIndices);
        assertEquals(2, localIndices.size());
        Map<String, LocalSecondaryIndex> localIndexMap = new HashMap<String, LocalSecondaryIndex>();
        for (LocalSecondaryIndex idx : localIndices) {
            localIndexMap.put(idx.getIndexName(), idx);
        }
        assertNotNull(localIndexMap.get("startDate-index"));
        assertNotNull(localIndexMap.get("endDate-index"));

        // global indices
        List<GlobalSecondaryIndex> globalIndexList = request.getGlobalSecondaryIndexes();
        assertEquals(1, globalIndexList.size());
        assertEquals("secondary-index", globalIndexList.get(0).getIndexName());

        // Attributes
        List<AttributeDefinition> attributes = request.getAttributeDefinitions();
        assertNotNull(attributes);
        assertEquals(5, attributes.size());

        // Throughput
        assertEquals(30L, request.getProvisionedThroughput().getReadCapacityUnits().longValue());
        assertEquals(50L, request.getProvisionedThroughput().getWriteCapacityUnits().longValue());
    }

    @Test
    public void testCompareSchema() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = copyTableDescription(table1);
        // No exception
        DynamoInitializer.compareSchema(table1, table2);
    }

    @Test(expected = BridgeInitializationException.class)
    public void testCompareSchemaDifferentKeys() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = copyTableDescription(table1);
        table2.getKeySchema().get(0).setAttributeName("some fake attr name");
        DynamoInitializer.compareSchema(table1, table2);
    }

    @Test(expected = BridgeInitializationException.class)
    public void testCompareSchemaDifferentGlobalIndex() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = copyTableDescription(table1);
        table2.getGlobalSecondaryIndexes().get(0).setIndexName("some fake index name");
        DynamoInitializer.compareSchema(table1, table2);
    }

    @Test(expected = BridgeInitializationException.class)
    public void testCompareSchemaDifferentLocalIndex() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = copyTableDescription(table1);
        table2.getLocalSecondaryIndexes().get(1).setIndexName("some fake index name");
        DynamoInitializer.compareSchema(table1, table2);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareSecondaryIndicesZeroToOne() {
        // one index in table 2
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(new GlobalSecondaryIndexDescription());

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", null, indexList2, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareSecondaryIndicesOneToZero() {
        // one index in table 1
        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(new GlobalSecondaryIndexDescription());

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, null, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareSecondaryIndicesOneToTwo() {
        // one index in table 1
        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(new GlobalSecondaryIndexDescription());

        // two index in table 2
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(new GlobalSecondaryIndexDescription(),
                new GlobalSecondaryIndexDescription());

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareSecondaryIndicesTwoToOne() {
        // two index in table 1
        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(new GlobalSecondaryIndexDescription(),
                new GlobalSecondaryIndexDescription());

        // one index in table 2
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(new GlobalSecondaryIndexDescription());

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test
    public void compareSecondaryIndicesNullToNull() {
        DynamoInitializer.compareSecondaryIndices("test-table", null, null, true);
    }

    @Test
    public void compareSecondaryIndicesZeroToZero() {
        DynamoInitializer.compareSecondaryIndices("test-table", Collections.emptyList(), Collections.emptyList(),
                true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareGlobalIndicesDifferentName() {
        // indices
        GlobalSecondaryIndexDescription sameIndex = makeGlobalIndex("same-index", "same-key",
                ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex1 = makeGlobalIndex("index1", "diff-key", ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex2 = makeGlobalIndex("index2", "diff-key", ProjectionType.ALL, 25, 25);

        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareGlobalIndicesDifferentKeys() {
        // indices
        GlobalSecondaryIndexDescription sameIndex = makeGlobalIndex("same-index", "same-key",
                ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex1 = makeGlobalIndex("diff-index", "key1", ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex2 = makeGlobalIndex("diff-index", "key2", ProjectionType.ALL, 25, 25);

        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareGlobalIndicesDifferentProjections() {
        // indices
        GlobalSecondaryIndexDescription sameIndex = makeGlobalIndex("same-index", "same-key",
                ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex1 = makeGlobalIndex("diff-index", "diff-key",
                ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription diffIndex2 = makeGlobalIndex("diff-index", "diff-key",
                ProjectionType.KEYS_ONLY, 25, 25);

        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test
    public void compareSameGlobalIndicesInDifferentOrder() {
        // indices
        GlobalSecondaryIndexDescription index1 = makeGlobalIndex("index1", "key1", ProjectionType.ALL, 25, 25);
        GlobalSecondaryIndexDescription index2 = makeGlobalIndex("index2", "key2", ProjectionType.ALL, 25, 25);

        List<GlobalSecondaryIndexDescription> indexList1 = ImmutableList.of(index1, index2);
        List<GlobalSecondaryIndexDescription> indexList2 = ImmutableList.of(index2, index1);

        // execute (no exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, true);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareLocalIndicesDifferentNames() {
        // indices
        LocalSecondaryIndexDescription sameIndex = makeLocalIndex("same-index", "same-key", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex1 = makeLocalIndex("index1", "diff-key", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex2 = makeLocalIndex("index2", "diff-key", ProjectionType.ALL);

        List<LocalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<LocalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, false);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareLocalIndicesDifferentKeys() {
        // indices
        LocalSecondaryIndexDescription sameIndex = makeLocalIndex("same-index", "same-key", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex1 = makeLocalIndex("diff-index", "key1", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex2 = makeLocalIndex("diff-index", "key2", ProjectionType.ALL);

        List<LocalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<LocalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, false);
    }

    @Test(expected = BridgeInitializationException.class)
    public void compareLocalIndicesDifferentProjections() {
        // indices
        LocalSecondaryIndexDescription sameIndex = makeLocalIndex("same-index", "same-key", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex1 = makeLocalIndex("diff-index", "diff-key", ProjectionType.ALL);
        LocalSecondaryIndexDescription diffIndex2 = makeLocalIndex("diff-index", "diff-key", ProjectionType.KEYS_ONLY);

        List<LocalSecondaryIndexDescription> indexList1 = ImmutableList.of(sameIndex, diffIndex1);
        List<LocalSecondaryIndexDescription> indexList2 = ImmutableList.of(sameIndex, diffIndex2);

        // execute (expected exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, false);
    }

    @Test
    public void compareSameLocalIndicesInDifferentOrder() {
        // indices
        LocalSecondaryIndexDescription index1 = makeLocalIndex("index1", "key1", ProjectionType.ALL);
        LocalSecondaryIndexDescription index2 = makeLocalIndex("index2", "key2", ProjectionType.ALL);

        List<LocalSecondaryIndexDescription> indexList1 = ImmutableList.of(index1, index2);
        List<LocalSecondaryIndexDescription> indexList2 = ImmutableList.of(index2, index1);

        // execute (no exception)
        DynamoInitializer.compareSecondaryIndices("test-table", indexList1, indexList2, false);
    }

    // Copies the relevant attributes from a table (name, keys, global and local secondary indices)
    private static TableDescription copyTableDescription(TableDescription table1) {
        TableDescription table2 = new TableDescription();
        table2.setTableName(table1.getTableName());

        // key schema
        table2.setKeySchema(new ArrayList<KeySchemaElement>());
        for (KeySchemaElement ele : table1.getKeySchema()) {
            table2.getKeySchema().add(new KeySchemaElement(ele.getAttributeName(), ele.getKeyType()));
        }

        // global indices
        table2.setGlobalSecondaryIndexes(new ArrayList<GlobalSecondaryIndexDescription>());
        for (GlobalSecondaryIndexDescription index : table1.getGlobalSecondaryIndexes()) {
            table2.getGlobalSecondaryIndexes().add(
                    new GlobalSecondaryIndexDescription()
                            .withIndexName(index.getIndexName())
                            .withKeySchema(index.getKeySchema())
                            .withProjection(index.getProjection())
                            .withProvisionedThroughput(index.getProvisionedThroughput()));
        }

        // local indices
        table2.setLocalSecondaryIndexes(new ArrayList<LocalSecondaryIndexDescription>());
        for (LocalSecondaryIndexDescription index : table1.getLocalSecondaryIndexes()) {
            table2.getLocalSecondaryIndexes().add(
                    new LocalSecondaryIndexDescription()
                            .withIndexName(index.getIndexName())
                            .withKeySchema(index.getKeySchema())
                            .withProjection(index.getProjection()));
        }

        return table2;
    }

    private static GlobalSecondaryIndexDescription makeGlobalIndex(String indexName, String keyName,
            ProjectionType projectionType, long readCapacity, long writeCapacity) {
        GlobalSecondaryIndexDescription index = new GlobalSecondaryIndexDescription().withIndexName(indexName)
                .withKeySchema(new KeySchemaElement(keyName, KeyType.HASH))
                .withProjection(new Projection().withProjectionType(projectionType))
                .withProvisionedThroughput(new ProvisionedThroughputDescription().withReadCapacityUnits(readCapacity)
                        .withWriteCapacityUnits(writeCapacity));
        return index;
    }

    private static LocalSecondaryIndexDescription makeLocalIndex(String indexName, String keyName,
            ProjectionType projectionType) {
        LocalSecondaryIndexDescription index = new LocalSecondaryIndexDescription().withIndexName(indexName)
                .withKeySchema(new KeySchemaElement(keyName, KeyType.RANGE))
                .withProjection(new Projection().withProjectionType(projectionType));
        return index;
    }
}
