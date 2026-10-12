package com.mdevstudio.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mdevstudio.questboard.model.Period;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class QuestboardTest {

    private Questboard plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(Questboard.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsItsFilesOnFirstStart() {
        for (String file : new String[] {"config.yml", "quests.yml", "lang/ru.yml", "lang/en.yml"}) {
            assertTrue(plugin.getDataFolder().toPath().resolve(file).toFile().exists(), file);
        }
    }

    @Test
    void bundledPoolHasQuestsForBothPeriods() {
        assertTrue(plugin.catalog().pool(Period.DAILY).size() >= 3);
        assertTrue(plugin.catalog().pool(Period.WEEKLY).size() >= 2);
    }

    @Test
    void streakRewardsRepeatEveryCycle() {
        int cycle = plugin.catalog().streakCycle();
        assertEquals(plugin.catalog().streakReward(1), plugin.catalog().streakReward(1 + cycle));
    }

    @Test
    void newPlayerGetsTheConfiguredNumberOfQuests() {
        var player = MockBukkit.getMock().addPlayer();
        var state = plugin.book().of(player);
        assertEquals(plugin.settings().schedule().dailyCount(),
                state.entries(Period.DAILY).size());
    }
}
