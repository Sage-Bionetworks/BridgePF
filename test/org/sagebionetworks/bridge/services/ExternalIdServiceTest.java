package org.sagebionetworks.bridge.services;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.dao.ParticipantOption.EXTERNAL_IDENTIFIER;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.config.Config;
import org.sagebionetworks.bridge.dao.ExternalIdDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
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
    private ParticipantOptionsService optionsService;
    
    private ExternalIdService externalIdService;
    
    @Before
    public void before() {
        Config config = mock(Config.class);
        when(config.getInt(ExternalIdDao.CONFIG_KEY_ADD_LIMIT)).thenReturn(10);
        
        externalIdService = new ExternalIdService();
        externalIdService.setExternalIdDao(externalIdDao);
        externalIdService.setParticipantOptionsService(optionsService);
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
    public void reserveExternalIdWithVerification() {
        STUDY.setExternalIdValidationEnabled(true);
        externalIdService.reserveExternalId(STUDY, EXT_ID);
        
        verify(externalIdDao).reserveExternalId(STUDY.getStudyIdentifier(), EXT_ID);
        verifyNoMoreInteractions(optionsService);
    }
    
    @Test
    public void reserveExternalIdWithoutVerification() {
        STUDY.setExternalIdValidationEnabled(false);
        externalIdService.reserveExternalId(STUDY, EXT_ID);
        
        verify(externalIdDao, never()).reserveExternalId(STUDY.getStudyIdentifier(), EXT_ID);
        verifyNoMoreInteractions(optionsService);
    }
    
    @Test
    public void assignExternalIdWithVerification() {
        STUDY.setExternalIdValidationEnabled(true);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao).assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, EXT_ID);
    }
    
    @Test
    public void assignExternalIdWithoutVerification() {
        STUDY.setExternalIdValidationEnabled(false);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao, never()).assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, EXT_ID);
    }
    
    @Test
    public void assignExternalIdFailsVerification() {
        STUDY.setExternalIdValidationEnabled(true);
        doThrow(new EntityNotFoundException(ExternalIdentifier.class)).when(externalIdDao)
                .assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
        
        try {
            externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(externalIdDao).assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
        verify(optionsService, never()).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, EXT_ID);
    }
    
    @Test
    public void unassignExternalId() {
        externalIdService.unassignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao).unassignExternalId(STUDY.getStudyIdentifier(), EXT_ID);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, null);
    }

    @Test
    public void deleteExternalIdsWithValidationDisabled() {
        STUDY.setExternalIdValidationEnabled(false);
        externalIdService.deleteExternalIds(STUDY, EXT_IDS);
        
        verify(externalIdDao).deleteExternalIds(STUDY.getStudyIdentifier(), EXT_IDS);
    }
    
    @Test
    public void deleteExternalIdsWithValidationEnabled() {
        STUDY.setExternalIdValidationEnabled(true);
        try {
            externalIdService.deleteExternalIds(STUDY, EXT_IDS);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
        }
        verifyNoMoreInteractions(externalIdDao);
    }
}
