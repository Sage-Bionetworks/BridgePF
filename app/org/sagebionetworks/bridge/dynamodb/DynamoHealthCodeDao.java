package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.dao.HealthCodeDao;
import org.sagebionetworks.bridge.models.HealthCode;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;

public class DynamoHealthCodeDao implements HealthCodeDao {

    private DynamoDBMapper mapper;

    @Override
    public boolean setIfNotExist(HealthCode healthCode) {
        final String code = healthCode.getCode();
        DynamoHealthCode toSave = new DynamoHealthCode(code);
        AttributeValue expected = new AttributeValue().withS(code);
        DynamoDBSaveExpression saveExpr = new DynamoDBSaveExpression()
                .withExpectedEntry("code", new ExpectedAttributeValue()
                        .withExists(false).withValue(expected));
        mapper.save(toSave, saveExpr);
        return false;
    }
}
