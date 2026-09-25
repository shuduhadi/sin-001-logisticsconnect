package co.wethinkcode.logisticsconnect;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Calculates an estimated arrival window from a delay stage.
 *
 * <p>No distance or routing data exists anywhere in this pipeline (no
 * province-to-province distances, no hub coordinates), so this is
 * deliberately simple rather than physically accurate:
 *
 * <pre>
 *   earliest = now + BASE_TRANSIT_HOURS + (delayStage * HOURS_PER_DELAY_STAGE)
 *   latest   = earliest + WINDOW_BUFFER_HOURS
 * </pre>
 *
 * All three constants are placeholders chosen to be easy to reason about
 * and easy to change, not derived from any real transit data.
 */
public class EtaCalculator {

    static final int BASE_TRANSIT_HOURS = 24;
    static final int HOURS_PER_DELAY_STAGE = 6;
    static final int WINDOW_BUFFER_HOURS = 4;

    /**
     * @param delayStage current delay stage (0-8)
     * @param clock      source of "now" - injected so callers/tests can
     *                   use a fixed clock instead of the real system clock
     * @return the estimated arrival window
     */
    static EtaWindow calculateEta(int delayStage, Clock clock) {
        Instant now = Instant.now(clock);
        Instant earliest = now.plus(BASE_TRANSIT_HOURS + (long) delayStage * HOURS_PER_DELAY_STAGE, ChronoUnit.HOURS);
        Instant latest = earliest.plus(WINDOW_BUFFER_HOURS, ChronoUnit.HOURS);
        return new EtaWindow(earliest, latest);
    }
}