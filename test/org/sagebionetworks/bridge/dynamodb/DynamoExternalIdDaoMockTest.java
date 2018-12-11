package org.sagebionetworks.bridge.dynamodb;

import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.BEGINS_WITH;
import static com.amazonaws.services.dynamodbv2.model.ComparisonOperator.NOT_NULL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.IDENTIFIER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.ReturnConsumedCapacity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;

@RunWith(MockitoJUnitRunner.class)
public class DynamoExternalIdDaoMockTest {

    private static final String HEALTH_CODE = "healthCode";
    private static final String USER_ID = "userId";
    private static final String ID = "external-id";
    private static final String SUBSTUDY_ID = "substudy-id";
    private static final ExternalIdentifier EXTERNAL_ID = ExternalIdentifier.create(TEST_STUDY, ID);

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private DynamoDBMapper mapper;
    @Captor
    private ArgumentCaptor<ExternalIdentifier> idCaptor;
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoExternalIdentifier>> queryCaptor;
    @Captor
    private ArgumentCaptor<DynamoDBSaveExpression> saveExpressionCaptor;

    private DynamoExternalIdDao dao;

    @Before
    public void setupTest() {
        EXTERNAL_ID.setHealthCode(null);
        EXTERNAL_ID.setSubstudyId(null);
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
        when(mapper.load(any())).thenReturn(EXTERNAL_ID);

        ExternalIdentifier retrieved = dao.getExternalId(TEST_STUDY, ID);

        verify(mapper).load(idCaptor.capture());
        assertEquals(EXTERNAL_ID, idCaptor.getValue());
        assertEquals(retrieved, EXTERNAL_ID);
    }

    @Test
    public void getExternalIdReturnsNull() {
        when(mapper.load(any())).thenReturn(null);

        ExternalIdentifier retrieved = dao.getExternalId(TEST_STUDY, ID);

        verify(mapper).load(idCaptor.capture());
        assertEquals(EXTERNAL_ID, idCaptor.getValue());
        assertNull(retrieved);
    }

