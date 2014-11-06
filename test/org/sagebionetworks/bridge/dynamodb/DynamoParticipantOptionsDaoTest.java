package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Resource;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Sets;

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
    
    @Test
    public void crudOptionForParticipant() {
        Study study = studyService.getStudyByKey(TestConstants.TEST_STUDY_KEY);
        String healthDataCode = "AAA";
        Option option = Option.DATA_SHARING;
        
        String value = optionsDao.getOption(healthDataCode, option, "def");
        assertEquals("Value is default value", "def", value);
        
        optionsDao.setOption(study, healthDataCode, Option.DATA_SHARING, "true");
        
        value = optionsDao.getOption(healthDataCode, option, "def");
        assertEquals("Value is value 'true'", "true", value);
        
        Map<Option,String> allOptions = optionsDao.getAllParticipantOptions(healthDataCode);
        assertEquals("One value in the map", 1, allOptions.size());
        assertEquals("Value in map is 'true'", "true", allOptions.get(Option.DATA_SHARING));
        
        optionsDao.deleteOption(healthDataCode, option);
        
        value = optionsDao.getOption(healthDataCode, option, "def");
        assertEquals("Value is default value", "def", value);
        
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
        
        Map<String,String> options = optionsDao.getOptionForAllStudyParticipants(study, option);
        assertEquals("There should be 3 options", 3, options.values().size());
        assertEquals("All the keys are correct", Sets.newHashSet("AAA","BBB","CCC"), options.keySet());
        assertEquals("And the values are right", "BBB", options.get("BBB"));
        
        // Now delete them all
        optionsDao.deleteAllParticipantOptions("AAA");
        optionsDao.deleteAllParticipantOptions("BBB");
        optionsDao.deleteAllParticipantOptions("CCC");
    }
    
}
