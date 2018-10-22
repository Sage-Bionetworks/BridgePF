package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SmsOptOutSettingsDao;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;

@Component
public class DynamoSmsOptOutSettingsDao implements SmsOptOutSettingsDao {
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the opt-out settings table. */
    @Resource(name = "smsOptOutDdbMapper")
    public final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public SmsOptOutSettings getOptOutSettings(String phoneNumber) {
        // Hash key needs to be an object.
        DynamoSmsOptOutSettings key = new DynamoSmsOptOutSettings();
        key.setPhoneNumber(phoneNumber);
        return mapper.load(key);
    }

    /** {@inheritDoc} */
    @Override
    public void setOptOutSettings(SmsOptOutSettings optOut) {
        mapper.save(optOut);
    }
}
