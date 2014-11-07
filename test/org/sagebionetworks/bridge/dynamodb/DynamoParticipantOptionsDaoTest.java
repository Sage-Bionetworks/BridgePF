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
import org.sagebionetworks.bridge.models.Study;
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
        Study study = studyService.getStudyByKey(TestConstants.TEST_STUDY_KEY);
        String healthDataCode = "AAA";
        Option option = Option.DATA_SHARING;
        
        String value = optionsDao.getOption(healthDataCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.setOption(study, healthDataCode, Option.DATA_SHARING, "false");
        
        value = optionsDao.getOption(healthDataCode, option);
        assertEquals("Value is value 'false'", "false", value);
        
        Map<Option,String> allOptions = optionsDao.getAllParticipantOptions(healthDataCode);
        assertEquals("One value in the map", 1, allOptions.size());
        assertEquals("Value in map is 'false'", "false", allOptions.get(Option.DATA_SHARING));
        
        optionsDao.deleteOption(healthDataCode, option);
        
        value = optionsDao.getOption(healthDataCode, option);
        assertEquals("Value is default value", "true", value);
        
        optionsDao.deleteAllParticipantOptions(healthDataCode);
    }

    @Test
    public void canGetAllOptionsForMultipleParticipants() {
        Study study = studyService.getStudyByKey(TestConstants.TEST_STUDY_KEY);
        String healthDataCode1 = "AAA";
        String healthDataCode2 = "BBB";
        String healthDataCode3 = "CCC";
        Option option = Option.DATA_SHARING;

        optionsDao.setOption(study, healthDataCode1, Option.DATA_SHARING, "AAA");
        optionsDao.setOption(study, healthDataCode2, Option.DATA_SHARING, "BBB");
        optionsDao.setOption(study, healthDataCode3, Option.DATA_SHARING, "CCC");
        
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
