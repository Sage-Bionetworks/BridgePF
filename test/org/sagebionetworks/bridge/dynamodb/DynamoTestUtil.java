package org.sagebionetworks.bridge.dynamodb;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

public class DynamoTestUtil {

    private static final AmazonDynamoDB DYNAMO;
    static {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String awsKey = config.getProperty("aws.key");
        String secretKey = config.getProperty("aws.secret.key");
        DYNAMO = new AmazonDynamoDBClient(new BasicAWSCredentials(awsKey,
                secretKey));
    }

    public static void clearTable(Class<? extends DynamoTable> clazz,
            String... nonKeyAttrs) {
        String tableName = clazz.getAnnotation(DynamoDBTable.class).tableName();
        tableName = DynamoInitializer.getTableName(tableName);
        ScanResult result = DYNAMO.scan(new ScanRequest(tableName));
        List<Map<String, AttributeValue>> items = result.getItems();
        do {
            for (Map<String, AttributeValue> item : items) {
                for (String nonKeyAttr : nonKeyAttrs) {
                    item.remove(nonKeyAttr);
                }
                DYNAMO.deleteItem(tableName, item);
            }
            items = null;
            Map<String, AttributeValue> lastKey = result.getLastEvaluatedKey();
            if (lastKey != null) {
                result = DYNAMO.scan(new ScanRequest(tableName)
                        .withExclusiveStartKey(lastKey));
                items = result.getItems();
            }
        } while (items != null);
    }
}
