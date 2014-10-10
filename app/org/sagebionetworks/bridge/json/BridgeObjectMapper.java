package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.BridgeUtils;

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
public class BridgeObjectMapper extends ObjectMapper {
    
    private static final BridgeObjectMapper INSTANCE = new BridgeObjectMapper();
    
    public static final BridgeObjectMapper get() {
        return INSTANCE;
    }
    
    private static final long serialVersionUID = 7329223290649069703L;

    public BridgeObjectMapper() {
        this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.registerModule(new SimpleModule() {
            private static final long serialVersionUID = 8900953081295761099L;
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
        public void serialize(Object bean, JsonGenerator jgen, SerializerProvider provider) 
            throws IOException, JsonGenerationException {
            jgen.writeStartObject();
            serializeFields(bean, jgen, provider);
            String typeName = BridgeUtils.getTypeName(bean.getClass());
            if (typeName != null) {
                jgen.writeStringField("type", typeName);    
            }
            jgen.writeEndObject();
        }
        @Override
        protected BeanSerializerBase asArraySerializer() {
            return null;
        }
    }
}
