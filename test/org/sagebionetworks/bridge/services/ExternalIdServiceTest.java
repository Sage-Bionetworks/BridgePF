package org.sagebionetworks.bridge.services;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
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
import org.sagebionetworks.bridge.models.accounts.ParticipantOptionsLookup;
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
    
    @Mock
    private ParticipantOptionsLookup lookup;
    
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
    public void assignExternalIdWithVerification() {
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        
        STUDY.setExternalIdValidationEnabled(true);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        verify(externalIdDao).assignExternalId(STUDY.getStudyIdentifier(), EXT_ID, HEALTH_CODE);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, EXT_ID);
    }
    
    @Test
    public void assignExternalIdWithoutVerification() {
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);

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
        doReturn(lookup).when(optionsService).getOptions(HEALTH_CODE);
        
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
    
    @Test
    public void createExternalIdValidatedWithValue() {
        setupExternalIdTest(true, null);
        
        externalIdService.reserveExternalId(STUDY, EXT_ID, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        // Adding validated ID, reserve it
        verifySetAsReservation(EXT_ID);
    }

    @Test
    public void createExternalIdNotValidatedWithValue() {
        setupExternalIdTest(false, null);
        
        externalIdService.reserveExternalId(STUDY, EXT_ID, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        // Adding unvalidated ID, set as an option
        verifySetAsOption(EXT_ID);
    }

    @Test
    public void createExternalIdValidatedNoValue() {
        setupExternalIdTest(true, null);
        
        externalIdService.reserveExternalId(STUDY, null, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, null, HEALTH_CODE);
        
        // Validated but no value supplied, just set null option
        verifySetAsOption(null);
    }

    @Test
    public void createExternalIdNotValidatedNoValue() {
        setupExternalIdTest(false, null);
        
        externalIdService.reserveExternalId(STUDY, null, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, null, HEALTH_CODE);
        
        // Not validated, set as a null option
        verifySetAsOption(null);
    }
    
    @Test
    public void updateExternalIdValidatedWithSameValue() {
        setupExternalIdTest(true, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, EXT_ID, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        // Submitting same value again with validation just resets option (noop)
        verifySetAsOption(EXT_ID);
    }

    @Test
    public void updateExternalIdNotValidatedWithSameValue() {
        setupExternalIdTest(false, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, EXT_ID, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, EXT_ID, HEALTH_CODE);
        
        // Submitting same value again without validation saves same option value (noop)
        verifySetAsOption(EXT_ID);
    }

    @Test(expected = BadRequestException.class)
    public void updateExternalIdValidatedWithChangedValue() {
        setupExternalIdTest(true, EXT_ID);
        
        // Updating a validated ID throws an exception
        externalIdService.reserveExternalId(STUDY, "newExternalId", HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, "newExternalId", HEALTH_CODE);
    }

    @Test
    public void updateExternalIdNotValidatedWithChangedValue() {
        setupExternalIdTest(false, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, "newExternalId", HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, "newExternalId", HEALTH_CODE);

        // Updating a non-validated ID sets it as the new option
        verifySetAsOption("newExternalId");
    }

    @Test(expected = BadRequestException.class)
    public void updateExternalIdValidatedNoValue() {
        setupExternalIdTest(true, EXT_ID);
        
        // Nulling a validated value throws an exception
        externalIdService.reserveExternalId(STUDY, null, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, null, HEALTH_CODE);
    }

    @Test
    public void updateExternalIdNotValidatedNoValue() {
        setupExternalIdTest(false, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, null, HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, null, HEALTH_CODE);
        
        // Nulling a non-validated value sets the option to null
        verifySetAsOption(null);
    }
    
    @Test(expected = BadRequestException.class)
    public void updateExternalIdValidatedNewValue() {
        setupExternalIdTest(true, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, "newExternalId", HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, "newExternalId", HEALTH_CODE);
        
        // Updating validated value with new value throws exception
        verifySetAsReservation("newExternalId");
    }

    @Test
    public void updateExternalIdNotValidatedNewValue() {
        setupExternalIdTest(false, EXT_ID);
        
        externalIdService.reserveExternalId(STUDY, "newExternalId", HEALTH_CODE);
        externalIdService.assignExternalId(STUDY, "newExternalId", HEALTH_CODE);
        
        // Updating unvalidated value with new value changes the option
        verifySetAsOption("newExternalId");
    }

    private void setupExternalIdTest(boolean withValidation, String existingValue) {
        STUDY.setExternalIdValidationEnabled(withValidation);
        when(optionsService.getOptions(HEALTH_CODE)).thenReturn(lookup);
        when(lookup.getString(EXTERNAL_IDENTIFIER)).thenReturn(existingValue);
    }
    
    private void verifySetAsOption(String externalId) {
        verify(externalIdDao, never()).reserveExternalId(STUDY.getStudyIdentifier(), externalId);
        verify(externalIdDao, never()).assignExternalId(STUDY.getStudyIdentifier(), externalId, HEALTH_CODE);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, externalId);
    }
    
    private void verifySetAsReservation(String externalId) {
        verify(externalIdDao).reserveExternalId(STUDY.getStudyIdentifier(), externalId);
        verify(externalIdDao).assignExternalId(STUDY.getStudyIdentifier(), externalId, HEALTH_CODE);
        verify(optionsService).setString(STUDY.getStudyIdentifier(), HEALTH_CODE, EXTERNAL_IDENTIFIER, externalId);
    }
}
