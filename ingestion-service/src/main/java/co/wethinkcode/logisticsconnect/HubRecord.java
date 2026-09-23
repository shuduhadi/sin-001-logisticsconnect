package co.wethinkcode.logisticsconnect;

import java.util.List;

public class HubRecord {
    private final String hubId;
    private final String province;
    private final String sortingCenter;
    private final Boolean active;
    private final List<String> mergedFrom;

    public HubRecord (String hubId, String province, String sortingCenter, Boolean active, List<String> mergedFrom) {
        this.hubId = hubId;
        this.province = province;
        this.sortingCenter = sortingCenter;
        this.active = active;
        this.mergedFrom = mergedFrom;
    }

    public String getHubId() {
        return hubId;
    }

    public String getProvince() {
        return province;
    }

    public String getSortingCenter() {
        return sortingCenter;
    }

    public Boolean getActive() {
        return active;
    }

    public List<String> getMergedFrom() {
        return mergedFrom;
    }

    
}
