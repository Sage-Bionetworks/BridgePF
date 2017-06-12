package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Page;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * This class is a wrapper around a DynamoDB Index object, to enable easy testing and easy mocking. This class is used
 * to query secondary indices, since DynamoDB mappers don't support querying on secondary indices. This class also 
 * encapsulates logic to re-query tables to get full table entries.
 */
public class DynamoIndexHelper {

    public static DynamoIndexHelper create(final Class<?> dynamoTable, final String indexName,
           final AmazonDynamoDB client, DynamoNamingHelper dynamoNamingHelper, DynamoUtils dynamoUtils) {
        final DynamoDB ddb = new DynamoDB(client);
        final Table ddbTable = ddb.getTable(dynamoNamingHelper.getFullyQualifiedTableName(dynamoTable));
        final Index ddbIndex = ddbTable.getIndex(indexName);
        final DynamoIndexHelper indexHelper = new DynamoIndexHelper();
        indexHelper.setIndex(ddbIndex);
        indexHelper.setMapper(dynamoUtils.getMapper(dynamoTable));
        return indexHelper;
    }

    private Index index;
    private DynamoDBMapper mapper;

    /** DynamoDB index. This is used to query the secondary index. This is configured by Spring. */
    final void setIndex(Index index) {
        this.index = index;
    }

    public Index getIndex() {
        return index;
    }
    
    /**
     * DynamoDB mapper. This is used to re-query the DynamoDB table to get full entries from the key objects. This setter is
     * called by tests.
     */
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    public QueryOutcome query(@Nonnull QuerySpec spec) {
        Page<Item,QueryOutcome> page = index.query(spec).firstPage();
        return page.getLowLevelResult();
    }

    /**
     * Queries the secondary index with the specified key name and value, and an optional range key condition. Only 
     * the attributes projected onto the index will be returned. (Generally, this is only the table index keys 
     * and the index keys.) This is generally used to re-query the table to get the full list of results, or to 
     * batch update or batch delete rows.
     *
     * @param clazz
     *         expected result class
     * @param indexKeyName
     *         index key name to query on
     * @param indexKeyValue
     *         index key value to query on
     * @param rangeKeyCondition
     *         range condition for query on range portion of key (optional)
     * @param <T>
     *         expected result type
     * @return list of key objects returned by the query
     */
    public <T> List<T> queryKeys(@Nonnull Class<? extends T> clazz, @Nonnull String indexKeyName,
            @Nonnull Object indexKeyValue, RangeKeyCondition rangeKeyCondition) {
        // query the index
        Iterable<Item> itemIter = queryHelper(indexKeyName, indexKeyValue, rangeKeyCondition);

        // convert items to the specified class
        List<T> recordKeyList = new ArrayList<>();
        for (Item oneItem : itemIter) {
            T oneRecord = BridgeObjectMapper.get().convertValue(oneItem.asMap(), clazz);
            recordKeyList.add(oneRecord);
        }
        return recordKeyList;
    }
    
    /**
     * Query via a secondary index to return the count of matching items in the table.
     *  
     * @param indexKeyName
     *         index key name to query on
     * @param indexKeyValue
     *         index key value to query on
     * @param rangeKeyCondition
     *         range condition for query on range portion of key (optional)
     * @return count of records in the table
     */
    public int queryKeyCount(@Nonnull String indexKeyName, @Nonnull Object indexKeyValue,
                    RangeKeyCondition rangeKeyCondition) {
        int count = 0;
        Iterable<Item> itemIter = queryHelper(indexKeyName, indexKeyValue, rangeKeyCondition);
        for(Iterator<Item> i = itemIter.iterator(); i.hasNext(); i.next()) {
            count++;
        }
        return count;
    }
    
    /**
     * <p>
     * Queries the secondary index with the specified key name and value. Results will be returned as a list of
     * the specified class. Unlike {@link #queryKeys}, this method re-queries DynamoDB to get the full rows of the
     * DynamoDB rows.
     * </p>
     * <p>
     * Note that for some reason, this method seems to return results in an unspecified order (generally sorted forward
     * on the range key or sorted backwards). It's unclear why this is the case, and it's not obvious how to fix it.
     * See https://sagebionetworks.jira.com/browse/BRIDGE-1467
     * </p>
     *
     * @param clazz
     *         expected result class
     * @param indexKeyName
     *         index key name to query on
     * @param indexKeyValue
     *         index key value to query on
     * @param rangeKeyCondition
     *         condition for query on range portion of key (optional)
     * @param <T>
     *         expected result type
     * @return list of query results
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> query(@Nonnull Class<? extends T> clazz, @Nonnull String indexKeyName,
            @Nonnull Object indexKeyValue, RangeKeyCondition rangeKeyCondition) {
        // In general, we only project keys onto global secondary indices, to save storage space. This means the
        // objects we get back aren't full fledged objects. However, we can use them as "key objects" to re-query
        // the DDB table to get full results.
        //
        // First step is to query the index to get these "key objects".
        List<T> recordKeyList = queryKeys(clazz, indexKeyName, indexKeyValue, rangeKeyCondition);

        // Using the "key objects", batch query DDB to get full records. For some reason, batchLoad() returns a map.
        // Flatten that map into a list.
        //
        // Also, for some reason, batchLoad() takes a List<Object>, not a List<T> or List<?>. Fortunately, Java type
        // erasure means we can safely cast this to a List<Object>.
        Map<String, List<Object>> resultMap = mapper.batchLoad((List<Object>) recordKeyList);
        List<T> recordList = new ArrayList<>();
        for (List<Object> resultList : resultMap.values()) {
            for (Object oneResult : resultList) {
                if (!clazz.isInstance(oneResult)) {
                    // This should never happen, but just in case.
                    throw new BridgeServiceException(String.format(
                            "DynamoDB returned objects of type %s instead of %s",
                            oneResult.getClass().getName(), clazz.getName()));
                }

                recordList.add((T) oneResult);
            }
        }

        return recordList;
    }

    /**
     * This abstracts away the call to index.query(), which returns an ItemCollection. While ItemCollection implements
     * Iterable, it overrides iterator() to return an IteratorSupport, which is not publicly exposed. This makes
     * index.query() nearly impossible to mock. So we abstract it away into a method that we can mock.
     */
    protected Iterable<Item> queryHelper(@Nonnull String indexKeyName, @Nonnull Object indexKeyValue,
                    @Nullable RangeKeyCondition rangeKeyCondition) {
        if (rangeKeyCondition != null) {
            return index.query(indexKeyName, indexKeyValue, rangeKeyCondition);
        } else {
            return index.query(indexKeyName, indexKeyValue);
        }
    }    

}