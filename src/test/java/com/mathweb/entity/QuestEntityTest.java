
package com.mathweb.entity;

import com.mathweb.enums.DifficultyLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestEntityTest {

    @Test
    void testNoArgsConstructor() {
        Quest quest = new Quest();

        assertNull(quest.getId());
        assertNull(quest.getTitle());
        assertFalse(quest.getPublished());
        assertEquals(Integer.valueOf(0), quest.getXpReward());
        assertNotNull(quest.getPrerequisites());
        assertTrue(quest.getPrerequisites().isEmpty());
    }

    @Test
    void testIsUnlocked_NoPrerequisites() {
        Quest quest = Quest.builder()
                .prerequisites(new ArrayList<>())
                .build();

        assertTrue(quest.isUnlocked(new ArrayList<>()));
    }

    @Test
    void testIsUnlocked_AllPrerequisitesCompleted() {
        Quest prerequisite1 = Quest.builder().build();
        prerequisite1.setId(1L);

        Quest prerequisite2 = Quest.builder().build();
        prerequisite2.setId(2L);

        List<Quest> prerequisites = new ArrayList<>();
        prerequisites.add(prerequisite1);
        prerequisites.add(prerequisite2);

        Quest quest = Quest.builder()
                .prerequisites(prerequisites)
                .build();

        List<Long> completedQuestIds = new ArrayList<>();
        completedQuestIds.add(1L);
        completedQuestIds.add(2L);

        assertTrue(quest.isUnlocked(completedQuestIds));
    }

    @Test
    void testIsUnlocked_SomePrerequisitesMissing() {
        Quest prerequisite1 = Quest.builder().build();
        prerequisite1.setId(1L);

        Quest prerequisite2 = Quest.builder().build();
        prerequisite2.setId(2L);

        List<Quest> prerequisites = new ArrayList<>();
        prerequisites.add(prerequisite1);
        prerequisites.add(prerequisite2);

        Quest quest = Quest.builder()
                .prerequisites(prerequisites)
                .build();

        List<Long> completedQuestIds = new ArrayList<>();
        completedQuestIds.add(1L);

        assertFalse(quest.isUnlocked(completedQuestIds));
    }
}
