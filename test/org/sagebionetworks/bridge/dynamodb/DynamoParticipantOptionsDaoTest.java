package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoParticipantOptionsDaoTest {

    @Resource
    DynamoParticipantOptionsDao optionsDao;
    
    @Resource
    StudyServiceImpl studyService;
    
    @BeforeClass
    public static void initialSetUp() {
        DynamoInitializer.init(DynamoParticipantOptions.class);
        DynamoTestUtil.clearTable(DynamoParticipantOptions.class);
    }
    
    @AfterClass
    public static void teardown() {
        DynamoTestUtil.clearTable(DynamoParticipantOptions.class);
    }
    
    @Test
    @SuppressWarnings("deprecation")
    public void crudOptionForParticipant() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode = "AAA";
        ParticipantOption option = ParticipantOption.DATA_SHARING;
        
        String value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.setOption(study, healthCode, ParticipantOption.DATA_SHARING, "false");
        
        value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is value 'false'", "false", value);
        
        Map<ParticipantOption,String> allOptions = optionsDao.getAllParticipantOptions(healthCode);
        assertEquals("Values in map same in number as options", 2, allOptions.size());
        assertEquals("Value in map is 'false'", "false", allOptions.get(ParticipantOption.DATA_SHARING));
        
        optionsDao.deleteOption(healthCode, option);
        
        value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void canGetAllOptionsForMultipleParticipants() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode1 = "AAA";
        String healthCode2 = "BBB";
        String healthCode3 = "CCC";
        ParticipantOption option = ParticipantOption.DATA_SHARING;

        optionsDao.setOption(study, healthCode1, ParticipantOption.DATA_SHARING, "AAA");
        optionsDao.setOption(study, healthCode2, ParticipantOption.DATA_SHARING, "BBB");
        optionsDao.setOption(study, healthCode3, ParticipantOption.DATA_SHARING, "CCC");
        
        OptionLookup lookup = optionsDao.getOptionForAllStudyParticipants(study, option);
        assertEquals("AAA", lookup.get("AAA"));
        assertEquals("BBB", lookup.get("BBB"));
        assertEquals("CCC", lookup.get("CCC"));
        assertEquals("true", lookup.get("DDD"));
        
        // Now delete them all
        optionsDao.deleteAllParticipantOptions("AAA");
        optionsDao.deleteAllParticipantOptions("BBB");
        optionsDao.deleteAllParticipantOptions("CCC");
        
        lookup = optionsDao.getOptionForAllStudyParticipants(study, option);
        assertEquals("Value is default value", "true", lookup.get("AAA"));
    }
    
    @Test
    public void testScopeOfDataSharing() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode1 = "AAA";
        
        optionsDao.setOption(study, healthCode1, ParticipantOption.SCOPE_OF_SHARING, ParticipantOption.ScopeOfSharing.ALL_QUALIFIED_RESEARCHERS.name());
        
        OptionLookup lookup = optionsDao.getOptionForAllStudyParticipants(study, ParticipantOption.SCOPE_OF_SHARING);
        // Either way will work, as a string or as a proper enumeration.
        assertEquals(ParticipantOption.ScopeOfSharing.ALL_QUALIFIED_RESEARCHERS.name(), lookup.get("AAA"));
        assertEquals(ParticipantOption.ScopeOfSharing.ALL_QUALIFIED_RESEARCHERS, lookup.getScopeOfSharing("AAA"));
        
        optionsDao.deleteAllParticipantOptions("AAA");
        
        // After deletion, should return to the default value
        lookup = optionsDao.getOptionForAllStudyParticipants(study, ParticipantOption.SCOPE_OF_SHARING);
        assertEquals(ParticipantOption.ScopeOfSharing.NO_SHARING.name(), lookup.get("AAA"));
        assertEquals(ParticipantOption.ScopeOfSharing.NO_SHARING, lookup.getScopeOfSharing("AAA"));
    }
    
}
