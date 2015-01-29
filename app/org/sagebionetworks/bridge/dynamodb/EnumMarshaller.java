package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

/**
 * <p>
 * Enum marshaller for DynamoDB, since DynamoDB doesn't have one natively.
 * </p>
 * <p>
 * We use raw Enum type rather than the proper T extends Enum, because the DynamoDBMarshalling annotation makes it
 * impossible to specify generics.
 * </p>
 */
@SuppressWarnings("rawtypes")
public class EnumMarshaller implements DynamoDBMarshaller<Enum> {
    /** {@inheritDoc} */
    @Override
    public String marshall(Enum enumVal) {
        return enumVal.name();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public Enum unmarshall(Class<Enum> clazz, String obj) {
        return Enum.valueOf(clazz, obj);
    }
}
