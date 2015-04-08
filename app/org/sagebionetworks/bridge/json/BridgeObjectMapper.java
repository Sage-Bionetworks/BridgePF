package org.sagebionetworks.bridge.json;

import java.io.IOException;
import java.lang.reflect.Method;

import org.sagebionetworks.bridge.BridgeUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.impl.ObjectIdWriter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.datatype.joda.JodaModule;

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
        this.registerModule(new JodaModule());
        this.registerModule(new LowercaseEnumModule());
        this.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        /* This works, but we don't want to do it. While it's nice to have shorter dates and times in some classes, like 
         * the Schedule (where seconds/milliseconds aren't needed), we do not know if this creates challenges for some 
         * language libraries when parsing ISO 8601 strings, and there are DateTimes in the system that we do want to 
         * server as timestamps to the millisecond. If used, we'd need to scope to specific properties of specific objects.
        mod.addSerializer(LocalTime.class, new StdSerializer<LocalTime>(LocalTime.class) {
            @Override public void serialize(LocalTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
                jgen.writeString(value.toString("HH:mm"));
            }
        });
        mod.addSerializer(DateTime.class, new StdSerializer<DateTime>(DateTime.class) {
            @Override public void serialize(DateTime value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
                jgen.writeString(value.toString("yyyy-MM-dd'T'HH:mmZZ"));
            }
        });
         */
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
