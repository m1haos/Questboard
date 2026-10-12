package com.mdevstudio.questboard.model;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;

public record QuestDef(
        String id,
        Period period,
        Goal goal,
        Set<String> targets,
        int amount,
        Material icon,
        int weight,
        String title,
        String description,
        List<Reward> rewards) {

    public boolean accepts(String key) {
        return targets.isEmpty() || targets.contains(key);
    }
}
