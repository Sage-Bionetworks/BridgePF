package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class DynamoAppConfigElementDaoTest {

    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private DynamoIndexHelper mockIndexHelper;
    
    @Mock
    private PaginatedQueryList<DynamoAppConfigElement> mockResults;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoAppConfigElement>> queryCaptor;
    
    @Captor
    private ArgumentCaptor<AppConfigElement> appConfigElementCaptor;
    
    private DynamoAppConfigElementDao dao;
    
    @Before
    public void before() {
        dao = new DynamoAppConfigElementDao();
        dao.setMapper(mockMapper);
        dao.setIndexHelper(mockIndexHelper);
    }
    
    @Test
    public void getMostRecentElementsIncludeDeleted() {
        when(mockIndexHelper.queryKeys(DynamoAppConfigElement.class, "studyId", TestConstants.TEST_STUDY.getIdentifier(),
                null)).thenReturn(appConfigElementListId1And2());
        when(mockMapper.load(any())).thenAnswer(invocation -> invocation.getArgumentAt(0, DynamoAppConfigElement.class));
        
        List<AppConfigElement> returned = dao.getMostRecentElements(TestConstants.TEST_STUDY, true);
        assertEquals(2, returned.size());
        assertIdAndRevision(returned.get(0), "id1", 3L);
        assertIdAndRevision(returned.get(1), "id2", 3L);
    }
    
    @Test
    public void getMostRecentElementsExcludeDeleted() {
        when(mockIndexHelper.queryKeys(DynamoAppConfigElement.class, "studyId", TestConstants.TEST_STUDY.getIdentifier(),
                null)).thenReturn(appConfigElementListId1And2());
        when(mockMapper.load(any())).thenAnswer(invocation -> invocation.getArgumentAt(0, DynamoAppConfigElement.class));
        
        List<AppConfigElement> returned = dao.getMostRecentElements(TestConstants.TEST_STUDY, false);
        assertEquals(2, returned.size());
        assertIdAndRevision(returned.get(0), "id1", 2L);
        assertIdAndRevision(returned.get(1), "id2", 3L);
    }
    
    @Test
    public void getMostRecentElementsNoResults() {
        when(mockIndexHelper.queryKeys(DynamoAppConfigElement.class, "studyId", TestConstants.TEST_STUDY.getIdentifier(),
                null)).thenReturn(ImmutableList.of());
        
        List<AppConfigElement> returned = dao.getMostRecentElements(TestConstants.TEST_STUDY, false);
        assertTrue(returned.isEmpty());
    }

    
    @Test
    public void getMostRecentlyPublishedElement() {
        DynamoAppConfigElement element = new DynamoAppConfigElement();
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.get(0)).thenReturn(element);
        
        AppConfigElement returned = dao.getMostRecentlyPublishedElement(TestConstants.TEST_STUDY, "id");
        assertEquals(element, returned);
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals("api:id", query.getHashKeyValues().getKey());
        assertFalse(query.isScanIndexForward());
        assertEquals(new Integer(1), query.getLimit());
        
        Condition deleteCondition = query.getQueryFilter().get("deleted");
        assertEquals("EQ", deleteCondition.getComparisonOperator());
        assertFalse(deleteCondition.getAttributeValueList().get(0).getBOOL());
        
        Condition publishedCondition = query.getQueryFilter().get("published");
        assertEquals("EQ", publishedCondition.getComparisonOperator());
        assertTrue(publishedCondition.getAttributeValueList().get(0).getBOOL());
    }

    @Test
    public void getMostRecentlyPublishedElementNotFound() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.get(0)).thenReturn(null);
        
        AppConfigElement returned = dao.getMostRecentlyPublishedElement(TestConstants.TEST_STUDY, "id");
        assertNull(returned);
    }
    
    @Test
    public void getElementRevisionsIncludesDeleted() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.stream()).thenReturn(appConfigElementListId1().stream());
        
        List<AppConfigElement> returned = dao.getElementRevisions(TestConstants.TEST_STUDY, "id1", true);
        assertEquals(appConfigElementListId1(), returned);
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals("api:id1", query.getHashKeyValues().getKey());
        assertFalse(query.isScanIndexForward());
        assertNull(query.getQueryFilter());
    }
    
    @Test
    public void getElementRevisionsExcludesDeleted() {
        when(mockMapper.query(eq(DynamoAppConfigElement.class), any())).thenReturn(mockResults);
        when(mockResults.stream()).thenReturn(appConfigElementListId1().stream());
        
        List<AppConfigElement> returned = dao.getElementRevisions(TestConstants.TEST_STUDY, "id1", false);
        assertEquals(appConfigElementListId1(), returned);
        
        verify(mockMapper).query(eq(DynamoAppConfigElement.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoAppConfigElement> query = queryCaptor.getValue();
        
        assertEquals("api:id1", query.getHashKeyValues().getKey());
        assertFalse(query.isScanIndexForward());
        
        Condition deleteCondition = query.getQueryFilter().get("deleted");
        assertEquals("EQ", deleteCondition.getComparisonOperator());
        assertFalse(deleteCondition.getAttributeValueList().get(0).getBOOL());
    }
    
    @Test
    public void getElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        when(mockMapper.load(any())).thenReturn(element);
        
        AppConfigElement returned = dao.getElementRevision(TestConstants.TEST_STUDY, "id", 3L);
        assertEquals(element, returned);
        
        verify(mockMapper).load(appConfigElementCaptor.capture());
        assertEquals("api:id", appConfigElementCaptor.getValue().getKey());
        assertEquals(new Long(3), appConfigElementCaptor.getValue().getRevision());
    }
    
    @Test
    public void saveElementRevision() {
        AppConfigElement element = AppConfigElement.create();
        element.setVersion(1L);
        
        VersionHolder returned = dao.saveElementRevision(element);
        assertEquals(new Long(1), returned.getVersion());
        
        verify(mockMapper).save(element);
    }
    
    @Test
    public void deleteElementRevisionPermanently() {
        AppConfigElement key = new DynamoAppConfigElement();
        key.setKey("api:id");
        key.setRevision(3L);
        when(mockMapper.load(key)).thenReturn(key);
        
        dao.deleteElementRevisionPermanently(TestConstants.TEST_STUDY, "id", 3L);
        
        verify(mockMapper).delete(appConfigElementCaptor.capture());
        assertEquals("api:id", key.getKey());
        assertEquals(new Long(3), key.getRevision());
    }
    
    @Test
    public void deleteElementRevisionPermanentlyNotFound() {
        when(mockMapper.load(any())).thenReturn(null);
        
        dao.deleteElementRevisionPermanently(TestConstants.TEST_STUDY, "id", 3L);
        
        verify(mockMapper, never()).delete(any());
    }
    
    // As will happen if version attribute isn't returned or is wrong
    @Test(expected = ConcurrentModificationException.class)
    public void saveElementRevisionThrowsConditionalCheckFailedException() {
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).save(any());
        
        AppConfigElement element = TestUtils.getAppConfigElement();
        
        dao.saveElementRevision(element);
    }
    
    // As will happen if version attribute isn't returned or is wrong
    @Test(expected = ConcurrentModificationException.class)
    public void deleteElementRevisionPermanentlyThrowsConditionalCheckFailedException() {
        AppConfigElement element = TestUtils.getAppConfigElement();
        when(mockMapper.load(any())).thenReturn(element);
        doThrow(new ConditionalCheckFailedException("")).when(mockMapper).delete(any());
        
        dao.deleteElementRevisionPermanently(TestConstants.TEST_STUDY, "id", 1L);        
    }
    
    private List<DynamoAppConfigElement> appConfigElementListId1() {
        DynamoAppConfigElement el1V1 = new DynamoAppConfigElement();
        el1V1.setKey("api:id1");
        el1V1.setId("id1");
        el1V1.setRevision(1L);
        
        DynamoAppConfigElement el1V2 = new DynamoAppConfigElement();
        el1V2.setKey("api:id1");
        el1V2.setId("id1");
        el1V2.setRevision(2L);
        
        return ImmutableList.of(el1V1, el1V2);
    }
    
    private List<DynamoAppConfigElement> appConfigElementListId1And2() {
        DynamoAppConfigElement el1V1 = new DynamoAppConfigElement();
        el1V1.setKey("api:id1");
        el1V1.setId("id1");
        el1V1.setRevision(1L);
        
        DynamoAppConfigElement el1V2 = new DynamoAppConfigElement();
        el1V2.setKey("api:id1");
        el1V2.setId("id1");
        el1V2.setRevision(2L);
        
        DynamoAppConfigElement el1V3 = new DynamoAppConfigElement();
        el1V3.setKey("api:id1");
        el1V3.setId("id1");
        el1V3.setRevision(3L);
        
        DynamoAppConfigElement el2V1 = new DynamoAppConfigElement();
        el2V1.setKey("api:id2");
        el2V1.setId("id2");
        el2V1.setRevision(1L);
        
        DynamoAppConfigElement el2V2 = new DynamoAppConfigElement();
        el2V2.setKey("api:id2");
        el2V2.setId("id2");
        el2V2.setRevision(2L);
        
        DynamoAppConfigElement el2V3 = new DynamoAppConfigElement();
        el2V3.setKey("api:id2");
        el2V3.setId("id2");
        el2V3.setRevision(3L);
        
        el1V3.setDeleted(true);
        el2V2.setDeleted(true);
        return ImmutableList.of(el1V1, el1V2, el1V3, el2V1, el2V2, el2V3);
    }
    
    private void assertIdAndRevision(AppConfigElement element, String id, long revision) {
        String key = TestConstants.TEST_STUDY_IDENTIFIER + ":" + id;
        assertTrue("Missing: " + id + ", " + revision,
                element.getKey().equals(key) && element.getRevision() == revision);
    }    
}
