package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.Map;

import com.google.common.collect.Maps;

public abstract class Constraints {

    public static Map<String,Class<? extends Constraints>> CLASSES = Maps.newHashMap();
    static {
        CLASSES.put("boolean", BooleanConstraints.class);
        CLASSES.put("integer", IntegerConstraints.class);
        CLASSES.put("decimal", DecimalConstraints.class);
        CLASSES.put("string", StringConstraints.class);
        CLASSES.put("datetime", DateTimeConstraints.class);
        CLASSES.put("date", DateConstraints.class);
        CLASSES.put("time", TimeConstraints.class);
        CLASSES.put("duration", DurationConstraints.class);
    }
    
    protected boolean allowMultiple;
    
    public abstract String getDataType();
    
    public abstract EnumSet<UIHint> getSuportedHints();
    
    public void setDataType(String type) {
        // noop. Jackson wants this to convert JSON constraint into an object.
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }
    public void setAllowMultiple(boolean allowMultiple) {
        this.allowMultiple = allowMultiple;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (allowMultiple ? 1231 : 1237);
        result = prime * result + getDataType().hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Constraints other = (Constraints) obj;
        if (allowMultiple != other.allowMultiple)
            return false;
        if (!getDataType().equals(other.getDataType()))
            return false;
        return true;
    }
}
