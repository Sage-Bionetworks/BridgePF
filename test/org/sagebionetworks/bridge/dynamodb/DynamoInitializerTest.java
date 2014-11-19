package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.LocalSecondaryIndexDescription;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;

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
        assertEquals(4, table.getAttributeDefinitions().size());
        assertEquals(2, table.getLocalSecondaryIndexes().size());
        assertEquals(0, table.getGlobalSecondaryIndexes().size());
        assertEquals(25L, table.getProvisionedThroughput().getReadCapacityUnits().longValue());
        assertEquals(20L, table.getProvisionedThroughput().getWriteCapacityUnits().longValue());
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
        // Attributes
        List<AttributeDefinition> attributes = request.getAttributeDefinitions();
        assertNotNull(attributes);
        assertEquals(4, attributes.size());
        // Throughput
        assertEquals(25L, request.getProvisionedThroughput().getReadCapacityUnits().longValue());
        assertEquals(20L, request.getProvisionedThroughput().getWriteCapacityUnits().longValue());
    }

    @Test
    public void testCompareKeySchema() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = new TableDescription();
        table2.setTableName(table1.getTableName());
        table2.setKeySchema(new ArrayList<KeySchemaElement>());
        for (KeySchemaElement ele : table1.getKeySchema()) {
            table2.getKeySchema().add(new KeySchemaElement(ele.getAttributeName(), ele.getKeyType()));
        }
        // No exception
        DynamoInitializer.compareKeySchema(table1, table2);
    }

    @Test(expected = RuntimeException.class)
    public void testCompareKeySchemaWithException() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = new TableDescription();
        table2.setTableName(table1.getTableName());
        table2.setKeySchema(new ArrayList<KeySchemaElement>());
        for (KeySchemaElement ele : table1.getKeySchema()) {
            table2.getKeySchema().add(new KeySchemaElement(ele.getAttributeName(), ele.getKeyType()));
        }
        table2.getKeySchema().get(0).setAttributeName("some fake attr name");
        DynamoInitializer.compareKeySchema(table1, table2);
    }

    @Test
    public void testCompareLocalIndices() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = new TableDescription();
        table2.setTableName(table1.getTableName());
        table2.setLocalSecondaryIndexes(new ArrayList<LocalSecondaryIndexDescription>());
        for (LocalSecondaryIndexDescription index : table1.getLocalSecondaryIndexes()) {
            table2.getLocalSecondaryIndexes().add(
                    new LocalSecondaryIndexDescription()
                            .withIndexName(index.getIndexName())
                            .withKeySchema(index.getKeySchema())
                            .withProjection(index.getProjection()));
        }
        // No exception
        DynamoInitializer.compareLocalIndices(table1, table2);
    }

    @Test(expected = RuntimeException.class)
    public void testCompareLocalIndicesWithException() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        TableDescription table1 = tables.get(0);
        TableDescription table2 = new TableDescription();
        table2.setTableName(table1.getTableName());
        table2.setLocalSecondaryIndexes(new ArrayList<LocalSecondaryIndexDescription>());
        for (LocalSecondaryIndexDescription index : table1.getLocalSecondaryIndexes()) {
            table2.getLocalSecondaryIndexes().add(
                    new LocalSecondaryIndexDescription()
                            .withIndexName(index.getIndexName())
                            .withKeySchema(index.getKeySchema())
                            .withProjection(index.getProjection()));
        }
        table2.getLocalSecondaryIndexes().get(1).setIndexName("some fake index name");
        DynamoInitializer.compareLocalIndices(table1, table2);
    }
}
