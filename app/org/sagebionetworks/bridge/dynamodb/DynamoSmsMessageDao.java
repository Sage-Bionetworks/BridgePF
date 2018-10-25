package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.SmsMessageDao;
import org.sagebionetworks.bridge.models.sms.SmsMessage;

@Component
public class DynamoSmsMessageDao implements SmsMessageDao {
    private DynamoDBMapper mapper;

    /** DynamoDB mapper for the SMS message table. */
    @Resource(name = "smsMessageDdbMapper")
    public void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public SmsMessage getMostRecentMessage(String phoneNumber) {
        // Hash key needs to be an object.
        DynamoSmsMessage key = new DynamoSmsMessage();
        key.setPhoneNumber(phoneNumber);

        // Get the most recent message. This is accomplished by scanning the range key backwards.
        DynamoDBQueryExpression<DynamoSmsMessage> query = new DynamoDBQueryExpression<DynamoSmsMessage>()
                .withHashKeyValues(key).withScanIndexForward(false).withLimit(1);
        QueryResultPage<DynamoSmsMessage> resultPage = mapper.queryPage(DynamoSmsMessage.class, query);
        List<DynamoSmsMessage> messageList = resultPage.getResults();
        if (messageList.isEmpty()) {
            return null;
        } else {
            return messageList.get(0);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void logMessage(SmsMessage smsMessage) {
        mapper.save(smsMessage);
    }
}
