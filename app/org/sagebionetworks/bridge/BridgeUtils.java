package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BridgeUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(BridgeUtils.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Used for the Stormpath REST API. This should go away when they update their SDK.
     * @param client
     * @param url
     * @param node
     * @return
     * @throws Exception
     */
    public static ObjectNode getJSON(CloseableHttpClient client, String url) throws Exception {
        HttpGet get = new HttpGet(url);
        get.setHeader("Accept", "application/json");
        try(CloseableHttpResponse response = client.execute(get)) {
            ObjectNode object = (ObjectNode)MAPPER.readTree(EntityUtils.toString(response.getEntity(), "UTF-8"));
            if (logger.isDebugEnabled()) {
                logger.debug("GET: " + url + "\n   request: <EMPTY>\n   response: "+ MAPPER.writeValueAsString(object));
            }
            return object;
        }
    }
    
    /**
     * Used for the Stormpath REST API. This should go away when they update their SDK.
     * @param client
     * @param url
     * @param node
     * @return
     * @throws Exception
     */
    public static ObjectNode postJSON(CloseableHttpClient client, String url, ObjectNode node) throws Exception {
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new StringEntity(MAPPER.writeValueAsString(node), "UTF-8"));
        
        try (CloseableHttpResponse response = client.execute(post)) {
            ObjectNode object = (ObjectNode)MAPPER.readTree(EntityUtils.toString(response.getEntity(), "UTF-8"));
            if (logger.isDebugEnabled()) {
                logger.debug("POST: " + url + "\n   request: "+MAPPER.writeValueAsString(node)+"\n   response: " + MAPPER.writeValueAsString(object));    
            }
            return object;
        }
    }
    
    public static String resolveTemplate(String template, Map<String,String> values) {
        checkNotNull(template);
        checkNotNull(values);
        
        for (String key : values.keySet()) {
            String value = values.get(key);
            if (value != null) {
                String regex = "\\$\\{"+key+"\\}";
                template = template.replaceAll(regex, values.get(key));
            }
        }
        return template;
    }
    
    public static String toStringQuietly(Resource resource) {
        try {
            return IOUtils.toString(resource.getInputStream());
        } catch(IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
    
    public static String generateGuid() {
        return UUID.randomUUID().toString();
    }
    
    public static String generateTaskRunKey(Task task) {
        checkNotNull(task.getSchedulePlanGuid());
        checkNotNull(task.getScheduledOn());
        return String.format("%s:%s", task.getSchedulePlanGuid(), Long.toString(task.getScheduledOn()));
    }
    
    /**
     * Searches for a @BridgeTypeName annotation on this or any parent class in the class hierarchy, returning 
     * that value as the type name. If none exists, defaults to the simple class name. 
     * @param clazz
     * @return
     */
    public static String getTypeName(Class<?> clazz) {
        BridgeTypeName att = AnnotationUtils.findAnnotation(clazz,BridgeTypeName.class);
        if (att != null) {
            return att.value();
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
            List<String> messages = Lists.newArrayList();
            for (FailedBatch failure : failures) {
                String message = failure.getException().getMessage();
                messages.add(message);
                String ids = Joiner.on("; ").join(failure.getUnprocessedItems().keySet());
                messages.add(ids);
            }
            throw new BridgeServiceException(Joiner.on(", ").join(messages));
        }
    }
    
    public static boolean isEmpty(Collection<?> coll) {
        return (coll == null || coll.isEmpty());
    }
    
    public static <S,T> Map<S,T> asMap(List<T> list, Function<T,S> function) {
        Map<S,T> map = Maps.newHashMap();
        if (list != null && function != null) {
            for (T item : list) {
                map.put(function.apply(item), item);
            }
        }
        return map;
    }
    
    public static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch(NumberFormatException e) {
            throw new RuntimeException("'" + value + "' is not a valid integer");
        }
    }
    
    public static void checkNewEntity(BridgeEntity entity, Object field, String message) {
        if (field != null) {
            throw new EntityAlreadyExistsException(entity, message);
        }
    }
    
    public static String toString(Long datetime) {
        return (datetime == null) ? null : new DateTime(datetime).toString();
    }
}
