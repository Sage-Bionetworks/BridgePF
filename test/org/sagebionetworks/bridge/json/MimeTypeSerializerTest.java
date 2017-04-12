package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.models.studies.MimeType;

import com.fasterxml.jackson.core.JsonGenerator;

@RunWith(MockitoJUnitRunner.class)
public class MimeTypeSerializerTest {
    
    @Mock
    private JsonGenerator mockJGen;
    
    @Captor
    ArgumentCaptor<String> stringCaptor;
    
    @Test
    public void test() throws Exception {
        new MimeTypeSerializer().serialize(MimeType.TEXT, mockJGen, null);
        verify(mockJGen).writeString(stringCaptor.capture());
        assertEquals("text/plain", stringCaptor.getValue());
    }
}
