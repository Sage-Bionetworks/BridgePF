package models;

import java.util.List;

public class IdHolder {

    private List<String> ids;
    
    public IdHolder(List<String> ids) {
        this.ids = ids;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
