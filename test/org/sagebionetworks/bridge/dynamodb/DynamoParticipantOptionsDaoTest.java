package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;

import javax.annotation.Resource;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.StudyServiceImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import com.google.common.collect.Maps;

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
    public void canChangeSharingScope() {
        Study study = studyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER);
        String healthCode1 = "AAA";
        
        optionsDao.setOption(study, healthCode1, ParticipantOption.SHARING_SCOPE, SharingScope.ALL_QUALIFIED_RESEARCHERS.name());
        
        OptionLookup lookup = optionsDao.getOptionForAllStudyParticipants(study, ParticipantOption.SHARING_SCOPE);
        // Either way will work, as a string or as a proper enumeration.
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS.name(), lookup.get("AAA"));
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, lookup.getSharingScope("AAA"));
        
        optionsDao.deleteAllParticipantOptions("AAA");
        
        // After deletion, should return to the default value
        lookup = optionsDao.getOptionForAllStudyParticipants(study, ParticipantOption.SHARING_SCOPE);
        assertEquals(SharingScope.NO_SHARING.name(), lookup.get("AAA"));
        assertEquals(SharingScope.NO_SHARING, lookup.getSharingScope("AAA"));
    }
    
    @Test
    public void getAllParticipantOptions() {
        // Verify we're only calling load
        DynamoParticipantOptions options = getOptionsForUser();
        
        DynamoDBMapper mapper = mock(DynamoDBMapper.class);
        when(mapper.load(any())).thenReturn(options);

        DynamoParticipantOptionsDao optionsDao = new DynamoParticipantOptionsDao();
        optionsDao.setMapper(mapper);
        
        optionsDao.getAllParticipantOptions("aaa");
        
        verify(mapper).load(any());
        verifyNoMoreInteractions(mapper);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getOptionForAllStudyParticipants() {
        // Verify we're only calling scan

        PaginatedScanList<DynamoParticipantOptions> results = mock(PaginatedScanList.class);
        when(results.iterator()).thenReturn(new ArrayList<DynamoParticipantOptions>().iterator());
        
        DynamoDBMapper mapper = mock(DynamoDBMapper.class);
        when(mapper.scan(
            (Class<DynamoParticipantOptions>)any(Class.class), 
            any(DynamoDBScanExpression.class)
        )).thenReturn(results);

        DynamoParticipantOptionsDao optionsDao = new DynamoParticipantOptionsDao();
        optionsDao.setMapper(mapper);
        
        optionsDao.getOptionForAllStudyParticipants(new StudyIdentifierImpl("api"), ParticipantOption.SHARING_SCOPE);
        
        verify(mapper).scan(
            (Class<DynamoParticipantOptions>)any(Class.class), 
            any(DynamoDBScanExpression.class)
        );
        verifyNoMoreInteractions(mapper);
    }

    private DynamoParticipantOptions getOptionsForUser() {
        DynamoParticipantOptions options = new DynamoParticipantOptions();
        options.setHealthCode("aaa");
        options.setStudyKey("api");
        options.setOptions(Maps.<String,String>newHashMap());
        return options;
    }
}
