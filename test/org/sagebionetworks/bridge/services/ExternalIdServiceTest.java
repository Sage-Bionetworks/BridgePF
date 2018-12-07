package org.sagebionetworks.bridge.services;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.Substudy;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdServiceTest {

    private static final String EXT_ID = "AAA";
    private static final String HEALTH_CODE = "healthCode";
    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
    }

    @Mock
    private ExternalIdDao externalIdDao;
    
    @Mock
    private SubstudyService substudyService;
    
    private ExternalIdServiceV4 externalIdService;
    
    @Before
    public void before() {
        BridgeUtils.setRequestContext(new RequestContext.Builder()
                .withCallerStudyId(STUDY.getStudyIdentifier()).build());
        Config config = mock(Config.class);
        when(config.getInt(ExternalIdDao.CONFIG_KEY_ADD_LIMIT)).thenReturn(10);
        
        externalIdService = new ExternalIdServiceV4();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setSubstudyService(substudyService);
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getExternalIds() {
        externalIdService.getExternalIds("offset", 10, "AAA", Boolean.FALSE);
        
        verify(externalIdDao).getExternalIds(STUDY.getStudyIdentifier(), "offset", 10, "AAA", Boolean.FALSE);
    }
    
    @Test
    public void getExternalIdsWithOptionalArguments() {
        externalIdService.getExternalIds(null, null, null, null);
        
        verify(externalIdDao).getExternalIds(STUDY.getStudyIdentifier(), null, BridgeConstants.API_DEFAULT_PAGE_SIZE, null, null);
    }
    
    @Test
    public void createExternalIdentifier() {
        when(substudyService.getSubstudy(any(), eq("substudyId"), eq(false))).thenReturn(Substudy.create());
        ExternalIdentifier externalId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXT_ID);
        externalId.setSubstudyId("substudyId");
        externalIdService.createExternalIdentifier(externalId);
        
        verify(externalIdDao).createExternalIdentifier(externalId);
    }
    
    @Test
    public void cannotAddExistingIdentifier() {
        when(substudyService.getSubstudy(any(), eq("substudyId"), eq(false))).thenReturn(Substudy.create());
        ExternalIdentifier externalId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXT_ID);
        externalId.setSubstudyId("substudyId");
        
        ExternalIdentifier persistedExternalId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXT_ID);
        persistedExternalId.setHealthCode("healthCode");
        
        when(externalIdDao.getExternalId(STUDY.getStudyIdentifier(), EXT_ID)).thenReturn(persistedExternalId);
        
        try {
            externalIdService.createExternalIdentifier(externalId);
            fail("Should have thrown exception");
        } catch(EntityAlreadyExistsException e) {
        }
        verify(externalIdDao, never()).createExternalIdentifier(any());
    }
    
    @Test
    public void assignExternalIdDoesOK() {
        STUDY.setExternalIdValidationEnabled(true);
        
        Account account = Account.create();
        account.setStudyId(STUDY.getIdentifier());
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.assignExternalId(account, EXT_ID);
        
        verify(externalIdDao).assignExternalId(account, EXT_ID);
    }
    
    @Test
    public void unassignExternalId() {
        Account account = Account.create();
        account.setStudyId(STUDY.getIdentifier());
        account.setHealthCode(HEALTH_CODE);
        
        externalIdService.unassignExternalId(account, EXT_ID);
        
        verify(externalIdDao).unassignExternalId(account, EXT_ID);
    }

    @Test
    public void deleteExternalIdWithValidationDisabled() {
        STUDY.setExternalIdValidationEnabled(false);
        ExternalIdentifier externalId = ExternalIdentifier.create(STUDY.getStudyIdentifier(), EXT_ID);
        
        when(externalIdDao.getExternalId(STUDY.getStudyIdentifier(), EXT_ID)).thenReturn(externalId);
        
        externalIdService.deleteExternalId(STUDY, EXT_ID);
        
        verify(externalIdDao).deleteExternalIdentifier(externalId);
    }
    
    @Test
    public void deleteExternalIdsWithValidationEnabled() {
        STUDY.setExternalIdValidationEnabled(true);
        try {
            externalIdService.deleteExternalId(STUDY, EXT_ID);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
        }
        verifyNoMoreInteractions(externalIdDao);
    }
}
