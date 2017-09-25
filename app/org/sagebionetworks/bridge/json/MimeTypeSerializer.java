package org.sagebionetworks.bridge.json;

import java.io.IOException;

import org.sagebionetworks.bridge.models.studies.MimeType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

@SuppressWarnings({"serial"})
public class MimeTypeSerializer extends StdSerializer<MimeType> {

    public MimeTypeSerializer() {
        super(MimeType.class, false);
    }
    
    @Override
    public void serialize(MimeType type, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(type.toString());
    }

}
