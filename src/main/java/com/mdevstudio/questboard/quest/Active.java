package com.mdevstudio.questboard.quest;

import com.mdevstudio.questboard.model.QuestDef;

public record Active(QuestDef def, PlayerQuests.Entry entry) {

    public boolean done() {
        return entry.progress() >= def.amount();
    }

    public boolean ready() {
        return done() && !entry.claimed();
    }

    public float ratio() {
        return Math.min(1f, (float) entry.progress() / def.amount());
    }
}
