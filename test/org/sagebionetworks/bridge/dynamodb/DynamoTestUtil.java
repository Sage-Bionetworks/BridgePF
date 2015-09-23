package org.sagebionetworks.bridge.dynamodb;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.springframework.core.annotation.AnnotationUtils;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
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

    public static void clearTable(Class<?> clazz) {
        List<String> keyAttrs = getKeyAttrs(clazz);
        BridgeConfig config = BridgeConfigFactory.getConfig();
        String tableName = DynamoUtils.getFullyQualifiedTableName(clazz, config);
        ScanResult result = DYNAMO.scan((new ScanRequest(tableName)).withAttributesToGet(keyAttrs));
        List<Map<String, AttributeValue>> items = result.getItems();
        do {
            for (Map<String, AttributeValue> item : items) {
                DYNAMO.deleteItem(tableName, item);
            }
            items = null;
            Map<String, AttributeValue> lastKey = result.getLastEvaluatedKey();
            if (lastKey != null) {
                result = DYNAMO.scan(new ScanRequest(tableName)
                        .withAttributesToGet(keyAttrs)
                        .withExclusiveStartKey(lastKey));
                items = result.getItems();
            }
        } while (items != null);
    }

    private static List<String> getKeyAttrs(Class<?> clazz) {
        List<String> keyAttrs = new ArrayList<String>();
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            
            DynamoDBHashKey hashKeyAtt = AnnotationUtils.getAnnotation(method, DynamoDBHashKey.class);
            DynamoDBRangeKey rangeKeyAtt = AnnotationUtils.getAnnotation(method, DynamoDBRangeKey.class);
            
            if (hashKeyAtt != null && isNotBlank(hashKeyAtt.attributeName())) {
                keyAttrs.add(hashKeyAtt.attributeName());
            } else if (rangeKeyAtt != null && isNotBlank(rangeKeyAtt.attributeName())) {
                keyAttrs.add(rangeKeyAtt.attributeName());
            } else if (hashKeyAtt != null || rangeKeyAtt != null) {
                String name = method.getName();
                // Remove 'get', 'set', or 'is'
                if (name.startsWith("get") || name.startsWith("set")) {
                    name = name.substring(3);
                } else if (name.startsWith("is")) {
                    name = name.substring(2);
                }
                // Make sure the first letter is lower case
                char[] chars = name.toCharArray();
                chars[0] = Character.toLowerCase(chars[0]);
                keyAttrs.add(new String(chars));
            }
        }
        return keyAttrs;
    }
}
