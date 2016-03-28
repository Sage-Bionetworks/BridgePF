package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.CONTAINS;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.LT;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NULL;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.DynamoPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
public class DynamoExternalIdDao implements ExternalIdDao {
    
    static final String CONFIG_KEY_ADD_LIMIT = "external.id.add.limit";
    static final String CONFIG_KEY_LOCK_DURATION = "external.id.lock.duration";

    private int addLimit;
    private int lockDuration;
    private DynamoDBMapper mapper;

    /** Gets the add limit and lock duration from Config. */
    @Autowired
    public final void setConfig(Config config) {
        addLimit = config.getInt(CONFIG_KEY_ADD_LIMIT);
        lockDuration = config.getInt(CONFIG_KEY_LOCK_DURATION);
    }
    
    @Resource(name = "externalIdDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DynamoPagedResourceList<? extends ExternalIdentifier> getExternalIds(StudyIdentifier studyId, String offsetKey, 
            int pageSize, String idFilter, Boolean assignmentFilter) {
        checkNotNull(studyId);
        
        // Just set a sane upper limit on this.
        if (pageSize < API_MINIMUM_PAGE_SIZE || pageSize > API_MAXIMUM_PAGE_SIZE) {
            throw new BadRequestException(BridgeConstants.PAGE_SIZE_ERROR);
        }
        // Paging in DDB: The limit attribute limits the records before query filters are applied, so it cannot be 
        // used to retrieve a page. Must reduce to a page after query. In comments on this, iterating will cause 
        // DDB to load query lazily, but if you call size(), it will do a scan to determine size of results. So, 
        // iterate through returned records, don't subList() or use other code that would requires calling size().
        
        PaginatedQueryList<DynamoExternalIdentifier> list = mapper.query(DynamoExternalIdentifier.class,
                createGetQuery(studyId, offsetKey, idFilter, assignmentFilter));
        
        int total = mapper.count(DynamoExternalIdentifier.class, createCountQuery(studyId, idFilter, assignmentFilter));
        
        List<ExternalIdentifier> identifiers = Lists.newArrayListWithCapacity(pageSize);
        
        Iterator<? extends ExternalIdentifier> iterator = list.iterator();
        while(iterator.hasNext() && identifiers.size() < pageSize) {
            identifiers.add(iterator.next());
        }
        // This is the last key, not the next key of the next page of records. It only exists if there's a record
        // beyond the records we've converted to a page. Then get the last key in the list.
        String nextPageKey = (iterator.hasNext()) ? last(identifiers).getExternalId() : null;
        
        DynamoPagedResourceList<? extends ExternalIdentifier> resourceList = new DynamoPagedResourceList<>(
                identifiers, nextPageKey, pageSize, total, null);
        resourceList.put("idFilter", idFilter);
        resourceList.put("assignmentFilter", assignmentFilter);

        return resourceList;
    }

