package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoExternalIdDaoTest {
    
    private static final List<String> EXT_IDS = Lists.newArrayList("AAA", "BBB", "CCC");
    
    @Resource
    private DynamoExternalIdDao dao;
    
    @Resource(name = "externalIdDdbMapper")
    private DynamoDBMapper mapper;
    
    private StudyIdentifier studyId;

    @Before
    public void before() {
        studyId = new StudyIdentifierImpl(TestUtils.randomName(DynamoExternalIdDaoTest.class));
        dao.createExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(0)));
        dao.createExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(1)));
        dao.createExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(2)));
    }
    
    @After
    public void after() {
        dao.deleteExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(0)));
        dao.deleteExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(1)));
        dao.deleteExternalId(ExternalIdentifier.create(studyId, EXT_IDS.get(2)));
    }
    
    @Test
    public void getExistingId() {
        assertEquals("AAA", dao.getExternalId(studyId, "AAA").getIdentifier());
    }
    
    @Test
    public void getExistingIdReturnsNull() {
        assertNull(dao.getExternalId(studyId, "does-not-exist"));
    }
    
    private Account createAccount(String healthCode) {
        Account account = Account.create();
        account.setId(BridgeUtils.generateGuid());
        account.setStudyId(studyId.getIdentifier());
        account.setHealthCode(healthCode);
        return account;
    }

    @Test
    public void missingIdentifierDoesNothing() {
        DynamoDBMapper spiedMapper = Mockito.spy(mapper);
        
        Account account = createAccount("healthCode");
        ExternalIdentifier externalId = setupExternalIdentifier(account, "missing");
        dao.commitAssignExternalId(externalId);
        
        verify(spiedMapper, never()).save(any(), (DynamoDBSaveExpression)any());
    }

    @Test
    public void matchingHealthCodeDoesNothing() {
        Account account = createAccount("healthCode");
        
        ExternalIdentifier externalId = setupExternalIdentifier(account, "missing");
        dao.commitAssignExternalId(externalId);
        
        DynamoDBMapper spiedMapper = Mockito.spy(mapper);
        
        externalId = setupExternalIdentifier(account, "AAA");
        dao.commitAssignExternalId(externalId);
        
        verify(spiedMapper, never()).save(any(), (DynamoDBSaveExpression)any());
    }

    @Test
    public void availableExternalIdIsAssigned() {
        Account account = createAccount("healthCode");
        
        ExternalIdentifier externalId = setupExternalIdentifier(account, "AAA");
        dao.commitAssignExternalId(externalId);
        
        externalId = dao.getExternalId(studyId, "AAA");
        assertEquals("AAA", externalId.getIdentifier());
        assertEquals("healthCode", externalId.getHealthCode());
        assertEquals(studyId.getIdentifier(), externalId.getStudyId());
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void assignedExternalIdThrowsException() {
        Account account1 = createAccount("healthCode");
        Account account2 = createAccount("differentHealthCode");
        ExternalIdentifier externalId = setupExternalIdentifier(account1, "AAA");
        dao.commitAssignExternalId(externalId);

        externalId = setupExternalIdentifier(account2, "AAA");
        dao.commitAssignExternalId(externalId);
    }

    @Test
    public void identifierCanBeUnassigned() throws Exception {
        ExternalIdentifier extId = ExternalIdentifier.create(studyId, "AAA");
        try {
            dao.createExternalId(extId);
            
            Account account = createAccount("healthCode");
            
            ExternalIdentifier externalId = setupExternalIdentifier(account, "AAA");
            dao.commitAssignExternalId(externalId);
            Thread.sleep(1000);
            DynamoExternalIdentifier keyObject = new DynamoExternalIdentifier(studyId.getIdentifier(), "AAA");
            DynamoExternalIdentifier identifier = mapper.load(keyObject);
            assertNotNull(identifier.getHealthCode());
            
            dao.unassignExternalId(account, "AAA");
            
            keyObject = new DynamoExternalIdentifier(studyId.getIdentifier(), "AAA");
            identifier = mapper.load(keyObject);
            assertNull(identifier.getHealthCode());
        } finally {
            dao.deleteExternalId(extId);
        }
    }
    
    @Test
    public void unassignFailsQuietly() {
        Account account = createAccount("healthCode");
        dao.unassignExternalId(account, "AAA"); // never assigned
        dao.unassignExternalId(account, "DDD"); // doesn't exist
    }
    
    @Test
    public void canGetIds() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 10, null, null);
        
        assertEquals(3, page.getItems().size());
        assertEquals(10, page.getRequestParams().get("pageSize"));
        assertNull(page.getNextPageOffsetKey());
    }
    
    @Test
    public void canFilterIds() {
        List<String> moreIds = Lists.newArrayList("aaa", "bbb", "ccc", "DDD", "AEE", "AFF");
        try {
            for (String id : moreIds) {
                dao.createExternalId(ExternalIdentifier.create(studyId, id));    
            }
            
            // AAA, AEE, AFF
            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 10, "A", null);
            assertEquals(3, page.getItems().size());
            assertEquals(10, page.getRequestParams().get("pageSize"));
            assertEquals("A", page.getRequestParams().get("idFilter"));
            assertNull(page.getNextPageOffsetKey());

            // Nothing matches this filter
            page = dao.getExternalIds(studyId, null, 10, "ZZZ", null);
            assertEquals(0, page.getItems().size());
            assertEquals(10, page.getRequestParams().get("pageSize"));
            assertEquals("ZZZ", page.getRequestParams().get("idFilter"));
            assertNull(page.getNextPageOffsetKey());
            
            Account account1 = createAccount("healthCode1");
            Account account2 = createAccount("healthCode2");
            ExternalIdentifier id1 = setupExternalIdentifier(account1, "AAA");
            dao.commitAssignExternalId(id1);
            ExternalIdentifier id2 = setupExternalIdentifier(account2, "BBB");
            dao.commitAssignExternalId(id2);

            page = dao.getExternalIds(studyId, null, 10, null, Boolean.TRUE);
            assertEquals(2, page.getItems().size());
            assertEquals(toSet(true, "AAA", "BBB"), Sets.newHashSet(page.getItems()));
        } finally {
            for (String id : moreIds) {
                try {
                    dao.deleteExternalId(ExternalIdentifier.create(studyId, id));
                } catch (Exception ex) {
                    // suppress cleanup exception
                }
            }
        }
    }
    
    @Test
    public void canRetrieveCurrentAndNextPage() {
        // Add more external IDs.
        List<String> moreIds = ImmutableList.of("DDD", "EEE", "FFF", "GGG", "HHH", "III", "JJJ", "KKK", "LLL", "MMM",
                "NNN", "OOO", "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW", "XXX", "YYY", "ZZZ");
        try {
            for (String id : moreIds) {
                dao.createExternalId(ExternalIdentifier.create(studyId, id));
            }

            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 5, null, null);
            assertEquals(5, page.getItems().size());
            assertEquals("EEE", page.getNextPageOffsetKey());
            assertNull(page.getRequestParams().get("offsetKey"));
            assertEquals(toSet(false, "AAA","BBB","CCC","DDD","EEE"), Sets.newHashSet(page.getItems()));
            
            page = dao.getExternalIds(studyId, page.getNextPageOffsetKey(), 5, null, null);
            assertEquals(toSet(false, "FFF","GGG","HHH","III","JJJ"), Sets.newHashSet(page.getItems()));
            assertEquals("JJJ", page.getNextPageOffsetKey());
            assertEquals("EEE", page.getRequestParams().get("offsetKey"));
            
            page = dao.getExternalIds(studyId, page.getNextPageOffsetKey(), 5, null, null);
            assertEquals(toSet(false, "KKK","LLL","MMM","NNN","OOO"), Sets.newHashSet(page.getItems()));
            assertEquals("OOO", page.getNextPageOffsetKey());
            assertEquals("JJJ", page.getRequestParams().get("offsetKey"));
            
            page = dao.getExternalIds(studyId, page.getNextPageOffsetKey(), 15, null, null);
            assertEquals(toSet(false, "PPP", "QQQ", "RRR", "SSS", "TTT", "UUU", "VVV", "WWW", "XXX", "YYY", "ZZZ"),
                    Sets.newHashSet(page.getItems()));
            assertNull(page.getNextPageOffsetKey());
            assertEquals("OOO", page.getRequestParams().get("offsetKey"));
        } finally {
            for (String id : moreIds) {
                try {
                    dao.deleteExternalId(ExternalIdentifier.create(studyId, id));
                } catch (Exception ex) {
                    // suppress cleanup exception
                }
            }
        }
    }
    
    @Test
    public void pagingWithFilterResetsInapplicableOffsetKey() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "B", 5, "C", null);
        assertEquals(new ExternalIdentifierInfo("CCC", null, false), page.getItems().get(0));
        assertNull(page.getNextPageOffsetKey());
    }
    
    @Test
    public void pagingWithFilterLongerThanKeyWorks() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "CCCCC", 5, "CC", null);
        assertTrue(page.getItems().isEmpty());
    }
    
    @Test
    public void pagingWithFilterShorterThanKeyWorks() {
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "C", 5, "CC", null);
        assertEquals(new ExternalIdentifierInfo("CCC", null, false), page.getItems().get(0));
        assertNull(page.getNextPageOffsetKey());
    }
    
    // Sometimes paging fails when the total records divided by the page has no remainder. 
    // So last item on last page is the last record. Verify this works.
    @Test
    public void pagingWithNoRemainderWorks() {
        List<String> moreIds = Lists.newArrayList("DDD", "EEE", "FFF");
        try {
            for (String id : moreIds) {
                dao.createExternalId(ExternalIdentifier.create(studyId, id));
            }

            ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 3, null, null);
            assertEquals(3, page.getItems().size());
            assertNotNull(page.getNextPageOffsetKey());
            assertNull(page.getRequestParams().get("offsetKey"));
            
            String nextOffsetKey = page.getNextPageOffsetKey();
            page = dao.getExternalIds(studyId, nextOffsetKey, 3, null, null);
            assertEquals(3, page.getItems().size());
            assertNull(page.getNextPageOffsetKey());
            assertEquals(nextOffsetKey, page.getRequestParams().get("offsetKey"));
        } finally {
            for (String id : moreIds) {
                try {
                    dao.deleteExternalId(ExternalIdentifier.create(studyId, id));        
                } catch(Exception e) {
                }
            }
        }
    }
    
    @Test
    public void retrieveUnassignedExcludesReserved() throws Exception {
        Account account = createAccount("healthCode1");
        
        ExternalIdentifier externalId1 = setupExternalIdentifier(account, "AAA");
        dao.commitAssignExternalId(externalId1);
        
        externalId1 = setupExternalIdentifier(account, "BBB");
        dao.commitAssignExternalId(externalId1);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, null, 5, null, Boolean.FALSE);
        assertEquals(1, page.getItems().size());
        assertEquals(new ExternalIdentifierInfo("CCC", null, false), page.getItems().get(0));

        page = dao.getExternalIds(studyId, null, 5, null, Boolean.TRUE);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("AAA", null, true)));
        assertTrue(page.getItems().contains(new ExternalIdentifierInfo("BBB", null, true)));
    }
    
    @Test
    public void getNextAvailableID() {
        Account account = createAccount("healthCode");
        // We should skip over reserved and assigned IDs to find a free one
        ExternalIdentifier externalId1 = setupExternalIdentifier(account, "AAA");
        ExternalIdentifier externalId2 = setupExternalIdentifier(account, "BBB");
        dao.commitAssignExternalId(externalId1);
        dao.commitAssignExternalId(externalId2);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> ids = dao.getExternalIds(studyId, null, 1, null, Boolean.FALSE);
        
        assertEquals(1, ids.getItems().size());
        assertEquals("CCC", ids.getItems().get(0).getIdentifier());
    }
    
    @Test
    public void hasCorrectRequestParams() {
        // StudyIdentifier studyId,String offsetKey, int pageSize, String idFilter, Boolean assignmentFilter
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = dao.getExternalIds(studyId, "offsetKeyValue", 1,
                "offsetKey", Boolean.FALSE);
        
        assertEquals("offsetKeyValue", page.getRequestParams().get("offsetKey"));
        assertEquals((Integer)1, (Integer)page.getRequestParams().get("pageSize"));
        assertEquals("offsetKey", page.getRequestParams().get("idFilter"));
        assertEquals(Boolean.FALSE, page.getRequestParams().get("assignmentFilter"));
    }

    private Set<ExternalIdentifierInfo> toSet(boolean isAssigned, String... infos) {
        Set<ExternalIdentifierInfo> set = Sets.newHashSetWithExpectedSize(infos.length);
        for (String identifier : infos) {
            set.add(new ExternalIdentifierInfo(identifier, null, isAssigned));
        }
        return set; 
    }
    
    // Pretty similar to ExternalIdService.beginAssignExternalId()
    private ExternalIdentifier setupExternalIdentifier(Account account, String externalId) {
        StudyIdentifier studyId = new StudyIdentifierImpl(account.getStudyId());
        ExternalIdentifier identifier = dao.getExternalId(studyId, externalId);
        if (identifier == null) {
            return null;
        }        
        //ExternalIdentifier identifier = ExternalIdentifier.create(TestConstants.TEST_STUDY, externalId);
        identifier.setHealthCode(account.getHealthCode());
        account.setExternalId(identifier.getIdentifier());
        if (identifier.getSubstudyId() != null) {
            AccountSubstudy acctSubstudy = AccountSubstudy.create(account.getStudyId(),
                    identifier.getSubstudyId(), account.getId());
            acctSubstudy.setExternalId(identifier.getIdentifier());
            if (!account.getAccountSubstudies().contains(acctSubstudy)) {
                account.getAccountSubstudies().add(acctSubstudy);    
            }
        }
        return identifier;
    }
}
