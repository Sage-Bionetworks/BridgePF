package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NULL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.google.common.util.concurrent.RateLimiter;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@Component
public class DynamoExternalIdDao implements ExternalIdDao {
    
    static final String PAGE_SIZE_ERROR = "pageSize must be from 1-"+API_MAXIMUM_PAGE_SIZE+" records";
    static final int PAGE_SCAN_LIMIT = 200;
    static final String HEALTH_CODE = "healthCode";
    static final String IDENTIFIER = "identifier";
    static final String SUBSTUDY_ID = "substudyId";
    static final String STUDY_ID = "studyId";

    private RateLimiter getExternalIdRateLimiter;
    private DynamoDBMapper mapper;

    /** Gets the add limit and lock duration from Config. */
    @Autowired
    final void setConfig(Config config) {
        setGetExternalIdRateLimiter(RateLimiter.create(config.getInt(EXTERNAL_ID_GET_RATE)));
    }

    // allow unit test to mock this
    final void setGetExternalIdRateLimiter(RateLimiter getExternalIdRateLimiter) {
        this.getExternalIdRateLimiter = getExternalIdRateLimiter;
    }

    @Resource(name = "externalIdDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ExternalIdentifier getExternalId(StudyIdentifier studyId, String externalId) {
        checkNotNull(studyId);
        checkNotNull(externalId);
        
        DynamoExternalIdentifier key = new DynamoExternalIdentifier(studyId.getIdentifier(), externalId);
        return mapper.load(key);
    }

