package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ExportTime;

@RunWith(MockitoJUnitRunner.class)
public class DynamoExportTimeDaoTest {
    private static final String STUDY_ID = "test-study-id";
    private static final DateTime LAST_EXPORT_DATE_TIME = DateTime.now();

    private DynamoDBMapper mockMapper;

    private DynamoExportTimeDao dynamoExportTimeDao;

    @Before
    public void setup() {
        ExportTime mockExportTime = ExportTime.create();
        mockExportTime.setStudyId(STUDY_ID);
        mockExportTime.setLastExportDateTime(LAST_EXPORT_DATE_TIME.getMillis());

        mockMapper = mock(DynamoDBMapper.class);
        when(mockMapper.load(any())).thenReturn(mockExportTime);

        dynamoExportTimeDao = new DynamoExportTimeDao();
        dynamoExportTimeDao.setDdbMapper(mockMapper);
    }

    @Test
    public void testGetExportTime() {
        // execute
        ExportTime exportTime = dynamoExportTimeDao.getExportTime(STUDY_ID);
        assertEquals(STUDY_ID, exportTime.getStudyId());
        assertEquals(LAST_EXPORT_DATE_TIME.getMillis(), (long)exportTime.getLastExportDateTime());
        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
    }

    @Test(expected = EntityNotFoundException.class)
    public void testGetExportTimeWithNullStudyId() {
        when(mockMapper.load(any())).thenReturn(null);

        // execute
        dynamoExportTimeDao.getExportTime(STUDY_ID);
    }

    @Test
    public void testDeleteExportTime() {
        // execute
        dynamoExportTimeDao.deleteStudyInfo(STUDY_ID);

        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());
        ArgumentCaptor<DynamoExportTime> captorDelete = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).delete(captorDelete.capture());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
        DynamoExportTime retDynamoExportTimeDelete = captorDelete.getValue();
        assertEquals(STUDY_ID, retDynamoExportTimeDelete.getStudyId());
    }

    @Test
    public void testDeleteExportTimeWithNullStudyId() {
        when(mockMapper.load(any())).thenReturn(null);

        // execute
        dynamoExportTimeDao.deleteStudyInfo(STUDY_ID);
        ArgumentCaptor<DynamoExportTime> captor = ArgumentCaptor.forClass(DynamoExportTime.class);
        verify(mockMapper).load(captor.capture());
        verify(mockMapper, times(0)).delete(any());

        DynamoExportTime retDynamoExportTime = captor.getValue();
        assertEquals(STUDY_ID, retDynamoExportTime.getStudyId());
    }
}
