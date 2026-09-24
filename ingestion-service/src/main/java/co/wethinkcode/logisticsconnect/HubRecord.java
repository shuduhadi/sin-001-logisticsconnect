package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * A single cleaned, deduplicated hub record, as served by
 * {@code GET /hubs}.
 *
 * <p>Where the source data had multiple rows describing the same real-world
 * hub under different IDs, this represents the one canonical record chosen
 * for that group; {@link #getMergedFrom()} lists the other raw IDs that were
 * folded into it, so the resolution is auditable rather than silent.
 */

public class HubRecord {
    private final String hubId;
    private final String province;
    private final String sortingCenter;
    private final Boolean active;
    private final List<String> mergedFrom;

 /**
     * @param hubId         canonical, normalized hub ID (e.g. {@code "H-500"})
     * @param province      canonical province name, or {@code null} if unknown
     * @param sortingCenter normalized sorting center name
     * @param active        resolved active flag, or {@code null} if unknown
     * @param mergedFrom    raw hub IDs of other rows folded into this record
     *                      as duplicates (empty if this record had no duplicates)
**/

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
