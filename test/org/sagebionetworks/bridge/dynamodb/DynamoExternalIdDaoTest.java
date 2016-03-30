package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoExternalIdDaoTest {
    
    private static final List<String> EXT_IDS = Lists.newArrayList("AAA", "BBB", "CCC");
    
    @Resource
    private DynamoExternalIdDao dao;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    @Resource
    private BridgeConfig config;
    
    private StudyIdentifier studyId;

    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoExternalIdDaoTest.class));
        
        dao.setConfig(config);
        dao.addExternalIds(studyId, EXT_IDS);
    }
    
    @After
    public void after() {
        dao.deleteExternalIds(studyId, EXT_IDS);
    }
    
    private Config makeConfig(int addLimit, int timeout) {
        Config config = mock(Config.class);
        when(config.getInt(DynamoExternalIdDao.CONFIG_KEY_ADD_LIMIT)).thenReturn(addLimit);
        when(config.getInt(DynamoExternalIdDao.CONFIG_KEY_LOCK_DURATION)).thenReturn(timeout);
        return config;
    }
    
    @Test(expected = BadRequestException.class)
    public void addCannotExceedLimit() {
        dao.setConfig(makeConfig(2, 1000));
        
        dao.addExternalIds(studyId, EXT_IDS);
    }
    
    @Test
    public void cannotAddExistingIdentifiers() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        dao.addExternalIds(studyId, EXT_IDS);
        
        DynamoDBQueryExpression<DynamoExternalIdentifier> query = new DynamoDBQueryExpression<DynamoExternalIdentifier>();
        query.withScanIndexForward(false);
        query.withHashKeyValues(new DynamoExternalIdentifier(studyId, null));
        
        PaginatedQueryList<? extends DynamoExternalIdentifier> page = mapper.query(DynamoExternalIdentifier.class, query);
        Set<String> ids = page.stream().map(item -> {
            return item.getIdentifier();
        }).collect(Collectors.toSet());
        assertEquals(Sets.newHashSet("AAA","BBB","CCC"), ids);
        
        // Just as importantly, AAA is still assigned to "healthCode"
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertEquals("healthCode", identifier.getHealthCode());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void cannotAddNullValues() {
        mapper = spy(mapper);
        dao.setMapper(mapper);
        try {
            dao.addExternalIds(studyId, Lists.newArrayList("DDD", null));
            fail("Should have thrown exception");
        } catch(BadRequestException e) {}
        verify(mapper, never()).load(any());
        verify(mapper, never()).batchSave((List<DynamoExternalIdentifier>)any());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void cannotAddEmptyValues() {
        mapper = spy(mapper);
        dao.setMapper(mapper);
        try {
            dao.addExternalIds(studyId, Lists.newArrayList("DDD", "  \t"));
            fail("Should have thrown exception");
        } catch(BadRequestException e) {}
        verify(mapper, never()).load(any());
        verify(mapper, never()).batchSave((List<DynamoExternalIdentifier>)any());
    }
    
    @Test
    public void reservationSucceedsFirstTime() {
        dao.reserveExternalId(studyId, "AAA");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertTrue(identifier.getReservation() > 0L);
    }
    
    @Test
    public void reservationSucceedsAfterLockExpires() throws Exception {
        dao.setConfig(makeConfig(10, 1000));
        
        dao.reserveExternalId(studyId, "AAA");
        Thread.sleep(1000);
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void reservationFailsOnHealthCodeAssigned() {
        dao.assignExternalId(studyId, "AAA", "some-health-code");
        
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void reservationFailsOnReservationWindow() {
        dao.reserveExternalId(studyId, "AAA");
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reservationFailsOnCodeDoesNotExist() {
        dao.reserveExternalId(studyId, "DDD");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reservationFailsOnCodeOutsideStudy() {
        StudyIdentifier studyId = new StudyIdentifierImpl("some-other-study");
        dao.reserveExternalId(studyId, "AAA");
    }
    
    @Test
    public void canAssignExternalId() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertEquals("healthCode", identifier.getHealthCode());
        assertEquals(0, identifier.getReservation());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void assignMissingExternalIdThrowException() {
        dao.assignExternalId(studyId, "DDD", "healthCode");
    }
    
    @Test
    public void canReassignHealthCodeSafely() {
        // Well-behaved client code shouldn't do this, but if it happens it does not throw an exception
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.assignExternalId(studyId, "AAA", "healthCode");
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void identifierCannotBeAssignedTwice() {
        // Well-behaved client code shouldn't do this, but if it happens, it will not succeed.
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.assignExternalId(studyId, "AAA", "differentHealthCode");
    }
    
    @Test
    public void identifierCanBeUnassigned() {
        dao.assignExternalId(studyId, "AAA", "healthCode");
        
        dao.unassignExternalId(studyId, "AAA");
        
        DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId, "AAA");
        DynamoExternalIdentifier identifier = mapper.load(keyObject);
        assertNull(identifier.getHealthCode());
        assertEquals(0L, identifier.getReservation());
    }
    
    @Test
    public void unassignFailsQuietly() {
        dao.unassignExternalId(studyId, "AAA"); // never assigned
        dao.unassignExternalId(studyId, "DDD"); // doesn't exist
    }
    
    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeNegative() {
        dao.getExternalIds(studyId, null, -100, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void pageSizeCannotBeGreaterThan100() {
        dao.getExternalIds(studyId, null, 101, null, null);
    }
    
    @Test
    public void canGetIds() {
        PagedResourceList<String> page = dao.getExternalIds(studyId, null, 10, null, null);
        
        assertEquals(3, page.getItems().size());
        assertEquals(10, page.getPageSize());
        assertEquals(3, page.getTotal());
    }
    
    @Test
    public void canFilterIds() {
        List<String> moreIds = Lists.newArrayList("aaa", "bbb", "ccc", "DDD", "AEE", "AFF");
        try {
            dao.addExternalIds(studyId, moreIds);
            
            // AAA, AEE, AFF
            PagedResourceList<String> page = dao.getExternalIds(studyId, null, 10, "A", null);
            assertEquals(3, page.getItems().size());
            assertEquals(10, page.getPageSize());
            assertEquals("A", page.getLastKey());
            assertEquals(3, page.getTotal());

            // Nothing matches this filter
            page = dao.getExternalIds(studyId, null, 10, "ZZZ", null);
            assertEquals(0, page.getItems().size());
            assertEquals(10, page.getPageSize());
            assertEquals("ZZZ", page.getFilters().get("idFilter"));
            assertEquals(0, page.getTotal());
            
            dao.assignExternalId(studyId, "AAA", "healthCode1");
            dao.assignExternalId(studyId, "BBB", "healthCode1");

            page = dao.getExternalIds(studyId, null, 10, null, Boolean.TRUE);
            assertEquals(2, page.getItems().size());
            assertEquals(Sets.newHashSet("AAA", "BBB"), Sets.newHashSet(page.getItems()));
        } finally {
            dao.deleteExternalIds(studyId, moreIds);
        }
    }
    
    @Test
    public void canRetrieveCurrentAndNextPage() {
        dao.setConfig(makeConfig(23, 2000));
        List<String> moreIds = Lists.newArrayList("DDD", "EEE", "FFF", "GGG", "HHH", "III", "JJJ", "KKK", "LLL", "MMM",
                "NNN", "OOO", "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW", "XXX", "YYY", "ZZZ");
        try {
            dao.addExternalIds(studyId, moreIds);

            PagedResourceList<String> page = dao.getExternalIds(studyId, null, 5, null, null);
            assertEquals(5, page.getItems().size());
            assertEquals(26, page.getTotal());
            assertEquals("EEE", page.getLastKey());
            assertEquals(Sets.newHashSet("AAA","BBB","CCC","DDD","EEE"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getLastKey(), 5, null, null);
            assertEquals(Sets.newHashSet("FFF","GGG","HHH","III","JJJ"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getLastKey(), 5, null, null);
            assertEquals(Sets.newHashSet("KKK","LLL","MMM","NNN","OOO"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getLastKey(), 15, null, null);
            assertEquals(Sets.newHashSet("PPP","QQQ","RRR","SSS","TTT","UUU","VVV","WWW","XXX","YYY","ZZZ"), Sets.newHashSet(page.getItems()));
            assertEquals(26, page.getTotal());
            assertNull(page.getLastKey());
            
        } finally {
            dao.deleteExternalIds(studyId, moreIds);
        }
    }
    
    // Sometimes paging fails when the total records divided by the page has no remainder. 
    // So last item on last page is the last record. Verify this works.
    @Test
    public void pagingWithNoRemainderWorks() {
        List<String> moreIds = Lists.newArrayList("DDD", "EEE", "FFF");
        try {
            dao.addExternalIds(studyId, moreIds);
            
            PagedResourceList<String> page = dao.getExternalIds(studyId, null, 3, null, null);
            assertEquals(6, page.getTotal());
            assertEquals(3, page.getItems().size());
            assertNotNull(page.getLastKey());
            
            page = dao.getExternalIds(studyId, page.getLastKey(), 3, null, null);
            assertEquals(6, page.getTotal());
            assertEquals(3, page.getItems().size());
            assertNull(page.getLastKey());
        } finally {
            dao.deleteExternalIds(studyId, moreIds);
        }
    }
    
    @Test
    public void retrieveUnassignedExcludesReserved() throws Exception {
        dao.setConfig(makeConfig(10, 1000));
        
        dao.assignExternalId(studyId, "AAA", "healthCode1");
        dao.reserveExternalId(studyId, "BBB"); // only reserved
        
        PagedResourceList<String> page = dao.getExternalIds(studyId, null, 5, null, Boolean.FALSE);
        assertEquals(1, page.getItems().size());
        assertEquals("CCC", page.getItems().get(0));
        
        Thread.sleep(1000);
        page = dao.getExternalIds(studyId, null, 5, null, Boolean.FALSE);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().contains("BBB"));
        assertTrue(page.getItems().contains("CCC"));
    }
    
    @Test
    public void retrieveAssignedIncludesReserved() throws Exception {
        dao.setConfig(makeConfig(10, 1000));
        
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.reserveExternalId(studyId, "BBB");
        
        PagedResourceList<String> page = dao.getExternalIds(studyId, null, 5, null, Boolean.TRUE);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().contains("AAA"));
        assertTrue(page.getItems().contains("BBB"));
        
        // Wait until lock is released, item is no longer in results that are considered assigned.
        Thread.sleep(10000);
        page = dao.getExternalIds(studyId, null, 5, null, Boolean.TRUE);
        assertEquals(1, page.getItems().size());
        assertTrue(page.getItems().contains("AAA"));
    }
    
    @Test
    public void getNextAvailableID() {
        // We should skip over reserved and assigned IDs to find a free one
        dao.assignExternalId(studyId, "AAA", "healthCode");
        dao.reserveExternalId(studyId, "BBB");
        
        PagedResourceList<String> ids = dao.getExternalIds(studyId, null, 1, null, Boolean.FALSE);
        
        assertEquals(1, ids.getItems().size());
        assertEquals("CCC", ids.getItems().get(0));
    }
    
}
