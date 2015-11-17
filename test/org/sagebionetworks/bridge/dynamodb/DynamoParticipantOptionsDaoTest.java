package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.dao.ParticipantOption.*;

import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;

import com.google.common.collect.Sets;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoParticipantOptionsDaoTest {

    private Study study;
    private String healthCode;
    
    @Resource
    DynamoParticipantOptionsDao optionsDao;
    
    @Resource
    StudyServiceImpl studyService;

    @BeforeClass
    public static void beforeClass() {
        DynamoInitializer.init(DynamoParticipantOptions.class);
    }
    
    @Before
    public void before() {
        study = studyService.getStudy(TEST_STUDY_IDENTIFIER);
        healthCode = BridgeUtils.generateGuid();
    }
    
    @After
    public void teardown() {
        optionsDao.deleteAllParticipantOptions(healthCode);
    }
    
    @Test
    public void crudOptions() {
        Set<String> dataGroups = Sets.newHashSet("group1", "group2", "group3");
        String sharingName = SharingScope.ALL_QUALIFIED_RESEARCHERS.name();
        
        // Set three options for an individual
        optionsDao.setOption(study, healthCode, SHARING_SCOPE, sharingName);
        optionsDao.setOption(study, healthCode, EXTERNAL_IDENTIFIER, "AAA");
        optionsDao.setOption(study,  healthCode, DATA_GROUPS, BridgeUtils.setToCommaList(dataGroups));

        // Get them all for one individual, they are set
        Map<ParticipantOption,String> values = optionsDao.getAllParticipantOptions(healthCode);
        assertEquals(sharingName, values.get(SHARING_SCOPE));
        assertEquals("AAA", values.get(EXTERNAL_IDENTIFIER));
        assertEquals(BridgeUtils.setToCommaList(dataGroups),  values.get(DATA_GROUPS));
        
        // Get an individual option for all users (try all three)
        OptionLookup sharingLookup = optionsDao.getOptionForAllStudyParticipants(study, SHARING_SCOPE);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, sharingLookup.getSharingScope(healthCode));
        
        OptionLookup externalIdLookup = optionsDao.getOptionForAllStudyParticipants(study, EXTERNAL_IDENTIFIER);
        assertEquals("AAA", externalIdLookup.get(healthCode));
        
        OptionLookup dataGroupsLookup = optionsDao.getOptionForAllStudyParticipants(study, DATA_GROUPS);
        assertEquals(dataGroups, dataGroupsLookup.getDataGroups(healthCode));
        
        // Now delete them all, there should still be defaults
        optionsDao.deleteAllParticipantOptions(healthCode);

        sharingLookup = optionsDao.getOptionForAllStudyParticipants(study, SHARING_SCOPE);
        assertEquals(SharingScope.NO_SHARING, sharingLookup.getSharingScope(healthCode));
        
        externalIdLookup = optionsDao.getOptionForAllStudyParticipants(study, EXTERNAL_IDENTIFIER);
        assertNull(externalIdLookup.get(healthCode));
        
        dataGroupsLookup = optionsDao.getOptionForAllStudyParticipants(study, DATA_GROUPS);
        assertNull(dataGroupsLookup.getDataGroups(healthCode));
    }
    
    @Test
    public void getAllParticipantOptions() {
        optionsDao.setOption(study, healthCode, SHARING_SCOPE, SharingScope.ALL_QUALIFIED_RESEARCHERS.name());
        optionsDao.setOption(study, healthCode, EXTERNAL_IDENTIFIER, "AAA");
        
        Map<ParticipantOption,String> values = optionsDao.getAllParticipantOptions(healthCode);
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), values.get(SHARING_SCOPE));
        assertEquals("AAA", values.get(EXTERNAL_IDENTIFIER));
        assertEquals("true", values.get(EMAIL_NOTIFICATIONS)); // Defaults are included
        assertNull(values.get(DATA_GROUPS));
    }
    
    @Test
    public void getOptionForAllStudyParticipants() {
        optionsDao.setOption(study, healthCode, EXTERNAL_IDENTIFIER, "AAA");
        optionsDao.setOption(study, healthCode+"2", EXTERNAL_IDENTIFIER, "BBB");
        optionsDao.setOption(study, healthCode+"3", EXTERNAL_IDENTIFIER, "CCC");

        OptionLookup lookup = optionsDao.getOptionForAllStudyParticipants(study, EXTERNAL_IDENTIFIER);
        
        assertEquals("AAA", lookup.get(healthCode));
        assertEquals("BBB", lookup.get(healthCode+"2"));
        assertEquals("CCC", lookup.get(healthCode+"3"));
        
        // healthCode's options are deleted in the @After method
        optionsDao.deleteAllParticipantOptions(healthCode+"2");
        optionsDao.deleteAllParticipantOptions(healthCode+"3");
    }
    
}
