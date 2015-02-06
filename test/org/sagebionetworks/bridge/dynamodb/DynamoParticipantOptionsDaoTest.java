package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
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
    public void crudOptionForParticipant() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode = "AAA";
        Option option = Option.DATA_SHARING;
        
        String value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.setOption(study, healthCode, Option.DATA_SHARING, "false");
        
        value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is value 'false'", "false", value);
        
        Map<Option,String> allOptions = optionsDao.getAllParticipantOptions(healthCode);
        assertEquals("One value in the map", 1, allOptions.size());
        assertEquals("Value in map is 'false'", "false", allOptions.get(Option.DATA_SHARING));
        
        optionsDao.deleteOption(healthCode, option);
        
        value = optionsDao.getOption(healthCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.deleteAllParticipantOptions(healthCode);
    }

    @Test
    public void canGetAllOptionsForMultipleParticipants() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode1 = "AAA";
        String healthCode2 = "BBB";
        String healthCode3 = "CCC";
        Option option = Option.DATA_SHARING;

        optionsDao.setOption(study, healthCode1, Option.DATA_SHARING, "AAA");
        optionsDao.setOption(study, healthCode2, Option.DATA_SHARING, "BBB");
        optionsDao.setOption(study, healthCode3, Option.DATA_SHARING, "CCC");
        
        OptionLookup lookup = optionsDao.getOptionForAllStudyParticipants(study, option);
        assertEquals("AAA", lookup.get("AAA"));
        assertEquals("BBB", lookup.get("BBB"));
        assertEquals("CCC", lookup.get("CCC"));
        assertEquals("true", lookup.get("DDD"));
        
        // Now delete them all
        optionsDao.deleteAllParticipantOptions("AAA");
        optionsDao.deleteAllParticipantOptions("BBB");
        optionsDao.deleteAllParticipantOptions("CCC");
    }
    
}
