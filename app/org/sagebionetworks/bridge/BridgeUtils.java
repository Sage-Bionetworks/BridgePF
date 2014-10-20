package org.sagebionetworks.bridge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.validators.Messages;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.google.common.base.Joiner;

public class BridgeUtils {

    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
    
    public static String getTypeName(Class<?> clazz) {
        try {
            BridgeTypeName att = (BridgeTypeName)clazz.getAnnotation(BridgeTypeName.class);
            if (att == null) {
                Class<?>[] ifcs = clazz.getInterfaces();
                for (Class<?> ifc : ifcs) {
                    if (att == null) {
                        att = ifc.getAnnotation(BridgeTypeName.class);    
                    }
                }
            }
            if (att != null) {
                return att.value();
            }
        } catch(Throwable t) {
        }
        return clazz.getSimpleName();
    }
    
    /**
     * All batch methods in Dynamo return a list of failures rather than 
     * throwing an exception. We should have an exception specifically for 
     * these so the caller gets a list of items back, but for now, convert 
     * to a generic exception;
     * @param failures
     */
    public static void ifFailuresThrowException(List<FailedBatch> failures) {
        if (!failures.isEmpty()) {
            Messages messages = new Messages();
            for (FailedBatch failure : failures) {
                String message = failure.getException().getMessage();
                messages.add(message);
                String ids = Joiner.on("; ").join(failure.getUnprocessedItems().keySet());
                messages.add(ids);
            }
            throw new BridgeServiceException(messages.join());
        }
    }
    
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }
    
}
