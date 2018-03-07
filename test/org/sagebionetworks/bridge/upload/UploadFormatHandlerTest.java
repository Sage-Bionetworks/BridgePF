package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class UploadFormatHandlerTest {
    private IosSchemaValidationHandler2 mockV1LegacyHandler;
    private GenericUploadFormatHandler mockV2GenericHandler;
    private UploadFormatHandler uploadFormatHandler;

    @Before
    public void setup() {
        mockV1LegacyHandler = mock(IosSchemaValidationHandler2.class);
        mockV2GenericHandler = mock(GenericUploadFormatHandler.class);

        uploadFormatHandler = new UploadFormatHandler();
        uploadFormatHandler.setV1LegacyHandler(mockV1LegacyHandler);
        uploadFormatHandler.setV2GenericHandler(mockV2GenericHandler);
    }

    private static UploadValidationContext setupContextWithFormat(UploadFormat format) {
        // Make info.json.
        ObjectNode infoJsonNode = BridgeObjectMapper.get().createObjectNode();
        if (format != null) {
            infoJsonNode.put(UploadUtil.FIELD_FORMAT, format.toString());
        }

        // Make context.
        UploadValidationContext context = new UploadValidationContext();
        context.setInfoJsonNode(infoJsonNode);
        return context;
    }

    @Test
    public void defaultFormat() throws Exception {
        UploadValidationContext context = setupContextWithFormat(null);
        uploadFormatHandler.handle(context);
        verify(mockV1LegacyHandler).handle(context);
        verifyZeroInteractions(mockV2GenericHandler);
    }

    @Test
    public void v1Legacy() throws Exception {
        UploadValidationContext context = setupContextWithFormat(UploadFormat.V1_LEGACY);
        uploadFormatHandler.handle(context);
        verify(mockV1LegacyHandler).handle(context);
        verifyZeroInteractions(mockV2GenericHandler);
    }

    @Test
    public void v2Generic() throws Exception {
        UploadValidationContext context = setupContextWithFormat(UploadFormat.V2_GENERIC);
        uploadFormatHandler.handle(context);
        verify(mockV2GenericHandler).handle(context);
        verifyZeroInteractions(mockV1LegacyHandler);
    }
}
