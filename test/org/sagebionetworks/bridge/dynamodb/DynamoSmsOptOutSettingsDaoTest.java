package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

public class DynamoSmsOptOutSettingsDaoTest {
    private static final String PHONE_NUMBER = "+12065550123";

    private DynamoSmsOptOutSettingsDao dao;
    private DynamoDBMapper mockMapper;

    @Before
    public void before() {
        mockMapper = mock(DynamoDBMapper.class);

        dao = new DynamoSmsOptOutSettingsDao();
        dao.setMapper(mockMapper);
    }

    @Test
    public void getOptOutSettings() {
        // Mock mapper.
        SmsOptOutSettings mapperOutput = new DynamoSmsOptOutSettings();
        when(mockMapper.load(any())).thenReturn(mapperOutput);

        // Execute.
        SmsOptOutSettings daoOutput = dao.getOptOutSettings(PHONE_NUMBER);
        assertSame(mapperOutput, daoOutput);

        // Verify mapper call.
        ArgumentCaptor<SmsOptOutSettings> hashKeyCaptor = ArgumentCaptor.forClass(SmsOptOutSettings.class);
        verify(mockMapper).load(hashKeyCaptor.capture());

        SmsOptOutSettings hashKey = hashKeyCaptor.getValue();
        assertEquals(PHONE_NUMBER, hashKey.getPhoneNumber());
    }

    @Test
    public void setOptOutSettings() {
        // Execute.
        SmsOptOutSettings daoInput = new DynamoSmsOptOutSettings();
        dao.setOptOutSettings(daoInput);

        // Verify mapper call.
        verify(mockMapper).save(same(daoInput));
    }
}
