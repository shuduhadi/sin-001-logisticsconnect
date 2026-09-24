package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * Local copy of hub record shape returned by ingestion-service's
 * GET/hubs. hub-service and ingestion-service are independent Maven
 * modules with no shared library so this mirrors that JSON contract
 * rather than reusing ingestion-service's class directly.
 */

public class HubRecord {

    private String hubId;
    private String province;
    private String sortingCenter;
    private Boolean active;
    private List<String> mergedFrom;

    public HubRecord() {
        // no-arg constructor required for Jackson deserilization
    }

    public String getHubId() {return hubId;}
    public void setHubId(String hubId) {this.hubId = hubId;}

    public String getProvince() {return province;}
    public void setProvince(String province) {this.province = province;}

    public String getSortingCenter() { return sortingCenter;}
    public void setSortingCenter(String sortingCenter) {this.sortingCenter = sortingCenter;}

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) {this.active = active;}

    public List<String> getMergedFrom() { return mergedFrom;}
    public void setMergedFrom(List<String> mergedFrom) { this.mergedFrom = mergedFrom;}

}
