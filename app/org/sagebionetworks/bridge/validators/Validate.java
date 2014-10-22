package org.sagebionetworks.bridge.validators;

import global.JsonSchemaValidator;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthDataRecord;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Tracker;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Validate {
    
    private final static JsonSchemaValidator schemaValidator = new JsonSchemaValidator();
    
    public static String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    
    public static String CANNOT_BE_NULL = "cannot be missing or null";
  
    public static void jsonWithSchema(Tracker tracker, JsonNode node) {
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), tracker.getName());
        try {
            ProcessingReport report = schemaValidator.validate(tracker, node);
            for (ProcessingMessage message : report) {
                errors.rejectValue("data", "invalid: " + message.getMessage());
            }
        } catch(Throwable throwable) {
            throw new BadRequestException(throwable);
        }
        if (errors.hasErrors()) {
            String message = convertBindingResultToMessage(errors);
            Map<String,List<String>> map = convertBindingResultToSimpleMap(errors);
            
            throw new InvalidEntityException(new DynamoHealthDataRecord(), message, map);
        }
    }
    
    public static void entityThrowingException(Validator validator, Object object) {
        Preconditions.checkNotNull(validator);
        Preconditions.checkArgument(object instanceof BridgeEntity);
        Preconditions.checkArgument(validator.supports(object.getClass()), "Invalid validator");
        
        String entityName = BridgeUtils.getTypeName(object.getClass());
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), entityName);
        
        validator.validate(object, errors);
        
        if (errors.hasErrors()) {
            String message = convertBindingResultToMessage(errors);
            Map<String,List<String>> map = convertBindingResultToSimpleMap(errors);
            
            throw new InvalidEntityException((BridgeEntity)object, message, map);
        }
    }
    private static String convertBindingResultToMessage(BindingResult errors) {
        List<String> messages = Lists.newArrayListWithCapacity(errors.getErrorCount());
        for (ObjectError error : errors.getGlobalErrors()) {
            messages.add(errorToString(error.getObjectName(), error));    
        }
        for (FieldError error : errors.getFieldErrors()) {
            messages.add(errorToString(error.getField(), error));
        }
        return String.format("%s is invalid: %s", errors.getObjectName(), Joiner.on("; ").join(messages));
    }
    private static Map<String,List<String>> convertBindingResultToSimpleMap(BindingResult errors) {
        Map<String,List<String>> map = Maps.newHashMap();

        if (errors.hasGlobalErrors()) {
            List<String> list = Lists.newArrayList();
            for (ObjectError error : errors.getGlobalErrors()) {
                list.add(errorToString(error.getObjectName(), error));
            }
            map.put(errors.getObjectName(), list);
        }
        if (errors.hasFieldErrors()) {
            for (FieldError error : errors.getFieldErrors()) {
                String fieldName = error.getField();
                if (map.get(fieldName) == null) {
                    map.put(fieldName, Lists.<String>newArrayList());
                }
                map.get(fieldName).add(errorToString(error.getField(), error));
            }
        }
        return map;
    }
    private static String errorToString(String name, ObjectError error) {
        if (error.getDefaultMessage() != null) {
            return name + " is " + error.getDefaultMessage();
        } else if (error.getCode() != null && error.getArguments() == null) {
            return name + " is " + error.getCode();
        } else {
            String base = (error.getCode() != null) ? error.getCode() : error.getDefaultMessage();
            String message = String.format(base, error.getArguments());
            return name + " " + message;
        }
    }
}
