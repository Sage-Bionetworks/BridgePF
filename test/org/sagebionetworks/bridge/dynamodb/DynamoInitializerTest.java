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
import org.sagebionetworks.bridge.exceptions.BridgeInitializationException;

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndex;
import com.amazonaws.services.dynamodbv2.model.GlobalSecondaryIndexDescription;
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
        assertEquals(2, tables.size());
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
        assertEquals(2, classes.size());
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

    @Test
    public void createsGlobalIndices() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses(PACKAGE);
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables(classes);
        
        // This is the TaskTest with two global index annotations on it.
        TableDescription table = tables.get(1);
        assertEquals(3, table.getGlobalSecondaryIndexes().size());
        
        GlobalSecondaryIndexDescription index = findIndex(table.getGlobalSecondaryIndexes(), "guid-index");
        assertEquals("INCLUDE", index.getProjection().getProjectionType());
        assertEquals("guid", index.getKeySchema().get(0).getAttributeName());
        
        index = findIndex(table.getGlobalSecondaryIndexes(), "healthCode-scheduledOn-index");
        assertEquals("ALL", index.getProjection().getProjectionType());
        assertEquals("healthCode", index.getKeySchema().get(0).getAttributeName());
        assertEquals("scheduledOn", index.getKeySchema().get(1).getAttributeName());
        
        index = findIndex(table.getGlobalSecondaryIndexes(), "healthCode-expiresOn-index");
        assertEquals("expiresOn", index.getKeySchema().get(0).getAttributeName());
    }
    
    private GlobalSecondaryIndexDescription findIndex(List<GlobalSecondaryIndexDescription> list, String name) {
        for (GlobalSecondaryIndexDescription index : list) {
            if (index.getIndexName().equals(name)) {
                return index;
            }
        }
        return null;
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
}
