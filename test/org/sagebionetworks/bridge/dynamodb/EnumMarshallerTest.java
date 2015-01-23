package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class EnumMarshallerTest {
    private static final EnumMarshaller MARSHALLER = new EnumMarshaller();

    @Test
    public void testMarshall() {
        assertEquals("FOO", MARSHALLER.marshall(TestEnum.FOO));
        assertEquals("BAR", MARSHALLER.marshall(TestEnum.BAR));
    }

    @Test
    public void testUnmarshall() {
        assertEquals(TestEnum.FOO, MARSHALLER.unmarshall((Class) TestEnum.class, "FOO"));
        assertEquals(TestEnum.BAR, MARSHALLER.unmarshall((Class) TestEnum.class, "BAR"));
    }

    private static enum TestEnum {
        FOO,
        BAR,
    }
}
