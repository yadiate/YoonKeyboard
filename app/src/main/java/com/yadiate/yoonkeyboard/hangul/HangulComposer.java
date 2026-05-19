package com.yadiate.yoonkeyboard.hangul;

import java.util.HashMap;
import java.util.Map;

public class HangulComposer {
    public static final int V_A = 0;
    public static final int V_AE = 1;
    public static final int V_YA = 2;
    public static final int V_YAE = 3;
    public static final int V_EO = 4;
    public static final int V_E = 5;
    public static final int V_YEO = 6;
    public static final int V_YE = 7;
    public static final int V_O = 8;
    public static final int V_WA = 9;
    public static final int V_WAE = 10;
    public static final int V_OE = 11;
    public static final int V_YO = 12;
    public static final int V_U = 13;
    public static final int V_WEO = 14;
    public static final int V_WE = 15;
    public static final int V_WI = 16;
    public static final int V_YU = 17;
    public static final int V_EU = 18;
    public static final int V_YI = 19;
    public static final int V_I = 20;

    private static final char[] COMPAT_LEADS = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };
    private static final char[] COMPAT_VOWELS = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };
    private static final Map<Integer, Integer> VOWEL_COMBINATIONS = new HashMap<>();
    private static final Map<Integer, Integer> FINAL_COMBINATIONS = new HashMap<>();
    private static final Map<Integer, FinalSplit> FINAL_SPLITS = new HashMap<>();

    static {
        putVowel(V_O, V_A, V_WA);
        putVowel(V_O, V_AE, V_WAE);
        putVowel(V_O, V_I, V_OE);
        putVowel(V_U, V_EO, V_WEO);
        putVowel(V_U, V_E, V_WE);
        putVowel(V_U, V_I, V_WI);
        putVowel(V_EU, V_I, V_YI);

        putFinal(1, 19, 3);
        putFinal(4, 22, 5);
        putFinal(4, 27, 6);
        putFinal(8, 1, 9);
        putFinal(8, 16, 10);
        putFinal(8, 17, 11);
        putFinal(8, 19, 12);
        putFinal(8, 25, 13);
        putFinal(8, 26, 14);
        putFinal(8, 27, 15);
        putFinal(17, 19, 18);

        putSplit(3, 1, Consonant.SIOT);
        putSplit(5, 4, Consonant.JIEUT);
        putSplit(6, 4, Consonant.HIEUT);
        putSplit(9, 8, Consonant.GIYEOK);
        putSplit(10, 8, Consonant.MIEUM);
        putSplit(11, 8, Consonant.BIEUP);
        putSplit(12, 8, Consonant.SIOT);
        putSplit(13, 8, Consonant.TIEUT);
        putSplit(14, 8, Consonant.PIEUP);
        putSplit(15, 8, Consonant.HIEUT);
        putSplit(18, 17, Consonant.SIOT);
    }

    private int lead = -1;
    private int vowel = -1;
    private int tail = 0;

    public String inputConsonant(Consonant consonant) {
        if (lead < 0) {
            lead = consonant.leadingIndex();
            return "";
        }
        if (vowel < 0) {
            String committed = commit();
            lead = consonant.leadingIndex();
            return committed;
        }
        if (tail == 0 && consonant.canBeFinal()) {
            tail = consonant.finalIndex();
            return "";
        }
        int combinedTail = combineFinal(tail, consonant.finalIndex());
        if (combinedTail > 0) {
            tail = combinedTail;
            return "";
        }
        String committed = commit();
        lead = consonant.leadingIndex();
        return committed;
    }

    public String inputVowel(int nextVowel) {
        if (lead < 0) {
            lead = Consonant.IEUNG.leadingIndex();
            vowel = nextVowel;
            return "";
        }
        if (vowel < 0) {
            vowel = nextVowel;
            return "";
        }
        if (tail > 0) {
            FinalSplit split = FINAL_SPLITS.get(tail);
            if (split != null) {
                int previousLead = lead;
                int previousVowel = vowel;
                lead = split.nextLeading.leadingIndex();
                vowel = nextVowel;
                tail = 0;
                return compose(previousLead, previousVowel, split.remainingFinal);
            }
            Consonant nextLead = Consonant.fromFinalIndex(tail);
            if (nextLead != null) {
                int previousLead = lead;
                int previousVowel = vowel;
                lead = nextLead.leadingIndex();
                vowel = nextVowel;
                tail = 0;
                return compose(previousLead, previousVowel, 0);
            }
            String committed = commit();
            lead = Consonant.IEUNG.leadingIndex();
            vowel = nextVowel;
            return committed;
        }
        int combined = combineVowel(vowel, nextVowel);
        if (combined >= 0) {
            vowel = combined;
            return "";
        }
        String committed = commit();
        lead = Consonant.IEUNG.leadingIndex();
        vowel = nextVowel;
        return committed;
    }

    public boolean backspace() {
        if (tail > 0) {
            FinalSplit split = FINAL_SPLITS.get(tail);
            tail = split == null ? 0 : split.remainingFinal;
            return true;
        }
        if (vowel >= 0) {
            vowel = -1;
            return true;
        }
        if (lead >= 0) {
            lead = -1;
            return true;
        }
        return false;
    }

    public boolean replaceSingleConsonant(Consonant base, Consonant replacement) {
        if (base == null || replacement == null) {
            return false;
        }
        if (lead == base.leadingIndex() && vowel < 0 && tail == 0) {
            lead = replacement.leadingIndex();
            return true;
        }
        if (vowel >= 0 && tail == base.finalIndex() && replacement.finalIndex() > 0) {
            tail = replacement.finalIndex();
            return true;
        }
        return false;
    }

    public String promoteCombinedFinalToDoubleInitial(Consonant base, Consonant replacement) {
        if (base == null || replacement == null || lead < 0 || vowel < 0 || tail <= 0) {
            return null;
        }
        FinalSplit split = FINAL_SPLITS.get(tail);
        if (split == null || split.nextLeading != base) {
            return null;
        }
        int previousLead = lead;
        int previousVowel = vowel;
        int previousTail = split.remainingFinal;
        lead = replacement.leadingIndex();
        vowel = -1;
        tail = 0;
        return compose(previousLead, previousVowel, previousTail);
    }

    public boolean hasComposingText() {
        return lead >= 0 || vowel >= 0 || tail > 0;
    }

    public String getComposingText() {
        if (lead < 0) {
            return "";
        }
        if (vowel < 0) {
            return String.valueOf(COMPAT_LEADS[lead]);
        }
        return compose(lead, vowel, tail);
    }

    public String commit() {
        String text = getComposingText();
        reset();
        return text;
    }

    public static String previewSyllable(Consonant consonant, int vowel) {
        if (consonant == null || vowel < 0 || vowel >= COMPAT_VOWELS.length) {
            return "";
        }
        return compose(consonant.leadingIndex(), vowel, 0);
    }

    public static String compatVowel(int vowel) {
        if (vowel < 0 || vowel >= COMPAT_VOWELS.length) {
            return "";
        }
        return String.valueOf(COMPAT_VOWELS[vowel]);
    }

    public void reset() {
        lead = -1;
        vowel = -1;
        tail = 0;
    }

    private static String compose(int lead, int vowel, int tail) {
        int codePoint = 0xAC00 + ((lead * 21) + vowel) * 28 + tail;
        return String.valueOf((char) codePoint);
    }

    private static int combineVowel(int first, int second) {
        Integer combined = VOWEL_COMBINATIONS.get(key(first, second));
        return combined == null ? -1 : combined;
    }

    private static int combineFinal(int first, int second) {
        if (first <= 0 || second <= 0) {
            return 0;
        }
        Integer combined = FINAL_COMBINATIONS.get(key(first, second));
        return combined == null ? 0 : combined;
    }

    private static void putVowel(int first, int second, int result) {
        VOWEL_COMBINATIONS.put(key(first, second), result);
    }

    private static void putFinal(int first, int second, int result) {
        FINAL_COMBINATIONS.put(key(first, second), result);
    }

    private static void putSplit(int combinedFinal, int remainingFinal, Consonant nextLeading) {
        FINAL_SPLITS.put(combinedFinal, new FinalSplit(remainingFinal, nextLeading));
    }

    private static int key(int first, int second) {
        return first * 100 + second;
    }

    private static class FinalSplit {
        final int remainingFinal;
        final Consonant nextLeading;

        FinalSplit(int remainingFinal, Consonant nextLeading) {
            this.remainingFinal = remainingFinal;
            this.nextLeading = nextLeading;
        }
    }
}
