package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;

public class DynamoHealthCodeDao {

    private DynamoDBMapper mapper;

    public boolean setIfNotExist(String code) {
        DynamoHealthCode toSave = new DynamoHealthCode(code);
        AttributeValue expected = new AttributeValue().withS(code);
        DynamoDBSaveExpression saveExpr = new DynamoDBSaveExpression()
                .withExpectedEntry("code", new ExpectedAttributeValue()
                        .withExists(false).withValue(expected));
        mapper.save(toSave, saveExpr);
        return false;
    }
}
