package org.sagebionetworks.bridge.validators;

import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Validator;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Validate {

    public static void entityThrowingException(Validator validator, Object object) {
        Preconditions.checkNotNull(validator);
        Preconditions.checkArgument(object instanceof BridgeEntity);
        
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
            messages.add(error.getDefaultMessage());
        }
        for (FieldError error : errors.getFieldErrors()) {
            messages.add(error.getField() + " is " + error.getDefaultMessage());
        }
        return String.format("%s is invalid: %s", errors.getObjectName(), Joiner.on("; ").join(messages));
    }
    
    private static Map<String,List<String>> convertBindingResultToSimpleMap(BindingResult errors) {
        Map<String,List<String>> map = Maps.newHashMap();

        if (errors.hasGlobalErrors()) {
            List<String> list = Lists.newArrayList();
            for (ObjectError error : errors.getGlobalErrors()) {
                list.add(error.getDefaultMessage());
            }
            map.put(errors.getObjectName(), list);
        }
        if (errors.hasFieldErrors()) {
            for (FieldError error : errors.getFieldErrors()) {
                String fieldName = error.getField();
                if (map.get(fieldName) == null) {
                    map.put(fieldName, Lists.<String>newArrayList());
                }
                String msg = error.getField() + " is " + error.getDefaultMessage();
                map.get(fieldName).add(msg);
            }
        }
        return map;
        
    }
    
}
