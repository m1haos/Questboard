package com.mdevstudio.questboard.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mdevstudio.questboard.model.Period;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;

class PlayerQuestsTest {

    @Test
    void stateSurvivesSerialisation() throws InvalidConfigurationException {
        PlayerQuests state = new PlayerQuests();
        state.setKey(Period.DAILY, "2026-10-12");
        state.setKey(Period.WEEKLY, "2026-10-12");
        state.entries(Period.DAILY).add(new PlayerQuests.Entry("stone-cutter", 40, false));
        state.entries(Period.WEEKLY).add(new PlayerQuests.Entry("marathon", 15000, true));
        state.setStreak(4, "2026-10-12");
        state.track("stone-cutter");

        PlayerQuests copy = PlayerQuests.parse(state.serialize());

        assertEquals("2026-10-12", copy.key(Period.DAILY));
        assertEquals(40, copy.find("stone-cutter").progress());
        assertTrue(copy.find("marathon").claimed());
        assertEquals(4, copy.streak());
        assertEquals("stone-cutter", copy.tracked());
    }

    @Test
    void stoppingTheTrackerIsRemembered() throws InvalidConfigurationException {
        PlayerQuests state = new PlayerQuests();
        state.track("farmer");
        state.stopTracking();

        PlayerQuests copy = PlayerQuests.parse(state.serialize());

        assertTrue(copy.trackerOff());
        assertNull(copy.tracked());
    }
}
