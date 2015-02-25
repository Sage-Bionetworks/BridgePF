package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.lang.reflect.Method;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.SurveyRule.Operator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.impl.ObjectIdWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;

/**
 * Use this version of the ObjectMapper in preference to its parent class. This version
 * ignores unknown properties and it adds a "type" property to all objects it serializes, 
 * which is part of our API contract. Also, ObjectMapper is threadsafe, so we are able 
 * to create a singleton available via <code>BridgeObjectMapper.get()</code>.
 *
 */
@SuppressWarnings("serial")
public class BridgeObjectMapper extends ObjectMapper {
    
    private static final BridgeObjectMapper INSTANCE = new BridgeObjectMapper();
    
    public static final BridgeObjectMapper get() {
        return INSTANCE;
    }

    public BridgeObjectMapper() {
        this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.registerModule(new SimpleModule() {
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                BeanSerializerModifier bsm = new BeanSerializerModifier() {
                    public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc,
                            JsonSerializer<?> serializer) {
                        if (serializer instanceof BeanSerializerBase) {
                            return new ExtraFieldSerializer((BeanSerializerBase) serializer);
                        }
                        return serializer;
                    }                   
                };
                context.addBeanSerializerModifier(bsm);
            }
        });
        SimpleModule serializers = new SimpleModule();
        
        serializers.addDeserializer(Schedule.class, new ScheduleDeserializer());
        serializers.addSerializer(Schedule.class, new ScheduleSerializer());
        
        serializers.addDeserializer(ActivityType.class, new ActivityTypeDeserializer());
        serializers.addSerializer(ActivityType.class, new LowercaseEnumJsonSerializer());
        
        serializers.addDeserializer(DataType.class, new DataTypeJsonDeserializer());
        serializers.addSerializer(DataType.class, new LowercaseEnumJsonSerializer());
        
        serializers.addDeserializer(Operator.class, new OperatorJsonDeserializer());
        serializers.addSerializer(Operator.class, new LowercaseEnumJsonSerializer());

        serializers.addDeserializer(ParticipantOption.ScopeOfSharing.class, new ScopeOfSharingDeserializer());
        serializers.addSerializer(ParticipantOption.ScopeOfSharing.class, new LowercaseEnumJsonSerializer());
        
        this.registerModule(serializers);
    }
    
    private class ExtraFieldSerializer extends BeanSerializerBase {
        ExtraFieldSerializer(BeanSerializerBase source) {
            super(source);
        }
        ExtraFieldSerializer(ExtraFieldSerializer source, ObjectIdWriter objectIdWriter) {
            super(source, objectIdWriter);
        }
        ExtraFieldSerializer(ExtraFieldSerializer source, String[] toIgnore) {
            super(source, toIgnore);
        }
        public BeanSerializerBase withObjectIdWriter(ObjectIdWriter objectIdWriter) {
            return new ExtraFieldSerializer(this, objectIdWriter);
        }
        protected BeanSerializerBase withIgnorals(String[] toIgnore) {
            return new ExtraFieldSerializer(this, toIgnore);
        }
        public BeanSerializerBase withFilterId(Object object) {
            return null;
        }
        public void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider) throws IOException,
                JsonGenerationException {
            jgen.writeStartObject();
            serializeFields(bean, jgen, provider);
            // We only want to do this if there is not a getType() method
            if (noTypeProperty(bean)) {
                String typeName = BridgeUtils.getTypeName(bean.getClass());
                if (typeName != null) {
                    jgen.writeStringField("type", typeName);    
                }
            }
            jgen.writeEndObject();
        }
        @Override
        protected BeanSerializerBase asArraySerializer() {
            return this;
        }
        private boolean noTypeProperty(Object bean) {
            for (Method method : bean.getClass().getMethods()) {
                if ("getType".equals(method.getName())) {
                    return false;
                }
            }
            return true;
         }
    }
}
