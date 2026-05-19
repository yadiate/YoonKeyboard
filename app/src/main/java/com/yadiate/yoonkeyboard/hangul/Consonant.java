package com.yadiate.yoonkeyboard.hangul;

public enum Consonant {
    GIYEOK("ㄱ", 0, 1),
    SSANG_GIYEOK("ㄲ", 1, 2),
    NIEUN("ㄴ", 2, 4),
    DIGEUT("ㄷ", 3, 7),
    SSANG_DIGEUT("ㄸ", 4, 0),
    RIEUL("ㄹ", 5, 8),
    MIEUM("ㅁ", 6, 16),
    BIEUP("ㅂ", 7, 17),
    SSANG_BIEUP("ㅃ", 8, 0),
    SIOT("ㅅ", 9, 19),
    SSANG_SIOT("ㅆ", 10, 20),
    IEUNG("ㅇ", 11, 21),
    JIEUT("ㅈ", 12, 22),
    SSANG_JIEUT("ㅉ", 13, 0),
    CHIEUT("ㅊ", 14, 23),
    KIEUK("ㅋ", 15, 24),
    TIEUT("ㅌ", 16, 25),
    PIEUP("ㅍ", 17, 26),
    HIEUT("ㅎ", 18, 27);

    private final String label;
    private final int leadingIndex;
    private final int finalIndex;

    Consonant(String label, int leadingIndex, int finalIndex) {
        this.label = label;
        this.leadingIndex = leadingIndex;
        this.finalIndex = finalIndex;
    }

    public String label() {
        return label;
    }

    public int leadingIndex() {
        return leadingIndex;
    }

    public int finalIndex() {
        return finalIndex;
    }

    public boolean canBeFinal() {
        return finalIndex > 0;
    }

    public Consonant doubleTapVariant() {
        switch (this) {
            case GIYEOK:
                return SSANG_GIYEOK;
            case DIGEUT:
                return SSANG_DIGEUT;
            case BIEUP:
                return SSANG_BIEUP;
            case SIOT:
                return SSANG_SIOT;
            case JIEUT:
                return SSANG_JIEUT;
            default:
                return null;
        }
    }

    public static Consonant fromFinalIndex(int finalIndex) {
        for (Consonant consonant : values()) {
            if (consonant.finalIndex == finalIndex) {
                return consonant;
            }
        }
        return null;
    }

    public static Consonant fromLeadingIndex(int leadingIndex) {
        for (Consonant consonant : values()) {
            if (consonant.leadingIndex == leadingIndex) {
                return consonant;
            }
        }
        return null;
    }
}
