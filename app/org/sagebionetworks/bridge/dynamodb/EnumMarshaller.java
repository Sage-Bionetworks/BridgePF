package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

/**
 * <p>
 * Enum marshaller for DynamoDB, since DynamoDB doesn't have one natively. The 
 * specific enumeration type is passed in during construction by DynamoDB, when 
 * this converter is discovered through the @DynamoDBTypeConverted annotation. 
 * </p>
 */
@SuppressWarnings("rawtypes")
public class EnumMarshaller implements DynamoDBTypeConverter<String,Enum> {
    
    private final Class<? extends Enum> enumType;
    
    public EnumMarshaller(Class<? extends Enum> enumType) {
        this.enumType = enumType;
    }

    /** {@inheritDoc} */
    @Override
    public String convert(Enum e) {
        return e.name();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public Enum unconvert(String string) {
        return Enum.valueOf(enumType, string);
    }
}
