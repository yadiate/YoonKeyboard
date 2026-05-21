package com.yadiate.yoonkeyboard.hangul;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CalibrationPlanner {
    public static final int TARGET_REPEATS = 10;
    public static final int NO_FINAL_REPEATS = 5;
    public static final int INTERFERENCE_REPEATS = 5;

    private CalibrationPlanner() {
    }

    public static Plan build(String input) {
        char target = firstHangulSyllable(input);
        if (target == 0) {
            throw new IllegalArgumentException("Hangul syllable is required");
        }

        List<Step> steps = new ArrayList<>();
        addStep(steps, target, TARGET_REPEATS, "목표 글자");

        char noFinal = withoutFinal(target);
        if (noFinal != target) {
            addStep(steps, noFinal, NO_FINAL_REPEATS, "받침 제거 비교");
        }

        int leading = GestureCalibration.leadingIndex(target);
        int targetVowel = GestureCalibration.vowelIndex(target);
        int[] candidates = interferenceVowels(targetVowel);
        int interferenceAdded = 0;
        for (int candidateVowel : candidates) {
            char candidate = compose(leading, candidateVowel, 0);
            if (candidate == target || candidate == noFinal || containsText(steps, candidate)) {
                continue;
            }
            addStep(steps, candidate, INTERFERENCE_REPEATS, "간섭 글자 비교");
            interferenceAdded++;
            if (interferenceAdded >= 2) {
                break;
            }
        }

        return new Plan(target, steps);
    }

    public static char firstHangulSyllable(String input) {
        if (input == null) {
            return 0;
        }
        for (int i = 0; i < input.length(); i++) {
            char value = input.charAt(i);
            if (GestureCalibration.isHangulSyllable(value)) {
                return value;
            }
        }
        return 0;
    }

    public static char withoutFinal(char syllable) {
        if (!GestureCalibration.isHangulSyllable(syllable)) {
            return syllable;
        }
        int offset = syllable - 0xAC00;
        return (char) (0xAC00 + (offset / 28) * 28);
    }

    private static void addStep(List<Step> steps, char text, int repeats, String purpose) {
        steps.add(new Step(String.valueOf(text), repeats, purpose));
    }

    private static boolean containsText(List<Step> steps, char value) {
        String text = String.valueOf(value);
        for (Step step : steps) {
            if (step.text.equals(text)) {
                return true;
            }
        }
        return false;
    }

    private static char compose(int leadingIndex, int vowelIndex, int finalIndex) {
        return (char) (0xAC00 + ((leadingIndex * 21 + vowelIndex) * 28) + finalIndex);
    }

    private static int[] interferenceVowels(int vowel) {
        switch (vowel) {
            case HangulComposer.V_A:
                return new int[]{HangulComposer.V_YA, HangulComposer.V_EU};
            case HangulComposer.V_AE:
                return new int[]{HangulComposer.V_A, HangulComposer.V_YA};
            case HangulComposer.V_YA:
                return new int[]{HangulComposer.V_A, HangulComposer.V_AE};
            case HangulComposer.V_YAE:
                return new int[]{HangulComposer.V_YA, HangulComposer.V_AE};
            case HangulComposer.V_EO:
                return new int[]{HangulComposer.V_YEO, HangulComposer.V_EU};
            case HangulComposer.V_E:
                return new int[]{HangulComposer.V_EO, HangulComposer.V_YEO};
            case HangulComposer.V_YEO:
                return new int[]{HangulComposer.V_EO, HangulComposer.V_O};
            case HangulComposer.V_YE:
                return new int[]{HangulComposer.V_YEO, HangulComposer.V_E};
            case HangulComposer.V_O:
                return new int[]{HangulComposer.V_YO, HangulComposer.V_I};
            case HangulComposer.V_WA:
                return new int[]{HangulComposer.V_O, HangulComposer.V_A};
            case HangulComposer.V_WAE:
                return new int[]{HangulComposer.V_WA, HangulComposer.V_AE};
            case HangulComposer.V_OE:
                return new int[]{HangulComposer.V_O, HangulComposer.V_I};
            case HangulComposer.V_YO:
                return new int[]{HangulComposer.V_O, HangulComposer.V_I};
            case HangulComposer.V_U:
                return new int[]{HangulComposer.V_YU, HangulComposer.V_I};
            case HangulComposer.V_WEO:
                return new int[]{HangulComposer.V_U, HangulComposer.V_EO};
            case HangulComposer.V_WE:
                return new int[]{HangulComposer.V_WEO, HangulComposer.V_E};
            case HangulComposer.V_WI:
                return new int[]{HangulComposer.V_U, HangulComposer.V_I};
            case HangulComposer.V_YU:
                return new int[]{HangulComposer.V_U, HangulComposer.V_I};
            case HangulComposer.V_EU:
                return new int[]{HangulComposer.V_EO, HangulComposer.V_A};
            case HangulComposer.V_YI:
                return new int[]{HangulComposer.V_EU, HangulComposer.V_I};
            case HangulComposer.V_I:
                return new int[]{HangulComposer.V_O, HangulComposer.V_U};
            default:
                return new int[0];
        }
    }

    public static final class Plan {
        public final char target;
        public final List<Step> steps;

        private Plan(char target, List<Step> steps) {
            this.target = target;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public int totalCharacters() {
            int total = 0;
            for (Step step : steps) {
                total += step.targetText().length();
            }
            return total;
        }

        public int completedCharactersBeforeStep(int stepIndex) {
            int total = 0;
            for (int i = 0; i < stepIndex && i < steps.size(); i++) {
                total += steps.get(i).targetText().length();
            }
            return total;
        }

        public String summary() {
            StringBuilder builder = new StringBuilder();
            for (Step step : steps) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(step.text).append(" ").append(step.repeats).append("번");
            }
            return builder.toString();
        }
    }

    public static final class Step {
        public final String text;
        public final int repeats;
        public final String purpose;

        private Step(String text, int repeats, String purpose) {
            this.text = text;
            this.repeats = repeats;
            this.purpose = purpose;
        }

        public String targetText() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < repeats; i++) {
                builder.append(text);
            }
            return builder.toString();
        }
    }
}
