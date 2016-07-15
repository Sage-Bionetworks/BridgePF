package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.dao.SubpopulationDao;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class SubpopulationServiceTest {

    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("AAA");
    private static final long CONSENT_CREATED_ON = DateTime.now().getMillis();
    
    SubpopulationService service;
    
    @Mock
    SubpopulationDao subpopDao;
    
    @Mock
    Study study;
    
    @Mock
    StudyConsentDao studyConsentDao;
    
    @Mock
    StudyConsentService studyConsentService;
    
    @Mock
    StudyConsentView view; 
    
    @Mock
    StudyConsentForm form;
    
    @Mock
    StudyConsent consent;
    
    Subpopulation subpop;
    
    @Before
    public void before() throws Exception {
        service = new SubpopulationService();
        service.setSubpopulationDao(subpopDao);
        service.setStudyConsentService(studyConsentService);
        service.setStudyConsentDao(studyConsentDao);
        service.setDefaultConsentForm(form);
        
        subpop = Subpopulation.create();
        subpop.setGuidString(BridgeUtils.generateGuid());
        
        Set<String> dataGroups = Sets.newHashSet("group1","group2");
        when(study.getDataGroups()).thenReturn(dataGroups);
        when(study.getIdentifier()).thenReturn(TEST_STUDY_IDENTIFIER);
        
        when(subpopDao.createSubpopulation(any())).thenAnswer(returnsFirstArg());
        when(subpopDao.updateSubpopulation(any())).thenAnswer(returnsFirstArg());
        
        when(view.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        when(consent.getCreatedOn()).thenReturn(CONSENT_CREATED_ON);
        
        when(studyConsentService.addConsent(any(), any())).thenReturn(view);
        when(studyConsentService.publishConsent(any(), any(), eq(CONSENT_CREATED_ON))).thenReturn(view);
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expected = InvalidEntityException.class)
    public void creationIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    // The contents of this exception are tested in the validator tests.
    @Test(expected = InvalidEntityException.class)
    public void updateIsValidated() {
        Subpopulation subpop = Subpopulation.create();
        service.createSubpopulation(study, subpop);
    }
    
    @Test
    public void createSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuidString("cannot-set-guid");
        subpop.setDefaultGroup(false);
        
        Subpopulation result = service.createSubpopulation(study, subpop);
        assertEquals("Name", result.getName());
        assertNotNull(result.getGuidString());
        assertNotEquals("cannot-set-guid", result.getGuidString());
        assertFalse(result.isDeleted());
        assertEquals(TEST_STUDY_IDENTIFIER, result.getStudyIdentifier());
        
        verify(subpopDao).createSubpopulation(subpop);
        verify(studyConsentService).addConsent(eq(result.getGuid()), any());
        verify(studyConsentService).publishConsent(study, result, CONSENT_CREATED_ON);
    }
    
    @Test
    public void createDefaultSubpopulationWhereNoConsents() {
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        
        StudyConsentView view = new StudyConsentView(new DynamoStudyConsent1(), "");
        Subpopulation subpop = Subpopulation.create();
        SubpopulationGuid defaultGuid = SubpopulationGuid.create("test-study");
        subpop.setGuid(defaultGuid);
        
        ArgumentCaptor<StudyConsentForm> captor = ArgumentCaptor.forClass(StudyConsentForm.class);
        
        when(studyConsentService.getAllConsents(defaultGuid)).thenReturn(Lists.newArrayList());
        when(studyConsentService.addConsent(eq(defaultGuid), any())).thenReturn(view);
        when(subpopDao.createDefaultSubpopulation(study.getStudyIdentifier())).thenReturn(subpop);
        
        // No consents, so we add and publish one.
        Subpopulation returnValue = service.createDefaultSubpopulation(study);
        verify(studyConsentService).addConsent(any(), captor.capture());
        verify(studyConsentService).publishConsent(eq(study), eq(subpop), any(Long.class));
        assertEquals(subpop, returnValue);
        
        // This used the default document.
        assertEquals(form, captor.getValue());
    }
    
    @Test
    public void createDefaultSubpopulationWhereConsentsExist() {
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        
        SubpopulationGuid defaultGuid = SubpopulationGuid.create("test-study");
        Subpopulation subpop = Subpopulation.create();
        when(studyConsentService.getAllConsents(defaultGuid)).thenReturn(Lists.newArrayList(new DynamoStudyConsent1()));
        when(subpopDao.createDefaultSubpopulation(study.getStudyIdentifier())).thenReturn(subpop);
        
        Subpopulation returnValue = service.createDefaultSubpopulation(study);
        assertEquals(subpop, returnValue);
        
        // Consents exist... don't add any
        verify(studyConsentService, never()).addConsent(any(), any());
    }
    
    @Test
    public void updateSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setStudyIdentifier("junk-you-cannot-set");
        subpop.setGuidString("guid");
        subpop.setDefaultGroup(false);
        subpop.setDeleted(true);

        doReturn(consent).when(studyConsentDao).getConsent(any(), anyLong());
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(Subpopulation.create());
        
        Subpopulation result = service.updateSubpopulation(study, subpop);
        assertEquals("Name", result.getName());
        assertEquals("guid", result.getGuidString());
        assertEquals(TEST_STUDY_IDENTIFIER, result.getStudyIdentifier());
        
        verify(subpopDao).updateSubpopulation(subpop);
    }
    
    @Test
    public void updateSubpopulationVerifiesStudyConsent() {
        when(studyConsentService.getConsent(anyObject(), anyLong())).thenThrow(new EntityNotFoundException(StudyConsent.class));
        
        // doesn't even get to validation, so no need to fill this out.
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString("test-guid");
        subpop.setPublishedConsentCreatedOn(DateTime.now().getMillis());
        
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(Subpopulation.create());
        
        try {
            service.updateSubpopulation(study, subpop);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            assertEquals("StudyConsent not found.", e.getMessage());
        }
    }
    
    @Test
    public void updateSubpopulationSetsConsentCreatedOn() {
        doReturn(1000L).when(view).getCreatedOn();
        when(studyConsentDao.getConsent(anyObject(), anyLong())).thenReturn(consent);
        
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setGuidString("test-guid");
        
        Subpopulation existing = Subpopulation.create();
        existing.setPublishedConsentCreatedOn(1000L);
        when(subpopDao.getSubpopulation(any(), any())).thenReturn(existing);
        
        Subpopulation updated = service.updateSubpopulation(study, subpop);
        assertEquals(1000L, updated.getPublishedConsentCreatedOn());
    }
    
    @Test
    public void getSubpopulations() {
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setName("Name 1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setName("Name 2");

        List<Subpopulation> list = Lists.newArrayList(subpop1, subpop2); 
        when(subpopDao.getSubpopulations(TEST_STUDY, true, false)).thenReturn(list);
        
        List<Subpopulation> results = service.getSubpopulations(TEST_STUDY);
        assertEquals(2, results.size());
        assertEquals(subpop1, results.get(0));
        assertEquals(subpop2, results.get(1));
        verify(subpopDao).getSubpopulations(TEST_STUDY, true, false);
    }
    @Test
    public void getSubpopulation() {
        Subpopulation subpop = Subpopulation.create();
        when(subpopDao.getSubpopulation(TEST_STUDY, SUBPOP_GUID)).thenReturn(subpop);

        Subpopulation result = service.getSubpopulation(TEST_STUDY, SUBPOP_GUID);
        assertEquals(subpop, result);
        verify(subpopDao).getSubpopulation(TEST_STUDY, SUBPOP_GUID);
    }
    @Test
    public void getSubpopulationForUser() {
        List<Subpopulation> subpops = ImmutableList.of(Subpopulation.create());
        // We test the matching logic in CriteriaUtilsTest as well as in the DAO. Here we just want
        // to verify it is being carried through.
        CriteriaContext context = new CriteriaContext.Builder()
                .withStudyIdentifier(new StudyIdentifierImpl("test-key"))
                .withClientInfo(ClientInfo.fromUserAgentCache("app/4")).build();
        
        when(subpopDao.getSubpopulationsForUser(context)).thenReturn(subpops);
        
        List<Subpopulation> results = service.getSubpopulationForUser(context);
        
        assertEquals(subpops, results);
        verify(subpopDao).getSubpopulationsForUser(context);
    }
    
    @Test
    public void deleteSubpopulation() {
        service.deleteSubpopulation(TEST_STUDY, SUBPOP_GUID, true);
        
        verify(subpopDao).deleteSubpopulation(TEST_STUDY, SUBPOP_GUID, true);
    }
    
}
