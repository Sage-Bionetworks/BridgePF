package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.springframework.core.io.Resource;

@BridgeTypeName("Tracker")
public class Tracker implements BridgeEntity {

    private String identifier;
    private String name;
    private String type;
    private Resource schemaFile;
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public Resource getSchemaFile() {
        return schemaFile;
    }
    public void setSchemaFile(Resource schemaFile) {
        this.schemaFile = schemaFile;
    }
}
