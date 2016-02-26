package org.sagebionetworks.bridge.play.modules;

/** Play module for unit tests. */
public class BridgeTestSpringContextModule extends BridgeSpringContextModule {
    /** {@inheritDoc} */
    @Override
    protected String getSpringXmlFilename() {
        return "test-context.xml";
    }
}