    @Test
    public void getExternalIds() {
        setupQuery(ImmutableList.of("foo1", "foo2"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY,
                "offsetKey", 50, "offset", true);
        
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

    @Test
    public void getExternalIdsNullValues() {
        setupQuery(ImmutableList.of("foo1", "foo2"));

        // Note that the page size is enforced in the service, and should never be 0
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = dao.getExternalIds(TEST_STUDY,
                null, 0, null, null);
        Map<String,Object> paramsMap = results.getRequestParams();
        assertEquals(0, (int) paramsMap.get(ResourceList.PAGE_SIZE));
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
        setupQuery(ImmutableList.of("foo1", "foo2"));

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
    public void getExternalIdsRespectsSubstudyFiltering() {
        Set<String> substudies = ImmutableSet.of("substudyA", "substudyB");
        setupQuery(ImmutableList.of("foo1", "foo2"));
        BridgeUtils.setRequestContext(new RequestContext.Builder().withCallerSubstudies(substudies).build());

        dao.getExternalIds(TEST_STUDY, null, 50, null, false);

        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), queryCaptor.capture());

        DynamoDBQueryExpression<DynamoExternalIdentifier> query = queryCaptor.getValue();

        List<AttributeValue> substudyAttList = substudies.stream().map(substudyId -> new AttributeValue(substudyId))
                .collect(Collectors.toList());

        Condition anotherRangeCondition = query.getRangeKeyConditions().get(DynamoExternalIdDao.SUBSTUDY_ID);
        assertEquals(ComparisonOperator.CONTAINS.toString(), anotherRangeCondition.getComparisonOperator());
        assertEquals(substudyAttList, anotherRangeCondition.getAttributeValueList());
    }

    @Test
    public void getExternalIdsPaged() throws Exception {
        int pageSize = 10;
        setupQuery(ImmutableList.of("AAA", "BBB", "CCC"));

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
        setupQuery(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

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
        setupQuery(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

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
        setupQuery(ImmutableList.of("AAA", "BBB", "CCC"), ImmutableList.of("DDD", "EEE"));

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
        setupQuery(ImmutableList.of("AAA", "BBB", "CCC", "DDD", "EEE"), ImmutableList.of("FFF", "GGG"));

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result = dao.getExternalIds(TEST_STUDY,
                null, pageSize, null, false);
        assertEquals(3, result.getItems().size());
        assertEquals("CCC", result.getNextPageOffsetKey());
    }

    @Test
    public void createExternalId() {
        dao.createExternalId(EXTERNAL_ID);

        verify(mapper).save(idCaptor.capture());
        assertEquals(EXTERNAL_ID, idCaptor.getValue());
    }

    @Test
    public void deleteExternalId() {
        dao.deleteExternalId(EXTERNAL_ID);

        verify(mapper).delete(idCaptor.capture());
        assertEquals(EXTERNAL_ID, idCaptor.getValue());
    }

    @Test
    public void assignExternalId() {
        EXTERNAL_ID.setSubstudyId(SUBSTUDY_ID);
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        account.setId(USER_ID);

        when(mapper.load(any())).thenReturn(EXTERNAL_ID);

        dao.assignExternalId(account, ID);

        verify(mapper).save(idCaptor.capture(), saveExpressionCaptor.capture());

        ExternalIdentifier externalId = idCaptor.getValue();
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertAccountHasCorrectSubstudy(account);

        DynamoDBSaveExpression saveExpr = saveExpressionCaptor.getValue();
        assertFalse(saveExpr.getExpected().get(DynamoExternalIdDao.HEALTH_CODE).getExists());
    }

    @Test
    public void assignExternalIdMissingIdDoesNothing() {
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);

        when(mapper.load(any())).thenReturn(null);

        dao.assignExternalId(account, ID);

        assertNull(account.getExternalId());
        assertTrue(account.getAccountSubstudies().isEmpty());
        verify(mapper, never()).save(any(), any(DynamoDBSaveExpression.class));
    }

    // This could be the result of a data integrity issue, so although we don't update externalId,
    // we do update the account to match it.
    @Test
    public void assignExternalIdAlreadySetUpdatesAccountOnly() {
        EXTERNAL_ID.setHealthCode(HEALTH_CODE);
        EXTERNAL_ID.setSubstudyId("substudyA");

        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        account.setId(USER_ID);

        when(mapper.load(any())).thenReturn(EXTERNAL_ID);

        dao.assignExternalId(account, ID);

        assertEquals(ID, account.getExternalId()); // legacy

        verify(mapper, never()).save(any(), any(DynamoDBSaveExpression.class));
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void assignExistingExternalIdThrows() {
        EXTERNAL_ID.setHealthCode("someOtherHealthCode");
        Account account = Account.create();
        account.setStudyId(TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        account.setId(USER_ID);

        when(mapper.load(any())).thenReturn(EXTERNAL_ID);
        doThrow(new ConditionalCheckFailedException("message")).when(mapper).save(any(),
                any(DynamoDBSaveExpression.class));

        dao.assignExternalId(account, ID);
    }

    @Test
    public void unassignExternalId() {
        EXTERNAL_ID.setHealthCode(HEALTH_CODE);
        EXTERNAL_ID.setSubstudyId(SUBSTUDY_ID);
        when(mapper.load(any())).thenReturn(EXTERNAL_ID);

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
        EXTERNAL_ID.setSubstudyId(SUBSTUDY_ID);
        when(mapper.load(any())).thenReturn(EXTERNAL_ID);

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
        setupQuery(ImmutableList.of("foo1", "foo2"));

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
        setupQuery(ImmutableList.of("foo1", "foo2"));

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

    private void assertAccountHasCorrectSubstudy(Account account) {
        AccountSubstudy acctSubstudy = AccountSubstudy.create(TEST_STUDY_IDENTIFIER, SUBSTUDY_ID, USER_ID);
        acctSubstudy.setExternalId(ID);
        assertEquals(acctSubstudy, Iterables.getFirst(account.getAccountSubstudies(), null));
    }

    @SuppressWarnings("unchecked")
    @SafeVarargs
    private final void setupQuery(List<String>... lists) {
        // Convert lists of strings to lists of DynamExternalIdentifiers
        List<List<DynamoExternalIdentifier>> extIdLists = Arrays.asList(lists).stream().map(oneList -> {
            return oneList.stream()
                .map(id -> new DynamoExternalIdentifier(TEST_STUDY_IDENTIFIER, id))
                .collect(Collectors.toList());
        }).collect(Collectors.toList());
        
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
