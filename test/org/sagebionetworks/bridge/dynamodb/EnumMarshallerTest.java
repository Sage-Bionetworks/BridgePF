package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class EnumMarshallerTest {
    private static final EnumMarshaller MARSHALLER = new EnumMarshaller(TestEnum.class);

    @Test
    public void testMarshall() {
        assertEquals("FOO", MARSHALLER.convert(TestEnum.FOO));
        assertEquals("BAR", MARSHALLER.convert(TestEnum.BAR));
    }

    @Test
    public void testUnmarshall() {
        assertEquals(TestEnum.FOO, MARSHALLER.unconvert("FOO"));
        assertEquals(TestEnum.BAR, MARSHALLER.unconvert("BAR"));
    }

    private static enum TestEnum {
        FOO,
        BAR,
    }
}
