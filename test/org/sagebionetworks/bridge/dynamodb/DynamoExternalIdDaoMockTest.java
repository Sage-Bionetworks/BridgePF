package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.IDENTIFIER;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

@RunWith(MockitoJUnitRunner.class)
public class DynamoExternalIdDaoMockTest {

    private static final String HEALTH_CODE = "healthCode";
    private static final String USER_ID = "userId";
    private static final String ID = "external-id";
    private static final String SUBSTUDY_ID = "substudy-id";

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private DynamoDBMapper mapper;
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoExternalIdentifier>> queryCaptor;
    @Captor
    private ArgumentCaptor<DynamoDBSaveExpression> saveExpressionCaptor;

    private DynamoExternalIdDao dao;

    private ExternalIdentifier externalId;
    
    @Before
    public void setupTest() {
        externalId = ExternalIdentifier.create(TEST_STUDY, ID);
        dao = new DynamoExternalIdDao();
        dao.setMapper(mapper);
        dao.setGetExternalIdRateLimiter(rateLimiter);
    }

    @After
    public void after() {
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void getExternalId() {
        when(mapper.load(any())).thenReturn(externalId);

        Optional<ExternalIdentifier> retrieved = dao.getExternalId(TEST_STUDY, ID);

        verify(mapper).load(externalId);
        assertEquals(externalId, retrieved.get());
    }

    @Test
    public void getExternalIdReturnsNull() {
        when(mapper.load(any())).thenReturn(null);

        Optional<ExternalIdentifier> retrieved = dao.getExternalId(TEST_STUDY, ID);

        verify(mapper).load(externalId);
        assertFalse(retrieved.isPresent());
    }

    @Test
    public void getExternalIds() {
        setupQueryOfStrings(ImmutableList.of("foo1", "foo2"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY,
                "offsetKey", 50, "offset", true);
        
        assertEquals("foo1", results.getItems().get(0).getIdentifier());
        assertEquals("foo2", results.getItems().get(1).getIdentifier());
        assertEquals(2, results.getItems().size());
        
        Map<String,Object> paramsMap = results.getRequestParams();
        assertEquals(50, (int) paramsMap.get(ResourceList.PAGE_SIZE));
        assertTrue((boolean) paramsMap.get(ResourceList.ASSIGNMENT_FILTER));
        assertEquals("offset", (String) paramsMap.get(ResourceList.ID_FILTER));
        assertEquals("offsetKey", (String) paramsMap.get(ResourceList.OFFSET_KEY));

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        Condition rangeCondition = query.getRangeKeyConditions().get(DynamoExternalIdDao.IDENTIFIER);
        assertEquals(BEGINS_WITH.toString(), rangeCondition.getComparisonOperator());
        assertEquals("offset", rangeCondition.getAttributeValueList().get(0).getS());

        Condition assignmentCondition = query.getQueryFilter().get(DynamoExternalIdDao.HEALTH_CODE);
        assertEquals(NOT_NULL.toString(), assignmentCondition.getComparisonOperator());

        Map<String, AttributeValue> map = query.getExclusiveStartKey();
        assertEquals(TEST_STUDY_IDENTIFIER, map.get(DynamoExternalIdDao.STUDY_ID).getS());
        assertEquals("offsetKey", map.get(DynamoExternalIdDao.IDENTIFIER).getS());

        assertEquals(ReturnConsumedCapacity.TOTAL.toString(), query.getReturnConsumedCapacity());
        assertEquals(new Integer(DynamoExternalIdDao.PAGE_SCAN_LIMIT), query.getLimit());
        assertTrue(query.isConsistentRead());

        DynamoExternalIdentifier id = query.getHashKeyValues();
        assertEquals(TEST_STUDY_IDENTIFIER, id.getStudyId());
        assertNull(id.getIdentifier());
    }

    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeLessThanMin() {
        dao.getExternalIds(TEST_STUDY, null, -2, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeGreaterThanMax() {
        dao.getExternalIds(TEST_STUDY, null, BridgeConstants.API_MAXIMUM_PAGE_SIZE+1, null, null);
    }
    
    @Test
    public void getExternalIdsNullValues() {
        setupQueryOfStrings(ImmutableList.of("foo1", "foo2"));

        // Note that the page size is enforced in the service, and should never be 0
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY,
                null, BridgeConstants.API_MINIMUM_PAGE_SIZE, null, null);
        Map<String,Object> paramsMap = results.getRequestParams();
        assertEquals(BridgeConstants.API_MINIMUM_PAGE_SIZE, (int) paramsMap.get(ResourceList.PAGE_SIZE));
        assertNull(paramsMap.get(ResourceList.ASSIGNMENT_FILTER));
        assertNull(paramsMap.get(ResourceList.ID_FILTER));
        assertNull(paramsMap.get(ResourceList.OFFSET_KEY));

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        assertNull(query.getRangeKeyConditions());
        assertNull(query.getQueryFilter());
        assertNull(query.getExclusiveStartKey());
    }

    @Test
    public void getExternalIdsMismatchedIdFilterClearsOffsetKey() {
        setupQueryOfStrings(ImmutableList.of("foo1", "foo2"));

        dao.getExternalIds(TEST_STUDY, "offsetKey", 50, "foo", true);

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        Condition rangeCondition = query.getRangeKeyConditions().get(DynamoExternalIdDao.IDENTIFIER);
        assertEquals(BEGINS_WITH.toString(), rangeCondition.getComparisonOperator());
        assertEquals("foo", rangeCondition.getAttributeValueList().get(0).getS());

        Condition assignmentCondition = query.getQueryFilter().get(DynamoExternalIdDao.HEALTH_CODE);
        assertEquals(NOT_NULL.toString(), assignmentCondition.getComparisonOperator());

        // This has been nullified, despite the fact that we provided an offset key
        assertNull(query.getExclusiveStartKey());
    }

    @Test
    public void getExternalIdsFiltersSubstudyInExternalIdentifierInfo() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerSubstudies(ImmutableSet.of("substudyA", "substudyB")).build());
        
        // Verify here that prior to migration, a lack of association doesn't break anything
        DynamoExternalIdentifier extId1 = new DynamoExternalIdentifier(TestConstants.TEST_STUDY_IDENTIFIER, "extId1");
        extId1.setSubstudyId(null); // should see this external identifier record
        DynamoExternalIdentifier extId2 = new DynamoExternalIdentifier(TestConstants.TEST_STUDY_IDENTIFIER, "extId2");
        extId2.setSubstudyId("substudyA");
        DynamoExternalIdentifier extId3 = new DynamoExternalIdentifier(TestConstants.TEST_STUDY_IDENTIFIER, "extId3");
        extId3.setSubstudyId("substudyB");
        DynamoExternalIdentifier extId4 = new DynamoExternalIdentifier(TestConstants.TEST_STUDY_IDENTIFIER, "extId4");
        extId4.setSubstudyId("substudyC");
        DynamoExternalIdentifier extId5 = new DynamoExternalIdentifier(TestConstants.TEST_STUDY_IDENTIFIER, "extId5");
        extId5.setSubstudyId("substudyD");
        setupQueryOfIds(ImmutableList.of(extId1, extId2, extId3, extId4, extId5));
        
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> externalIds = dao.getExternalIds(TEST_STUDY, null, 10,
                null, null);
        assertExternalId(externalIds.getItems().get(0), "extId1", null);  
        assertExternalId(externalIds.getItems().get(1), "extId2", "substudyA");
        assertExternalId(externalIds.getItems().get(2), "extId3", "substudyB");
        assertExternalId(externalIds.getItems().get(3), "extId4", null);
        assertExternalId(externalIds.getItems().get(4), "extId5", null);
    }
    
    private void assertExternalId(ExternalIdentifierInfo info, String expectedExternalId, String substudyId) {
        assertEquals(expectedExternalId, info.getIdentifier());
        assertEquals(substudyId, info.getSubstudyId());
    }
    
    @Test
    public void getExternalIdsPaged() throws Exception {
        int pageSize = 10;
        setupQueryOfStrings(ImmutableList.of("AAA", "BBB", "CCC"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao
                .getExternalIds(new StudyIdentifierImpl("studyId"), null, pageSize, null, false);

        assertEquals(pageSize, result.getRequestParams().get("pageSize"));

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(3, externalIds.size());

        verify(rateLimiter).acquire(anyInt());
        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIdsFirstOfTwoPages() throws Exception {
        // first page has 3 ids, so second page should not be retrieved
        int pageSize = 3;
        setupQueryOfStrings(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao
                .getExternalIds(new StudyIdentifierImpl("studyId"), null, pageSize, null, false);

        assertEquals(pageSize, result.getRequestParams().get("pageSize"));

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(3, externalIds.size());

        verify(rateLimiter).acquire(anyInt());
        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIdsAggregateTwoPages() throws Exception {
        // first page has 3 ids, second (and final) page has 2 ids, DAO should return 5 ids
        int pageSize = 10;
        setupQueryOfStrings(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao
                .getExternalIds(new StudyIdentifierImpl("studyId"), null, pageSize, null, false);

        assertEquals(pageSize, result.getRequestParams().get("pageSize"));

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(5, externalIds.size());

        verify(rateLimiter, times(2)).acquire(anyInt());
        verify(mapper, times(2)).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIdsAggregateTwoPagesTruncated() throws Exception {
        // first page has 3 ids, second page has 2 ids. DAO should return 4 ids
        int pageSize = 4;
        setupQueryOfStrings(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao
                .getExternalIds(new StudyIdentifierImpl("studyId"), null, pageSize, null, false);

        assertEquals(pageSize, result.getRequestParams().get("pageSize"));

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(4, externalIds.size());

        verify(rateLimiter, times(2)).acquire(anyInt());
        verify(mapper, times(2)).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIdsQueryResultsExceedPageSize() {
        int pageSize = 3;
        setupQueryOfStrings(ImmutableList.of("AAA", "BBB", "CCC", "DDD", "EEE"), ImmutableList.of("FFF", "GGG"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao.getExternalIds(TEST_STUDY,
                null, pageSize, null, false);
        assertEquals(3, result.getItems().size());
        assertEquals("CCC", result.getNextPageOffsetKey());
    }

    @Test
    public void createExternalId() {
        dao.createExternalId(externalId);

        verify(mapper).save(externalId);
    }

    @Test
    public void deleteExternalId() {
        dao.deleteExternalId(externalId);

        verify(mapper).delete(externalId);
    }
    
    @Test
    public void commitAssignExternalId() {
        when(mapper.load(any())).thenReturn(externalId);
        externalId.setHealthCode(HEALTH_CODE);
        
        dao.commitAssignExternalId(externalId);
        
        verify(mapper).save(eq(externalId), saveExpressionCaptor.capture());

        DynamoDBSaveExpression expr = saveExpressionCaptor.getValue();
        assertFalse(expr.getExpected().get(HEALTH_CODE).getExists());
    }
    
    @Test
    public void commitAssignExternalIdNullDoesNothing() { 
        dao.commitAssignExternalId(null);
        
        verify(mapper, never()).save(any());
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void commitAssignExternalIdThrows() {
        when(mapper.load(any())).thenReturn(externalId);
        doThrow(new ConditionalCheckFailedException("message")).when(mapper).save(any(),
                any(DynamoDBSaveExpression.class));
        externalId.setHealthCode(HEALTH_CODE);
        
        dao.commitAssignExternalId(externalId);
    }
    
    @Test(expected = ConcurrentModificationException.class)
    public void commitAssignExternalWithoutHealthCodeThrows() {
        dao.commitAssignExternalId(externalId);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void commitAssignExternalNewThrows() { 
        externalId.setHealthCode(HEALTH_CODE);
        
        dao.commitAssignExternalId(externalId);
    }

    @Test
    public void unassignExternalId() {
        externalId.setHealthCode(HEALTH_CODE);
        externalId.setSubstudyId(SUBSTUDY_ID);
        when(mapper.load(any())).thenReturn(externalId);

        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        acctSubstudy.setExternalId(ID);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        account.setId(USER_ID);
        account.setExternalId(ID);
        account.getAccountSubstudies().add(acctSubstudy);

        dao.unassignExternalId(account, ID);

        assertNull(account.getExternalId());
        assertTrue(account.getAccountSubstudies().isEmpty());
    }

    // Or, the wrong ID does not remove the existing ID
    @Test
    public void unassignExternalIdMissingIdDoesNothing() {
        when(mapper.load(any())).thenReturn(null);

        AccountSubstudy as = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        as.setExternalId(ID);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setExternalId(ID);
        account.getAccountSubstudies().add(as);

        dao.unassignExternalId(account, ID);

        verify(mapper, never()).save(any());
        assertEquals(ID, account.getExternalId());
        assertFalse(account.getAccountSubstudies().isEmpty());
    }

    // This could result from a data integrity issue. We will allow unassignment to clear account
    @Test
    public void unassignExternalIdNotAssignedJustUpdatesAccount() {
        externalId.setSubstudyId(SUBSTUDY_ID);
        when(mapper.load(any())).thenReturn(externalId);

        AccountSubstudy as = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        as.setExternalId(ID);

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setExternalId(ID);
        account.setHealthCode(HEALTH_CODE);
        account.setId(USER_ID);
        account.getAccountSubstudies().add(as);

        dao.unassignExternalId(account, ID);

        verify(mapper, never()).save(any());
        assertNull(account.getExternalId());
        assertTrue(account.getAccountSubstudies().isEmpty());
    }

    @Test
    public void pagingWithFilterShorterThanKeyWorks() {
        setupQueryOfStrings(ImmutableList.of("foo1", "foo2"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY,
                "CCCCC", 5, "CC", null);

        assertEquals(5, (int) results.getRequestParams().get("pageSize"));
        assertEquals("CC", (String) results.getRequestParams().get("idFilter"));
        assertEquals("CCCCC", (String) results.getRequestParams().get("offsetKey"));

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        Map<String, AttributeValue> map = query.getExclusiveStartKey();
        assertEquals(TEST_STUDY_IDENTIFIER, map.get(DynamoExternalIdDao.STUDY_ID).getS());
        assertEquals("CCCCC", map.get(DynamoExternalIdDao.IDENTIFIER).getS());

        DynamoExternalIdentifier id = query.getHashKeyValues();
        assertEquals(TEST_STUDY_IDENTIFIER, id.getStudyId());
        assertNull(id.getIdentifier());
    }

    @Test
    public void pagingWithFilterLongerThanKeyWorks() {
        setupQueryOfStrings(ImmutableList.of("foo1", "foo2"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY, "CCCCC", 5,
                "CCCCCCCCCC", null);

        assertEquals(5, (int) results.getRequestParams().get("pageSize"));
        assertEquals("CCCCCCCCCC", (String) results.getRequestParams().get("idFilter"));
        assertEquals("CCCCC", (String) results.getRequestParams().get("offsetKey"));

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        assertNull(query.getExclusiveStartKey());

        DynamoExternalIdentifier id = query.getHashKeyValues();
        assertEquals(TEST_STUDY_IDENTIFIER, id.getStudyId());
        assertNull(id.getIdentifier());
    }
    
    @SafeVarargs
    private final void setupQueryOfIds(List<DynamoExternalIdentifier>... lists) {
        finishMocking(Arrays.asList(lists));
    }
    
    @SafeVarargs
    private final void setupQueryOfStrings(List<String>... lists) {
        // Convert lists of strings to lists of DynamExternalIdentifiers
        List<List<DynamoExternalIdentifier>> extIdLists = Arrays.asList(lists).stream().map(oneList -> {
            return oneList.stream()
                .map(id -> new DynamoExternalIdentifier(TEST_STUDY_IDENTIFIER, id))
                .collect(Collectors.toList());
        }).collect(Collectors.toList());
        
        finishMocking(extIdLists);
    }
    
    private void finishMocking(List<List<DynamoExternalIdentifier>> extIdLists) {
        // Now mock each result
        List<QueryResultPage<DynamoExternalIdentifier>> pages = Lists.newArrayList();
        for (int i = 0; i < extIdLists.size(); i++) {
            List<DynamoExternalIdentifier> oneList = extIdLists.get(i);
            QueryResultPage<DynamoExternalIdentifier> oneResultPage = mock(QueryResultPage.class);
            when(oneResultPage.getResults()).thenReturn(oneList);
            when(oneResultPage.getCount()).thenReturn(oneList.size());
            when(oneResultPage.getConsumedCapacity()).thenReturn(mock(ConsumedCapacity.class));
            if (i < extIdLists.size() - 1) {
                String id = oneList.get(oneList.size() - 1).getIdentifier();
                Map<String, AttributeValue> lastEvaluatedKey = Maps.newHashMap();
                lastEvaluatedKey.put(IDENTIFIER, new AttributeValue().withS(id));
                when(oneResultPage.getLastEvaluatedKey()).thenReturn(lastEvaluatedKey);
            } else {
                when(oneResultPage.getLastEvaluatedKey()).thenReturn(null);
            }
            pages.add(oneResultPage);
        }
        
        // Now mock returning one or more pages
        when(mapper.queryPage(eq(DynamoExternalIdentifier.class), any()))
                .thenAnswer(new Answer<QueryResultPage<DynamoExternalIdentifier>>() {
                    private int count = -1;
            public QueryResultPage<DynamoExternalIdentifier> answer(InvocationOnMock invocation) {
                count++;
                return (count < pages.size()) ? pages.get(count) : null;
            }
        });
    }
}