    @Override
    public ForwardCursorPagedResourceList<ExternalIdentifierInfo> getExternalIds(StudyIdentifier studyId,
            String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter) {
        
        Set<String> callerSubstudies = BridgeUtils.getRequestContext().getCallerSubstudies();
        String nextPageOffsetKey = getNextPageOffsetKey(offsetKey, idFilter);
        // initial estimate: read capacity consumed will equal 1
        // see https://aws.amazon.com/blogs/developer/rate-limited-scans-in-amazon-dynamodb/
        int capacityAcquired = 1;
        int capacityConsumed = 0;
        List<ExternalIdentifierInfo> resultList = Lists.newArrayListWithCapacity(pageSize);
        do {
            getExternalIdRateLimiter.acquire(capacityAcquired);

            QueryResultPage<DynamoExternalIdentifier> queryResults = mapper.queryPage(DynamoExternalIdentifier.class,
                    createGetQuery(studyId, nextPageOffsetKey, PAGE_SCAN_LIMIT, idFilter, assignmentFilter, callerSubstudies));
            
            int limit = Math.min(pageSize - resultList.size(), queryResults.getResults().size());
            
            queryResults.getResults().stream().limit(limit).forEach(id -> resultList.add(convertToInfo(id)));
            capacityConsumed = queryResults.getConsumedCapacity().getCapacityUnits().intValue();

            // use capacity consumed by last request to as our estimate for the next request
            capacityAcquired = capacityConsumed;

            nextPageOffsetKey = updateNextPageOffsetKey(queryResults, resultList, pageSize);
            
        } while (resultList.size() < pageSize && nextPageOffsetKey != null);

        return new ForwardCursorPagedResourceList<>(resultList, nextPageOffsetKey)
                .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.ID_FILTER, idFilter)
                .withRequestParam(ResourceList.ASSIGNMENT_FILTER, assignmentFilter);
    }
    
    private ExternalIdentifierInfo convertToInfo(DynamoExternalIdentifier id) {
        return new ExternalIdentifierInfo(id.getIdentifier(), id.getSubstudyId(), id.getHealthCode() != null);
    }
    
    /**
     * The offset key is applied after the idFilter. If the offsetKey doesn't match the beginning
     * of the idFilter, the AWS SDK throws a validation exception. So when providing an idFilter and 
     * a paging offset, clear the offset (go back to the first page) if they don't match.
     */
    private String getNextPageOffsetKey(String offsetKey, String idFilter) {
        if (offsetKey != null && idFilter != null && !offsetKey.startsWith(idFilter)) {
            offsetKey = null;
        }
        return offsetKey;
    }
    
    private String updateNextPageOffsetKey(QueryResultPage<DynamoExternalIdentifier> queryResults,
            List<ExternalIdentifierInfo> resultList, int pageSize) {
        if (queryResults.getCount() > pageSize && !resultList.isEmpty()) {
            // we retrieved more records from Dynamo than we are returning, get the last identifier to page
            return resultList.get(pageSize - 1).getIdentifier();
        }
        // The page from DDB has returned a key for another page, and we haven't exceeded our page 
        // size, so we need to page another set of records from DDB. 
        Map<String, AttributeValue> lastEvaluated = queryResults.getLastEvaluatedKey();
        return lastEvaluated != null ? lastEvaluated.get(IDENTIFIER).getS() : null;
    }

    @Override
    public void createExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        mapper.save(externalId);
    }

    @Override
    public void deleteExternalId(ExternalIdentifier externalId) {
        checkNotNull(externalId);
        
        mapper.delete(externalId);
    }
    
    @Override
    public void assignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkArgument(isNotBlank(externalId));
        
        StudyIdentifier studyId = new StudyIdentifierImpl(account.getStudyId());
        ExternalIdentifier identifier = getExternalId(studyId, externalId);
        
        // Validation should prevent this from happening
        if (identifier == null) {
            return;
        }
        // If this assignment has already occurred, update the account object in an idempotent
        // manner incase we need to repair it after a failed distributed operation.
        if (account.getHealthCode().equals(identifier.getHealthCode())) {
            // Update the account and return, though this may be a no-op
            updateAccountSubstudies(account, identifier);
            return;
        }
        // has the identifier already been assigned to another account? This is handled by 
        // a save expression passed to the save command, so we do not need to explicitly check
        try {
            identifier.setHealthCode(account.getHealthCode());
            mapper.save(identifier, getHealthCodeAssignedExpression(false));
            updateAccountSubstudies(account, identifier);
        } catch(ConditionalCheckFailedException e) {
            throw new EntityAlreadyExistsException(ExternalIdentifier.class, IDENTIFIER, identifier.getIdentifier());
        }
    }

    private void updateAccountSubstudies(Account account, ExternalIdentifier identifier) {
        if (identifier.getSubstudyId() != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getStudyId(),
                    identifier.getSubstudyId(), account.getId());
            acctSubstudy.setExternalId(identifier.getIdentifier());
            if (!account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().add(acctSubstudy);    
            }
            // For backwards compatibility while transitioning to multiple external IDs,
            // assign the singular external ID field. This will be replaced by the 
            // externalIds field which is based directly off the contents of the 
            // accountSubstudies collection.
            account.setExternalId(identifier.getIdentifier());
        }
    }

    @Override
    public void unassignExternalId(Account account, String externalId) {
        checkNotNull(account);
        checkArgument(isNotBlank(externalId));
        
        StudyIdentifier studyId = new StudyIdentifierImpl(account.getStudyId());
        ExternalIdentifier identifier = getExternalId(studyId, externalId);
        
        if (identifier == null) {
            return;
        }
        if (account.getHealthCode().equals(identifier.getHealthCode())) {
            identifier.setHealthCode(null);
            mapper.save(identifier);
        }
        // For backwards compatibility while migrating to externalIds
        account.setExternalId(null);
        if (identifier.getSubstudyId() != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getStudyId(),
                    identifier.getSubstudyId(), account.getId());
            acctSubstudy.setExternalId(identifier.getIdentifier());
            account.getAccountSubstudies().remove(acctSubstudy);
        }
    }
    
    /**
     * Get the count query (applies filters) and then sets an offset key and the limit to a page of records, 
     * plus one, to determine if there are records beyond the current page. 
     */
    private DynamoDBQueryExpression<DynamoExternalIdentifier> createGetQuery(StudyIdentifier studyId, String offsetKey,
            int pageSize, String idFilter, Boolean assignmentFilter, Set<String> callerSubstudyIds) {

        DynamoDBQueryExpression<DynamoExternalIdentifier> query =
                new DynamoDBQueryExpression<DynamoExternalIdentifier>();
        if (idFilter != null) {
            query.withRangeKeyCondition(IDENTIFIER, new Condition()
                    .withAttributeValueList(new AttributeValue().withS(idFilter))
                    .withComparisonOperator(BEGINS_WITH));
        }
        if (assignmentFilter != null) {
            addAssignmentFilter(query, assignmentFilter.booleanValue());
        }
        if (callerSubstudyIds != null && !callerSubstudyIds.isEmpty()) {
            query.withRangeKeyCondition(SUBSTUDY_ID, new Condition()
                    .withAttributeValueList(callerSubstudyIds.stream().map(id -> new AttributeValue(id)).collect(Collectors.toList()))
                    .withComparisonOperator(ComparisonOperator.CONTAINS));
        }
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId.getIdentifier(), null)); // no healthCode.

        if (offsetKey != null) {
            Map<String, AttributeValue> map = new HashMap<>();
            map.put(STUDY_ID, new AttributeValue().withS(studyId.getIdentifier()));
            map.put(IDENTIFIER, new AttributeValue().withS(offsetKey));
            query.withExclusiveStartKey(map);
        }

        query.withReturnConsumedCapacity(ReturnConsumedCapacity.TOTAL);
        query.withConsistentRead(true);
        query.withLimit(pageSize);
        return query;
    }

    private void addAssignmentFilter(DynamoDBQueryExpression<DynamoExternalIdentifier> query, boolean isAssigned) {
        ComparisonOperator healthCodeOp = (isAssigned) ? NOT_NULL : NULL;
        Condition healthCodeCondition = new Condition().withComparisonOperator(healthCodeOp);
        query.withQueryFilterEntry(HEALTH_CODE, healthCodeCondition);
    }
    
    private DynamoDBSaveExpression getHealthCodeAssignedExpression(boolean healthCodeAssigned) {
        Map<String, ExpectedAttributeValue> map = ImmutableMap.of(
                HEALTH_CODE, new ExpectedAttributeValue().withExists(healthCodeAssigned));

        return new DynamoDBSaveExpression().withExpected(map);
    }
    
}
