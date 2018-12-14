package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.models.substudies.Substudy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdServiceTest {

    private static final String ID = "AAA";
    private static final String USER_ID = "userId";
    private static final String SUBSTUDY_ID = "substudyId";
    private static final Set<String> SUBSTUDIES = ImmutableSet.of(SUBSTUDY_ID);
    private static final String HEALTH_CODE = "healthCode";
    private static final Study STUDY = new DynamoStudy();
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
    
    @Mock
    private ExternalIdDao externalIdDao;
    
    @Mock
    private SubstudyService substudyService;
    
    private ExternalIdService externalIdService;

    @Before
    public void before() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY).build());
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        externalIdService = new ExternalIdService();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setSubstudyService(substudyService);
    }
    
    @After
    public void after() {
        STUDY.setExternalIdValidationEnabled(false);
        EXT_ID.setSubstudyId(null);
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getExternalId() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        ExternalIdentifier retrieved = externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, true);
        assertEquals(EXT_ID, retrieved);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getExternalIdNoExtIdThrows() {
        externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, true);
    }

    @Test
    public void getExternalIdNoExtIdReturnsNull() {
        assertNull(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, false));
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getExternalIdExtIdOutsideSubstudiesThrows() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());
        EXT_ID.setSubstudyId("someOtherSubstudy");
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, true);   
    }
    
    @Test
    public void getExternalIdExtIdOutsideSubstudiesReturnsNull() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());
        EXT_ID.setSubstudyId("someOtherSubstudy");
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        assertNull(externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, false));   
    }
    
    @Test
    public void getExternalIdSubstudiesMatch() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());
        EXT_ID.setSubstudyId(SUBSTUDY_ID);
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        ExternalIdentifier retrieved = externalIdService.getExternalId(TestConstants.TEST_STUDY, ID, true);
        assertEquals(EXT_ID, retrieved);   
    }
    
    @Test
    public void getExternalIds() {
        externalIdService.getExternalIds("offsetKey", 10, "idFilter", true);
        
        verify(externalIdDao).getExternalIds(TestConstants.TEST_STUDY, "offsetKey", 10, "idFilter", true);
    }
    
    @Test
    public void getExternalIdsDefaultsPageSize() {
        externalIdService.getExternalIds(null, null, null, null);
        
        verify(externalIdDao).getExternalIds(TestConstants.TEST_STUDY, null, BridgeConstants.API_DEFAULT_PAGE_SIZE,
                null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getExternalIdsUnderMinPageSizeThrows() {
        externalIdService.getExternalIds(null, 0, null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void getExternalIdsOverMaxPageSizeThrows() {
        externalIdService.getExternalIds(null, 10000, null, null);
    }
    
    @Test
    public void createExternalId() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        
        EXT_ID.setSubstudyId(SUBSTUDY_ID);
        externalIdService.createExternalId(EXT_ID, false);
        
        verify(externalIdDao).createExternalId(EXT_ID);
    }
    
    @Test
    public void createExternalIdEnforcesStudyId() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        
        EXT_ID.setSubstudyId(SUBSTUDY_ID);
        EXT_ID.setStudyId("some-dumb-id");
        externalIdService.createExternalId(EXT_ID, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(EXT_ID);        
    }
    
    @Test
    public void createExternalIdSetsSubstudyIdIfMissingAndUnambiguous() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());
        
        externalIdService.createExternalId(EXT_ID, false);
        
        // still matches and verifies
        verify(externalIdDao).createExternalId(EXT_ID);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void createExternalIdDoesNotSetSubstudyIdAmbiguous() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(ImmutableSet.of(SUBSTUDY_ID, "anotherSubstudy")).build());
        
        externalIdService.createExternalId(EXT_ID, false);
    }

    @Test(expected = InvalidEntityException.class)
    public void createExternalIdValidates() {
        externalIdService.createExternalId(ExternalIdentifier.create(new StudyIdentifierImpl("nonsense"), "nonsense"), false);
    }
    
    @Test(expected = EntityAlreadyExistsException.class)
    public void createExternalIdAlreadyExistsThrows() {
        when(substudyService.getSubstudy(TestConstants.TEST_STUDY, SUBSTUDY_ID, false))
            .thenReturn(Substudy.create());
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        EXT_ID.setSubstudyId(SUBSTUDY_ID);
        
        externalIdService.createExternalId(EXT_ID, false);
    }
    
    @Test
    public void deleteExternalIdPermanently() {
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        externalIdService.deleteExternalIdPermanently(STUDY, EXT_ID);
        
        verify(externalIdDao).deleteExternalId(EXT_ID);
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteExternalIdPermanentlyThrowsIfValidationEnabled() {
        STUDY.setExternalIdValidationEnabled(true);
        
        externalIdService.deleteExternalIdPermanently(STUDY, EXT_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyMissingThrows() {
        externalIdService.deleteExternalIdPermanently(STUDY, EXT_ID);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteExternalIdPermanentlyOutsideSubstudiesThrows() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(TestConstants.TEST_STUDY)
                .withCallerSubstudies(SUBSTUDIES).build());        
        EXT_ID.setSubstudyId("someOtherId");
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(EXT_ID);
        
        externalIdService.deleteExternalIdPermanently(STUDY, EXT_ID);
    }
    
    @Test
    public void beginAssignExternalId() {
        Account account = Account.create();
        account.setId(USER_ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(existing);
        
        ExternalIdentifier externalId = externalIdService.beginAssignExternalId(account, ID);
        
        assertEquals(ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(SUBSTUDY_ID, externalId.getSubstudyId());
        
        assertEquals(ID, account.getExternalId());
        assertEquals(1, account.getAccountSubstudies().size());
        
        AccountSubstudy accountSubstudy = Iterables.getFirst(account.getAccountSubstudies(), null);
        assertEquals(USER_ID, accountSubstudy.getAccountId());
        assertEquals(ID, accountSubstudy.getExternalId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountSubstudy.getStudyId());
        assertEquals(SUBSTUDY_ID, accountSubstudy.getSubstudyId());
    }
    
    @Test
    public void beginAssignExternalIdIdentifierMissing() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = externalIdService.beginAssignExternalId(account, null);
        assertNull(externalId);
    }
    
    @Test
    public void beginAssignExternalIdIdentifierObjectMissing() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier externalId = externalIdService.beginAssignExternalId(account, ID);
        assertNull(externalId);
    }
    
    @Test
    public void beginAssignExternalIdHealthCodeExistsEqual() {
        Account account = Account.create();
        account.setId(USER_ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        existing.setSubstudyId(SUBSTUDY_ID);
        existing.setHealthCode(HEALTH_CODE); // despite assignment, we update everything
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(existing);
        
        ExternalIdentifier externalId = externalIdService.beginAssignExternalId(account, ID);
        
        assertEquals(ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(SUBSTUDY_ID, externalId.getSubstudyId());
        
        assertEquals(ID, account.getExternalId());
        assertEquals(1, account.getAccountSubstudies().size());
        
        AccountSubstudy accountSubstudy = Iterables.getFirst(account.getAccountSubstudies(), null);
        assertEquals(USER_ID, accountSubstudy.getAccountId());
        assertEquals(ID, accountSubstudy.getExternalId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountSubstudy.getStudyId());
        assertEquals(SUBSTUDY_ID, accountSubstudy.getSubstudyId());
    }

    @Test(expected = EntityAlreadyExistsException.class)
    public void beginAssignExternalIdHealthCodeExistsNotEqual() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        existing.setHealthCode("anotherHealthCode");
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(existing);
        
        externalIdService.beginAssignExternalId(account, ID);
    }
    
    @Test
    public void beginAssignExternalIdWithoutSubstudy() {
        Account account = Account.create();
        account.setId(USER_ID);
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        ExternalIdentifier existing = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        when(externalIdDao.getExternalId(TestConstants.TEST_STUDY, ID)).thenReturn(existing);
        
        ExternalIdentifier externalId = externalIdService.beginAssignExternalId(account, ID);
        
        assertEquals(ID, externalId.getIdentifier());
        assertEquals(HEALTH_CODE, externalId.getHealthCode());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalId.getStudyId());
        assertEquals(ID, account.getExternalId());
        assertNull(externalId.getSubstudyId());
        assertTrue(account.getAccountSubstudies().isEmpty());
    }
    
    @Test
    public void commitAssignExternalId() {
        ExternalIdentifier externalId = ExternalIdentifier.create(TestConstants.TEST_STUDY, ID);
        
        externalIdService.commitAssignExternalId(externalId);
        
        verify(externalIdDao).commitAssignExternalId(externalId);
    }

    @Test
    public void commitAssignExternalIdNullId() {
        externalIdService.commitAssignExternalId(null);
        
        verify(externalIdDao, never()).commitAssignExternalId(any());
    }

    @Test
    public void unassignExternalId() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, ID);
        
        verify(externalIdDao).unassignExternalId(account, ID);
    }

    @Test
    public void unassignExternalIdNullDoesNothing() {
        Account account = Account.create();
        account.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, null);
        
        verify(externalIdDao, never()).unassignExternalId(account, ID);
    }
}
