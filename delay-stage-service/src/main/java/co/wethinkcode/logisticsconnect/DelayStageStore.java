package co.wethinkcode.logisticsconnect;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of the current Transit Delay Stage (0-8) per hub.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}, since Javalin handles
 * requests concurrently. Hub IDs are normalized (trimmed, uppercased)
 * before lookup/storage so {@code "h-501"} and {@code "H-501"} refer to
 * the same entry.
 */
public class DelayStageStore {

    private final Map<String, Integer> stagesByHubId = new ConcurrentHashMap<>();

    /**
     * Records the current delay stage for a hub, overwriting any
     * previously stored value.
     *
     * @param hubId hub identifier; normalized before storage
     * @param stage delay stage, expected to already be validated (0-8)
     *              by the caller - this method does not re-validate
     */
    public void setStage(String hubId, int stage) {
        stagesByHubId.put(normalize(hubId), stage);
    }

    /**
     * Looks up the current delay stage for a hub.
     *
     * @param hubId hub identifier; normalized before lookup
     * @return the stored stage, or {@link Optional#empty()} if no stage
     *         has been recorded for this hub yet
     */
    public Optional<Integer> getStage(String hubId) {
        return Optional.ofNullable(stagesByHubId.get(normalize(hubId)));
    }

    private static String normalize(String hubId) {
        return hubId == null ? "" : hubId.trim().toUpperCase();
    }
}