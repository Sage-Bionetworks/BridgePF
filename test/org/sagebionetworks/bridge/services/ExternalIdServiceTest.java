package org.sagebionetworks.bridge.services;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdServiceTest {

    private static final String EXT_ID = "AAA";
    private static final List<String> EXT_IDS = Lists.newArrayList("AAA","BBB","CCC");
    private static final String HEALTH_CODE = "healthCode";
    private static final Study STUDY = new DynamoStudy();
    static {
        STUDY.setIdentifier("test-study");
    }

    @Mock
    private ExternalIdDao externalIdDao;
    
    @Mock
    private AccountDao accountDao;
    
    private ExternalIdService externalIdService;
    
    @Before
    public void before() {
        Config config = mock(Config.class);
        when(config.getInt(ExternalIdDao.CONFIG_KEY_ADD_LIMIT)).thenReturn(10);
        
        externalIdService = new ExternalIdService();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setAccountDao(accountDao);
        externalIdService.setConfig(config);
    }
    
    @Test
    public void getExternalIds() {
        externalIdService.getExternalIds(STUDY, "offset", 10, "AAA", Boolean.FALSE);
        
        verify(externalIdDao).getExternalIds(STUDY.getStudyIdentifier(), "offset", 10, "AAA", Boolean.FALSE);
    }
    
    @Test
    public void getExternalIdsWithOptionalArguments() {
        externalIdService.getExternalIds(STUDY, null, null, null, null);
        
        verify(externalIdDao).getExternalIds(STUDY.getStudyIdentifier(), null, BridgeConstants.API_DEFAULT_PAGE_SIZE, null, null);
    }
    
    @Test
    public void addExternalIds() {
        externalIdService.addExternalIds(STUDY, EXT_IDS);
        
        verify(externalIdDao).addExternalIds(STUDY.getStudyIdentifier(), EXT_IDS);
    }
    
    @Test
    public void assignExternalIdDoesOK() {
        STUDY.setExternalIdValidationEnabled(true);
        
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao).assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
    }
    
    @Test
    public void unassignExternalId() {
        externalIdService.unassignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao).unassignExternalId(STUDY.getStudyIdentifier(), EXT_ID);
    }

    @Test
    public void deleteExternalIdsWithValidationDisabled() {
        STUDY.setExternalIdValidationEnabled(false);
        // Note that the ExternalId record may even be assigned, but we do not care
        when(accountDao.getAccount(any())).thenReturn(Account.create());
        
        externalIdService.deleteExternalIds(STUDY, EXT_IDS);
        
        verify(externalIdDao).deleteExternalIds(STUDY.getStudyIdentifier(), EXT_IDS);
    }
    
    @Test
    public void deleteExternalIdsWithValidationEnabled() {
        STUDY.setExternalIdValidationEnabled(true);
        when(accountDao.getAccount(any())).thenReturn(Account.create());
        try {
            externalIdService.deleteExternalIds(STUDY, EXT_IDS);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
        }
        verifyNoMoreInteractions(externalIdDao);
    }
    
    @Test
    public void deleteExternalIdsWithValidationEnabledButNotAssignedWorks() {
        STUDY.setExternalIdValidationEnabled(true);
        
        externalIdService.deleteExternalIds(STUDY, EXT_IDS);
        
        verify(externalIdDao).deleteExternalIds(STUDY.getStudyIdentifier(), EXT_IDS);
    }
}
