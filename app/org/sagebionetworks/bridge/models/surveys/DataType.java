package org.sagebionetworks.bridge.models.surveys;

public enum DataType {
    DURATION(String.class),
    STRING(String.class),
    INTEGER(Integer.class),
    DECIMAL(Double.class),
    BOOLEAN(Boolean.class),
    DATE(String.class),
    TIME(String.class),
    DATETIME(String.class);
    
    private Class<?> castClass;
    
    private DataType(Class<?> castClass) {
        this.castClass = castClass;
    }
    
    public Class<?> getCastClass() {
        return castClass;
    }
}
