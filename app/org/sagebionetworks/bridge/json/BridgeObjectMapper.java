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
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.datatype.joda.JodaModule;

/**
 * <p>Use this version of the ObjectMapper in preference to its parent class. This version
 * ignores unknown properties and it adds a "type" property to all objects it serializes, 
 * which is part of our API contract. Also, ObjectMapper is threadsafe, so we are able 
 * to create a singleton available via <code>BridgeObjectMapper.get()</code>.</p>
 * 
 * <p>Attributes with @JsonIgnore are never serialized to JSON, to selectively remove some 
 * properties from some serializations of an object, first define a filter:</p>
 * 
 *     <blockquote>
 *     // Filter must be named "filter"
 *     FilterProvider filter = new SimpleFilterProvider()
 *         .addFilter("filter", SimpleBeanPropertyFilter.serializeAllExcept("propName"))
 *     </blockquote>
 * 
 * <p>And then create a new BridgeObjectMapper instance to retrieve a writer that will filter those 
 * properties:</p>
 * 
 *     <blockquote>
 *     ObjectWriter writer = new BridgeObjectMapper().writer(filter);
 *     writer.writeValueAsString(object); // will not include "propName"
 *     </blockquote>
 */
@SuppressWarnings("serial")
public class BridgeObjectMapper extends ObjectMapper {
    
    private static final BridgeObjectMapper INSTANCE = new BridgeObjectMapper();
    
    public static final BridgeObjectMapper get() {
        return INSTANCE;
    }

    public BridgeObjectMapper() {
        super();
        this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // This suppresses a failure if a class is found with a "filter" filter declared on it,
        // when you are trying to serialize without the filter. It's a Jackson oddity they may fix.
        FilterProvider filter = new SimpleFilterProvider().setFailOnUnknownId(false);
        this.setFilters(filter);
        this.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.registerModule(new TypeModule());
        this.registerModule(new JodaModule());
        this.registerModule(new LowercaseEnumModule());
    }
    
    /**
     * Extend this mapper to use the TypeBeanSerializer. 
     */
    private class TypeModule extends SimpleModule {
        public void setupModule(SetupContext context) {
            super.setupModule(context);
            BeanSerializerModifier bsm = new BeanSerializerModifier() {
                public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc,
                                JsonSerializer<?> serializer) {
                    if (serializer instanceof BeanSerializerBase) {
                        return new TypeBeanSerializer((BeanSerializerBase) serializer);
                    }
                    return serializer;
                }
            };
            context.addBeanSerializerModifier(bsm);
        }

    }

    /**
     * A serializer that adds a "type" attribute to the JSON produced by this mapper. It will use the 
     * simple class name, or the value of the @BridgeTypeName annotation if it is present on the class
     * being serialized. It will search interfaces and parent classes of the class being serialized 
     * for this annotation.
     *
     */
    public class TypeBeanSerializer extends BeanSerializer {
        public TypeBeanSerializer(BeanSerializerBase src) {
            super(src);
        }

        @Override
        protected void serializeFields(Object bean, JsonGenerator jgen, SerializerProvider provider)
                throws IOException, JsonGenerationException {
            super.serializeFields(bean, jgen, provider);
            addTypeProperty(bean, jgen);
        }

        @Override
        protected void serializeFieldsFiltered(Object bean, JsonGenerator jgen, SerializerProvider provider)
                        throws IOException, JsonGenerationException {
            super.serializeFieldsFiltered(bean, jgen, provider);
            addTypeProperty(bean, jgen);
        }

        private void addTypeProperty(Object bean, JsonGenerator jgen) throws IOException {
            if (noTypeProperty(bean)) {
                String typeName = BridgeUtils.getTypeName(bean.getClass());
                if (typeName != null) {
                    jgen.writeStringField("type", typeName);
                }
            }
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
