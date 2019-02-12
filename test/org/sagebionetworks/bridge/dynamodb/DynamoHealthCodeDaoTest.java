package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

@RunWith(MockitoJUnitRunner.class)
public class DynamoHealthCodeDaoTest {

    @Mock
    private DynamoDBMapper mapper;
    
    @Captor
    private ArgumentCaptor<DynamoHealthCode> codeCaptor;
    
    private DynamoHealthCodeDao healthCodeDao;
    
    @Before
    public void before() {
        healthCodeDao = new DynamoHealthCodeDao();
        healthCodeDao.setMapper(mapper);
    }
    

    @Test
    public void successfullyRetrieveStudyId() {
        DynamoHealthCode code = new DynamoHealthCode();
        code.setCode("healthCode");
        code.setStudyIdentifier("studyId");
        code.setVersion(1L);
        when(mapper.load(any())).thenReturn(code);
        
        String result = healthCodeDao.getStudyIdentifier("healthCode");
        
        verify(mapper).load(codeCaptor.capture());
        
        assertEquals("studyId", result);
        assertEquals("healthCode", codeCaptor.getValue().getCode());
    }
    
    @Test
    public void noRecord() {
        when(mapper.load(any())).thenReturn(null);
        
        assertNull(healthCodeDao.getStudyIdentifier("healthCode"));
    }
}
