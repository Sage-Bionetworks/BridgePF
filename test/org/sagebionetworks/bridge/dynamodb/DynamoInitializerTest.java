package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;

public class DynamoInitializerTest {

    @Test
    public void testGetAnnotatedTables() {
        List<TableDescription> tables = DynamoInitializer.getAnnotatedTables();
        assertNotNull(tables);
        assertEquals(1, tables.size());
        Map<String, TableDescription> tableMap = new HashMap<String, TableDescription>();
        for (TableDescription table : tables) {
            tableMap.put(table.getTableName(), table);
        }
        BridgeConfig config = BridgeConfigFactory.getConfig();
        Environment env = config.getEnvironment();
        if (Environment.STUB.equals(env)) {
            env = Environment.LOCAL;
        }
        String tableName = env.getEnvName() + "-" + config.getUser() + "-" + "HealthDataRecord";
        TableDescription table = tableMap.get(tableName);
        assertNotNull(table);
        assertEquals(2, table.getKeySchema().size());
        assertEquals(4, table.getAttributeDefinitions().size());
        assertEquals(2, table.getLocalSecondaryIndexes().size());
        assertEquals(0, table.getGlobalSecondaryIndexes().size());
        assertEquals(10L, table.getProvisionedThroughput().getReadCapacityUnits().longValue());
        assertEquals(5L, table.getProvisionedThroughput().getWriteCapacityUnits().longValue());
    }

    @Test
    public void testLoadDynamoTableClasses() {
        List<Class<?>> classes = DynamoInitializer.loadDynamoTableClasses();
        assertNotNull(classes);
        assertEquals(1, classes.size());
        Set<String> classSet = new HashSet<String>();
        for (Class<?> clazz : classes) {
            classSet.add(clazz.getName());
        }
        assertTrue(classSet.contains("org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord"));
    }

    @Test
    public void testGetAttributeName() throws Exception {
        Method method = DynamoHealthDataRecord.class.getMethod("getStartDate");
        assertEquals("startDate", DynamoInitializer.getAttributeName(method));
        method = DynamoHealthDataRecord.class.getMethod("hashCode");
        assertEquals("hashCode", DynamoInitializer.getAttributeName(method));
    }

    @Test
    public void testGetAttributeType() throws Exception {
        Method method = DynamoHealthDataRecord.class.getMethod("getStartDate");
        assertEquals(ScalarAttributeType.N, DynamoInitializer.getAttributeType(method));
        method = DynamoHealthDataRecord.class.getMethod("getData");
        assertEquals(ScalarAttributeType.S, DynamoInitializer.getAttributeType(method));
    }

    @Test
    public void testGetCreateTableRequest() {
        
    }

    @Test
    public void testCompareKeySchema() {
        
    }

    @Test
    public void testCompareLocalIndices() {
        
    }
}
