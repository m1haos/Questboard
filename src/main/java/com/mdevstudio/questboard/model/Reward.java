package com.mdevstudio.questboard.model;

import org.bukkit.Material;

public sealed interface Reward {

    record Item(Material material, int amount) implements Reward {
    }

    record Experience(int points) implements Reward {
    }

    record Command(String line, String label) implements Reward {
    }
}
