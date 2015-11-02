package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.annotation.Nonnull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class DynamoIndexHelperTest {
    
    private DynamoIndexHelper helper;
    private ArgumentCaptor<List> arg;
    
    // test class to be used solely for mock testing
    public static class Thing {
        final String key;
        final String value;

        // needed for internal JSON conversion
        public Thing(@JsonProperty("key") String key) {
            this.key = key;
            value = null;
        }

        public Thing(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    // index.query() can't be mocked, so override queryHelper to sidestep this problem
    private static class TestDynamoIndexHelper extends DynamoIndexHelper {
        private final String expectedKey;
        private final String expectedValue;
        private final RangeKeyCondition expectedRangeKeyCondition;
        private final Iterable<Item> itemIterable;

        TestDynamoIndexHelper(String expectedKey, String expectedValue, RangeKeyCondition rangeKeyCondition, Iterable<Item> itemIterable) {
            this.expectedKey = expectedKey;
            this.expectedValue = expectedValue;
            this.expectedRangeKeyCondition = rangeKeyCondition;
            this.itemIterable = itemIterable;
        }

        @Override
        protected Iterable<Item> queryHelper(@Nonnull String indexKeyName, @Nonnull Object indexKeyValue, RangeKeyCondition rangeKeyCondition) {
            assertEquals(expectedKey, indexKeyName);
            assertEquals(expectedValue, indexKeyValue);
            assertEquals(expectedRangeKeyCondition, rangeKeyCondition);
            return itemIterable;
        }
    }
    
    public void mockResultsOfQuery(RangeKeyCondition condition) {
        // mock index
        List<Item> mockItemList = ImmutableList.of(new Item().with("key", "foo key"),
                new Item().with("key", "bar key"), new Item().with("key", "asdf key"),
                new Item().with("key", "jkl; key"));
        helper = new TestDynamoIndexHelper("test key", "test value", condition, mockItemList);

        // mock mapper result
        Map<String, List<Object>> mockMapperResultMap = new HashMap<>();
        mockMapperResultMap.put("dummy key 1", ImmutableList.<Object>of(new Thing("foo key", "foo value"),
                new Thing("bar key", "bar value")));
        mockMapperResultMap.put("dummy key 2", ImmutableList.<Object>of(new Thing("asdf key", "asdf value"),
                new Thing("jkl; key", "jkl; value")));

        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        arg = ArgumentCaptor.forClass(List.class);
        when(mockMapper.batchLoad(arg.capture())).thenReturn(mockMapperResultMap);
        helper.setMapper(mockMapper);
        
        Map<String,AttributeValue> mockResultMap = new HashMap<>();
        
        
        when(mockMapper.marshallIntoObject(any(), any())).thenReturn(new Thing("foo key", "foo value"));
    }

    @Test
    public void test() {
        RangeKeyCondition rangeKeyCondition = new RangeKeyCondition("antwerp").eq("belgium");
        mockResultsOfQuery(rangeKeyCondition);
        
        // execute query keys and validate
        List<Thing> keyList = helper.queryKeys(Thing.class, "test key", "test value", rangeKeyCondition);
        validateKeyObjects(keyList);

        // execute
        List<Thing> resultList = helper.query(Thing.class, "test key", "test value", rangeKeyCondition);

        // Validate intermediate "key objects". This is a List<Object>, but because of type erasure, this should work,
        // at least in the test context.
        validateKeyObjects(arg.getValue());

        // Validate final results. Because of wonkiness with maps and ordering, we'll convert the Things into a map and
        // validate the map.
        assertEquals(4, resultList.size());
        Map<String, String> thingMap = new HashMap<>();
        for (Thing oneThing : resultList) {
            thingMap.put(oneThing.key, oneThing.value);
        }

        assertEquals(4, thingMap.size());
        assertEquals("foo value", thingMap.get("foo key"));
        assertEquals("bar value", thingMap.get("bar key"));
        assertEquals("asdf value", thingMap.get("asdf key"));
        assertEquals("jkl; value", thingMap.get("jkl; key"));
    }
    
    @Test
    public void testCount() {
        mockResultsOfQuery(null);
        int count = helper.queryKeyCount("test key", "test value", null);
        // There are two lists of two items each
        assertEquals(4, count);
    }

    private static void validateKeyObjects(List<Thing> keyList) {
        assertEquals(4, keyList.size());

        assertEquals("foo key", keyList.get(0).key);
        assertNull(keyList.get(0).value);

        assertEquals("bar key", keyList.get(1).key);
        assertNull(keyList.get(1).value);

        assertEquals("asdf key", keyList.get(2).key);
        assertNull(keyList.get(2).value);

        assertEquals("jkl; key", keyList.get(3).key);
        assertNull(keyList.get(3).value);
    }
}
