package org.sagebionetworks.bridge.dynamodb;


import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dynamodb.DynamoExternalIdDao.IDENTIFIER;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

/**
 * Created by jyliu on 5/26/2017.
 */
public class DynamoExternalIdDaoMockTest {

    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private DynamoDBMapper mapper;

    private DynamoExternalIdDao dao;

    @Before
    public void setupTest() {
        MockitoAnnotations.initMocks(this);
        dao = new DynamoExternalIdDao();
        dao.setMapper(mapper);
        dao.setGetExternalIdRateLimiter(rateLimiter);
    }

    @Test
    public void getExternalIds() throws Exception {
        int pageSize = 10;

        QueryResultPage<DynamoExternalIdentifier> resultPage = mock(QueryResultPage.class);

        List<DynamoExternalIdentifier> identifiers = createIds(
                "AAA",
                "BBB",
                "CCC"
        );
        when(resultPage.getResults()).thenReturn(identifiers);

        when(resultPage.getLastEvaluatedKey()).thenReturn(null);

        when(rateLimiter.acquire(pageSize)).thenReturn(0.0);

        when(mapper.queryPage(eq(DynamoExternalIdentifier.class), any())).thenReturn(resultPage);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result =
                dao.getExternalIds(
                        new StudyIdentifierImpl("studyId"),
                        null,
                        pageSize,
                        null,
                        false);

        assertEquals(pageSize, result.getPageSize());

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(3, externalIds.size());

        verify(rateLimiter).acquire(pageSize);
        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIds_FirstOfTwoPages() throws Exception {
        // first page has 3 ids, so second page should not be retrieved
        int pageSize = 3;
        setupTwoPages();

        when(rateLimiter.acquire(pageSize)).thenReturn(0.0);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result =
                dao.getExternalIds(
                        new StudyIdentifierImpl("studyId"),
                        null,
                        pageSize,
                        null,
                        false);

        assertEquals(pageSize, result.getPageSize());

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(3, externalIds.size());

        verify(rateLimiter).acquire(pageSize);
        verify(mapper).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIds_AggregateTwoPages() throws Exception {
        // first page has 3 ids, second (and final) page has 2 ids, DAO should return 5 ids
        int pageSize = 10;
        setupTwoPages();

        when(rateLimiter.acquire(pageSize)).thenReturn(0.0);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result =
                dao.getExternalIds(
                        new StudyIdentifierImpl("studyId"),
                        null,
                        pageSize,
                        null,
                        false);

        assertEquals(pageSize, result.getPageSize());

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(5, externalIds.size());

        verify(rateLimiter, times(2)).acquire(pageSize);
        verify(mapper, times(2)).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    @Test
    public void getExternalIds_AggregateTwoPagesTruncated() throws Exception {
        // first page has 3 ids, second page has 2 ids. DAO should return 4 ids
        int pageSize = 4;
        setupTwoPages();

        when(rateLimiter.acquire(pageSize)).thenReturn(0.0);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> result =
                dao.getExternalIds(
                        new StudyIdentifierImpl("studyId"),
                        null,
                        pageSize,
                        null,
                        false);

        assertEquals(pageSize, result.getPageSize());

        List<ExternalIdentifierInfo> externalIds = result.getItems();
        assertEquals(4, externalIds.size());

        verify(rateLimiter, times(2)).acquire(pageSize);
        verify(mapper, times(2)).queryPage(eq(DynamoExternalIdentifier.class), any());
    }

    private void setupTwoPages() {
        QueryResultPage<DynamoExternalIdentifier> resultPage1 = mock(QueryResultPage.class);
        List<DynamoExternalIdentifier> ids1 = createIds(
                "AAA",
                "BBB",
                "CCC"
        );
        when(resultPage1.getResults()).thenReturn(ids1);

        Map<String, AttributeValue> lastEvaluatedKey1 = Maps.newHashMap();
        lastEvaluatedKey1.put(IDENTIFIER, new AttributeValue().withS("CCC"));
        when(resultPage1.getLastEvaluatedKey()).thenReturn(lastEvaluatedKey1);

        QueryResultPage<DynamoExternalIdentifier> resultPage2 = mock(QueryResultPage.class);
        List<DynamoExternalIdentifier> ids2 = createIds(
                "DDD",
                "EEE"
        );
        when(resultPage2.getResults()).thenReturn(ids2);
        when(resultPage2.getLastEvaluatedKey()).thenReturn(null);

        when(mapper.queryPage(eq(DynamoExternalIdentifier.class), any()))
                .thenReturn(resultPage1)
                .thenReturn(resultPage2);
    }

    private List<DynamoExternalIdentifier> createIds(String... ids) {
        List<DynamoExternalIdentifier> identifiers = Lists.newArrayListWithCapacity(ids.length);
        for (String id : ids) {
            identifiers.add(createId(id));
        }
        return identifiers;
    }

    private DynamoExternalIdentifier createId(String id) {
        return new DynamoExternalIdentifier(new StudyIdentifierImpl("studyId"), id);
    }

}