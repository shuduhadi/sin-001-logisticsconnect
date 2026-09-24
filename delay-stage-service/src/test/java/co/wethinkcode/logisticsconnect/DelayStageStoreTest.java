package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests DelayStageStore's in-memory state directly - no HTTP involved.
 */
public class DelayStageStoreTest {

    @Test
    void getStageForUnknownHubReturnsEmpty() {
        DelayStageStore store = new DelayStageStore();
        assertTrue(store.getStage("H-501").isEmpty());
    }

    @Test
    void setThenGetReturnsTheStoredStage() {
        DelayStageStore store = new DelayStageStore();
        store.setStage("H-501", 3);
        assertEquals(Optional.of(3), store.getStage("H-501"));
    }

    @Test
    void lookupIsCaseInsensitiveAndTrimsWhitespace() {
        DelayStageStore store = new DelayStageStore();
        store.setStage(" h-501 ", 3);
        assertEquals(Optional.of(3), store.getStage("H-501"));
    }

    @Test
    void settingAgainOverwritesThePreviousStage() {
        DelayStageStore store = new DelayStageStore();
        store.setStage("H-501", 3);
        store.setStage("H-501", 5);
        assertEquals(Optional.of(5), store.getStage("H-501"));
    }

    @Test
    void differentHubsAreTrackedIndependently() {
        DelayStageStore store = new DelayStageStore();
        store.setStage("H-501", 3);
        store.setStage("H-502", 7);
        assertEquals(Optional.of(3), store.getStage("H-501"));
        assertEquals(Optional.of(7), store.getStage("H-502"));
    }
}