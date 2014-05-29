package org.sagebionetworks.bridge.dynamodb;

import java.lang.reflect.Method;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsync;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;

public class DynamoInitializer {

    /**
     * Creates DynamoDB tables, if they do not exist yet, from the annotated types.
     * Throws an error if the table exists but the schema does not match.
     */
    public static void init() {
        Class<?>[] classes = DynamoTable.class.getClasses();
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(DynamoDBTable.class)) {
                DynamoDBTable table = clazz.getAnnotation(DynamoDBTable.class);
                table.tableName();
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(DynamoDBHashKey.class)) {
                        DynamoDBHashKey hashKey = method.getAnnotation(DynamoDBHashKey.class);
                        hashKey.attributeName();
                    } else if (method.isAnnotationPresent(DynamoDBRangeKey.class)) {
                        DynamoDBRangeKey rangeKey = method.getAnnotation(DynamoDBRangeKey.class);
                        rangeKey.attributeName();
                    } else if (method.isAnnotationPresent(DynamoDBIndexRangeKey.class)) {
                        DynamoDBIndexRangeKey indexKey = method.getAnnotation(DynamoDBIndexRangeKey.class);
                        indexKey.attributeName();
                        indexKey.localSecondaryIndexName();
                    } else if (method.isAnnotationPresent(DynamoDBAttribute.class)) {
                        DynamoDBAttribute attr = method.getAnnotation(DynamoDBAttribute.class);
                        attr.attributeName();
                    }
                }
            }
        }
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String awsKey = config.getProperty("aws.key");
        String secretKey = config.getProperty("aws.secret.key");
        AmazonDynamoDBAsync dynamo = new AmazonDynamoDBAsyncClient(new BasicAWSCredentials(awsKey, secretKey));
        CreateTableRequest ctRequest = (new CreateTableRequest())
                .withTableName("tableName");
        ListTablesResult result = dynamo.listTables();
        result.getTableNames();
    }
}
