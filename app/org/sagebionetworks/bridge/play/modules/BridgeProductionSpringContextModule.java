package org.sagebionetworks.bridge.play.modules;

/** Play module for launching actual Bridge servers. */
public class BridgeProductionSpringContextModule extends BridgeSpringContextModule {
    /** {@inheritDoc} */
    @Override
    protected String getSpringXmlFilename() {
        return "application-context.xml";
    }
}
