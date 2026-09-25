package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DelayStageCacheTest {

    @Test
    void getStageForUnknownHubReturnsEmpty() {
        DelayStageCache cache = new DelayStageCache();
        assertTrue(cache.getStage("H-501").isEmpty());
    }

    @Test
    void updateThenGetReturnsTheStoredStage() {
        DelayStageCache cache = new DelayStageCache();
        cache.updateStage("H-501", 3);
        assertEquals(Optional.of(3), cache.getStage("H-501"));
    }

    @Test
    void lookupIsCaseInsensitiveAndTrimsWhitespace() {
        DelayStageCache cache = new DelayStageCache();
        cache.updateStage(" h-501 ", 3);
        assertEquals(Optional.of(3), cache.getStage("H-501"));
    }

    @Test
    void updatingAgainOverwritesThePreviousStage() {
        DelayStageCache cache = new DelayStageCache();
        cache.updateStage("H-501", 3);
        cache.updateStage("H-501", 5);
        assertEquals(Optional.of(5), cache.getStage("H-501"));
    }
}