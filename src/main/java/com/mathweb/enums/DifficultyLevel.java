package com.mathweb.enums;

public enum DifficultyLevel {
    LEVEL_1_BEGINNER(1, "Beginner"),
    LEVEL_2_ELEMENTARY(2, "Elementary"),
    LEVEL_3_INTERMEDIATE(3, "Intermediate"),
    LEVEL_4_UPPER_INTERMEDIATE(4, "Upper Intermediate"),
    LEVEL_5_ADVANCED(5, "Advanced"),
    LEVEL_6_EXPERT(6, "Expert"),
    LEVEL_7_MASTER(7, "Master");

    private final int levelNumber;
    private final String displayName;

    DifficultyLevel(int levelNumber, String displayName) {
        this.levelNumber = levelNumber;
        this.displayName = displayName;
    }

    public int getLevelNumber() { return levelNumber; }
    public String getDisplayName() { return displayName; }
}