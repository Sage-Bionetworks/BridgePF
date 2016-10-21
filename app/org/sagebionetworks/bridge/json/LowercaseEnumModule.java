package org.sagebionetworks.bridge.json;

import org.sagebionetworks.bridge.models.studies.MimeType;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.Deserializers.Base;
import com.fasterxml.jackson.databind.module.SimpleModule;

@SuppressWarnings("serial")
class LowercaseEnumModule extends SimpleModule {

    public LowercaseEnumModule() {
        super("bridge-enum-module", new Version(1, 0, 0, "", "org.sagebionetworks.bridge", "sdk"));
        addSerializer(Enum.class, new LowercaseEnumSerializer());
    }
    
    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        Base deser = new Deserializers.Base() {
            @SuppressWarnings("unchecked")
            @Override
            public JsonDeserializer<?> findEnumDeserializer(Class<?> type, DeserializationConfig config,
                    BeanDescription beanDesc) throws JsonMappingException {
                if (type == MimeType.class) {
                    return null;
                }
                return new LowercaseEnumDeserializer((Class<Enum<?>>) type);
            }
        };
        context.addDeserializers(deser);
    };
    
}