    @Override
    public void addExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);

        if (externalIds.size() > addLimit) {
            throw new BadRequestException("List of externalIds is too large; size=" + externalIds.size() + ", limit=" + addLimit);
        }
        if (!externalIds.isEmpty()) {
            List<DynamoExternalIdentifier> idsToSave = externalIds.stream().map(id -> {
                return new DynamoExternalIdentifier(studyId, id);
            }).filter(externalId -> {
                return mapper.load(externalId) == null;
            }).collect(Collectors.toList());
            
            if (!idsToSave.isEmpty()) {
                List<FailedBatch> failures = mapper.batchSave(idsToSave);
                BridgeUtils.ifFailuresThrowException(failures);
            }
        }
    }
    
    @Override
    public void reserveExternalId(StudyIdentifier studyId, String externalId) throws EntityAlreadyExistsException {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        try {
            long newReservation = DateUtils.getCurrentMillisFromEpoch();
            
            identifier.setReservation(newReservation);
            mapper.save(identifier, getReservationExpression(newReservation));
            
        } catch(ConditionalCheckFailedException e) {
            // The timeout is in effect or the healthCode is set, either way, code is "taken"
            throw new EntityAlreadyExistsException(identifier);
        }
    }

    @Override
    public void assignExternalId(StudyIdentifier studyId, String externalId, String healthCode) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        checkArgument(isNotBlank(healthCode));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier == null) {
            throw new EntityNotFoundException(ExternalIdentifier.class);
        }
        // If the same code has already been set, do nothing, do not throw an error.
        if (!healthCode.equals(identifier.getHealthCode())) {
            try {
                
                identifier.setReservation(0L);
                identifier.setHealthCode(healthCode);
                mapper.save(identifier, getAssignmentExpression());
                
            } catch(ConditionalCheckFailedException e) {
                // The timeout is in effect or the healthCode is set, either way, code is "taken"
                throw new EntityAlreadyExistsException(identifier);
            }        
        }
    }

    @Override
    public void unassignExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkArgument(isNotBlank(externalId));
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, externalId);
        
        // Don't throw an exception if the identifier doesn't exist, we don't care.
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        if (identifier != null) {
            identifier.setHealthCode(null);
            identifier.setReservation(0L);
            mapper.save(identifier);
        }
    }
    
    /**
     * This is intended for testing. Deleting a large number of identifiers will cause DynamoDB capacity exceptions.
     */
    @Override
    public void deleteExternalIds(StudyIdentifier studyId, List<String> externalIds) {
        checkNotNull(studyId);
        checkNotNull(externalIds);
        
        if (!externalIds.isEmpty()) {
            List<DynamoExternalIdentifier> idsToDelete = externalIds.stream().map(id -> {
                return new DynamoExternalIdentifier(studyId, id);
            }).collect(Collectors.toList());
            
            List<FailedBatch> failures = mapper.batchDelete(idsToDelete);
            BridgeUtils.ifFailuresThrowException(failures);
        }
    }

    private DynamoDBQueryExpression<DynamoExternalIdentifier> createGetQuery(StudyIdentifier studyId,
            String offsetKey, String idFilter, Boolean assignmentFilter) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = createCountQuery(studyId, idFilter, assignmentFilter);
        if (offsetKey != null) {
            Map<String,AttributeValue> map = new HashMap<>();
            map.put("studyId", new AttributeValue().withS(studyId.getIdentifier()));
            map.put("externalId", new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }
        return query;
    }

    private DynamoDBQueryExpression<DynamoExternalIdentifier> createCountQuery(StudyIdentifier studyId,
            String idFilter, Boolean assignmentFilter) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = new DynamoDBQueryExpression<DynamoExternalIdentifier>();
        if (idFilter != null) {
            // You cannot filter a query on a hash key, so we copy this value to another column where we can filter
            query.withQueryFilterEntry("filterableExternalId", new Condition()
                    .withAttributeValueList(new AttributeValue().withS(idFilter))
                    .withComparisonOperator(CONTAINS));
        }
        if (assignmentFilter == Boolean.TRUE) {
            query.withQueryFilterEntry("healthCode", new Condition().withComparisonOperator(NOT_NULL));
        } else if (assignmentFilter == Boolean.FALSE) {
            query.withQueryFilterEntry("healthCode", new Condition().withComparisonOperator(NULL));
        }
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId, null)); // no healthCode.
        return query;
    }
    
    /**
     * The save of the identifier with a new timeout only occurs if healthCode isn't set and 
     * the existing value is less than the current timeout lock minus the lock duration.
     * @param reservation
     * @return
     */
    private DynamoDBSaveExpression getReservationExpression(long newReservation) {
        AttributeValue value = new AttributeValue().withN(Long.toString(newReservation-lockDuration));
        
        Map<String, ExpectedAttributeValue> map = Maps.newHashMap();
        map.put("reservation",
                new ExpectedAttributeValue().withValue(value).withComparisonOperator(LT));
        map.put("healthCode", new ExpectedAttributeValue().withExists(false));

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.withConditionalOperator(ConditionalOperator.AND);
        saveExpression.setExpected(map);
        return saveExpression;
    }
    
    /**
     * The assignment of an externalId only works if the healthCode has not already been set. We've 
     * already verified it's not the same healthCode as the caller (an attemp to reset the same ID, 
     * which is ignored).
     * @return
     */
    private DynamoDBSaveExpression getAssignmentExpression() {
        Map<String, ExpectedAttributeValue> map = Maps.newHashMap();
        map.put("healthCode", new ExpectedAttributeValue().withExists(false));

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression();
        saveExpression.setExpected(map);
        return saveExpression;
    }
    
    private <T> T last(List<T> items) {
        if (items != null && !items.isEmpty()) {
            return items.get(items.size()-1);
        }
        return null;
    }

    

}
