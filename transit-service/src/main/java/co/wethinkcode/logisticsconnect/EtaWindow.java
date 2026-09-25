package co.wethinkcode.logisticsconnect;

import java.time.Instant;

/**
 * An estimated arrival window: earliest and latest expected arrival.
 */
public record EtaWindow(Instant earliest, Instant latest) {
}