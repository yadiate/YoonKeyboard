import android.util.DisplayMetrics;

import com.yadiate.yoonkeyboard.hangul.CalibrationPlanner;
import com.yadiate.yoonkeyboard.hangul.Consonant;
import com.yadiate.yoonkeyboard.hangul.GestureVowelMapper;
import com.yadiate.yoonkeyboard.hangul.HangulComposer;

import java.util.ArrayList;
import java.util.List;

public final class ImeSimulationRunner {
    private static final float GESTURE_TEST_PX_PER_MM = 8f;

    private interface Scenario {
        void run(Engine engine);
    }

    private static final class Case {
        final String name;
        final Scenario scenario;
        final String expected;

        Case(String name, Scenario scenario, String expected) {
            this.name = name;
            this.scenario = scenario;
            this.expected = expected;
        }
    }

    private static final class GestureCase {
        final String name;
        final List<GestureVowelMapper.Point> points;
        final int expected;

        GestureCase(String name, List<GestureVowelMapper.Point> points, int expected) {
            this.name = name;
            this.points = points;
            this.expected = expected;
        }
    }

    private static final class FakeEditor {
        private final boolean trace;
        private final StringBuilder committed = new StringBuilder();
        private String composing = "";

        FakeEditor(boolean trace) {
            this.trace = trace;
        }

        void setComposingText(String text) {
            composing = text == null ? "" : text;
            log("setComposingText", text);
        }

        void finishComposingText() {
            String finished = composing;
            committed.append(composing);
            composing = "";
            log("finishComposingText", finished);
        }

        void commitText(String text) {
            composing = "";
            committed.append(text);
            log("commitText", text);
        }

        void deleteSurroundingText(int beforeLength) {
            int remaining = beforeLength;
            if (!composing.isEmpty()) {
                int delete = Math.min(remaining, composing.length());
                composing = composing.substring(0, composing.length() - delete);
                remaining -= delete;
            }
            while (remaining > 0 && committed.length() > 0) {
                committed.deleteCharAt(committed.length() - 1);
                remaining--;
            }
            log("deleteSurroundingText", String.valueOf(beforeLength));
        }

        String text() {
            return committed.toString() + composing;
        }

        private void log(String op, String value) {
            if (trace) {
                System.out.println("    " + op + "(" + escape(value == null ? "" : value)
                        + ") => " + escape(text()));
            }
        }
    }

    private static final class Engine {
        private final HangulComposer composer = new HangulComposer();
        private final FakeEditor editor;
        private Consonant lastConsonantTap;
        private long lastConsonantTapTimeMs;
        private boolean immediateConsonantPlaceholderVisible;
        private Consonant recentlySettledStandaloneConsonant;
        private long recentlySettledStandaloneConsonantTimeMs;
        private long now;

        Engine(boolean trace) {
            editor = new FakeEditor(trace);
        }

        void touch(Consonant consonant) {
            tick();
            handleHangulConsonantTouchDown(consonant);
        }

        void releaseFallbackTap(Consonant consonant) {
            tick();
            handleHangulConsonantTap(consonant);
        }

        void stroke(Consonant consonant, int vowel) {
            touch(consonant);
            gesture(consonant, vowel, true);
        }

        void gesture(Consonant consonant, int vowel, boolean keyHandledOnTouchDown) {
            tick();
            if (!keyHandledOnTouchDown) {
                boolean replacedWithDouble = replaceRecentConsonantWithDouble(consonant, now);
                if (!replacedWithDouble) {
                    commitTextIfNeeded(composer.inputConsonant(consonant));
                }
            }
            resetDoubleConsonantTapState();
            restoreSettledStandaloneForVowelIfNeeded(now);
            commitTextIfNeeded(composer.inputVowel(vowel));
            refreshComposingText();
        }

        void vowel(int vowel) {
            tick();
            resetDoubleConsonantTapState();
            restoreSettledStandaloneForVowelIfNeeded(now);
            commitTextIfNeeded(composer.inputVowel(vowel));
            refreshComposingText();
        }

        void forceSettleStandalone() {
            tick();
            settleImmediateConsonantPlaceholderIfStandalone();
        }

        void waitMs(long elapsedMs) {
            now += Math.max(0L, elapsedMs);
        }

        void finish() {
            tick();
            commitComposingText();
        }

        String text() {
            return editor.text();
        }

        private void handleHangulConsonantTap(Consonant consonant) {
            if (replaceRecentConsonantWithDouble(consonant, now)) {
                resetDoubleConsonantTapState();
                refreshComposingText();
                return;
            }
            settleImmediateConsonantPlaceholderIfStandalone();
            clearSettledStandaloneRecoveryIfNot(consonant, now);
            commitTextIfNeeded(composer.inputConsonant(consonant));
            refreshComposingText();
            lastConsonantTap = consonant;
            lastConsonantTapTimeMs = now;
        }

        private void handleHangulConsonantTouchDown(Consonant consonant) {
            if (replaceRecentConsonantWithDouble(consonant, now)) {
                resetDoubleConsonantTapState();
                if (composer.isLeadingOnly()) {
                    showImmediateConsonantPlaceholder();
                } else {
                    refreshComposingText();
                }
                return;
            }
            settleImmediateConsonantPlaceholderIfStandalone();
            clearSettledStandaloneRecoveryIfNot(consonant, now);
            commitTextIfNeeded(composer.inputConsonant(consonant));
            if (composer.isLeadingOnly()) {
                showImmediateConsonantPlaceholder();
            } else {
                refreshComposingText();
            }
            lastConsonantTap = consonant;
            lastConsonantTapTimeMs = now;
        }

        private boolean replaceRecentConsonantWithDouble(Consonant consonant, long eventTimeMs) {
            Consonant replacement = consonant == null ? null : consonant.doubleTapVariant();
            if (replacement == null) {
                return false;
            }
            boolean withinTapWindow = withinDoubleConsonantTapWindow(consonant, eventTimeMs);
            boolean withinSettledWindow = withinSettledStandaloneDoubleWindow(consonant, eventTimeMs);
            if (!withinTapWindow && !withinSettledWindow) {
                return false;
            }
            if (withinTapWindow && composer.replaceLeadingConsonant(consonant, replacement)) {
                clearSettledStandaloneRecovery();
                return true;
            }
            if (withinSettledWindow && restoreSettledStandaloneAsDoubleInitial(consonant, replacement, eventTimeMs)) {
                return true;
            }
            if (withinTapWindow && composer.canPromoteCombinedFinalToDoubleInitial(consonant)) {
                String committed = composer.promoteFinalToDoubleInitial(consonant, replacement);
                if (committed != null) {
                    commitTextIfNeeded(committed);
                    return true;
                }
            }
            if (withinTapWindow && composer.replaceSingleConsonant(consonant, replacement)) {
                clearSettledStandaloneRecovery();
                return true;
            }
            return false;
        }

        private boolean withinDoubleConsonantTapWindow(Consonant consonant, long eventTimeMs) {
            return lastConsonantTap == consonant
                    && lastConsonantTapTimeMs > 0L
                    && eventTimeMs - lastConsonantTapTimeMs <= doubleConsonantTimeoutMs();
        }

        private boolean withinSettledStandaloneDoubleWindow(Consonant consonant, long eventTimeMs) {
            return recentlySettledStandaloneConsonant == consonant
                    && recentlySettledStandaloneConsonantTimeMs > 0L
                    && eventTimeMs - recentlySettledStandaloneConsonantTimeMs <= doubleConsonantTimeoutMs();
        }

        private int doubleConsonantTimeoutMs() {
            return 380;
        }

        private int doubleConsonantRecoveryTimeoutMs() {
            return Math.min(760, doubleConsonantTimeoutMs() + 220);
        }

        private void resetDoubleConsonantTapState() {
            lastConsonantTap = null;
            lastConsonantTapTimeMs = 0L;
        }

        private void refreshComposingText() {
            if (composer.hasComposingText()) {
                editor.setComposingText(composer.getComposingText());
                immediateConsonantPlaceholderVisible = composer.isLeadingOnly();
            } else {
                editor.finishComposingText();
                immediateConsonantPlaceholderVisible = false;
            }
        }

        private void commitComposingText() {
            if (immediateConsonantPlaceholderVisible && composer.isLeadingOnly()) {
                editor.finishComposingText();
                immediateConsonantPlaceholderVisible = false;
                composer.reset();
                return;
            }
            commitTextIfNeeded(composer.commit());
        }

        private void showImmediateConsonantPlaceholder() {
            if (!composer.isLeadingOnly()) {
                refreshComposingText();
                return;
            }
            editor.setComposingText(composer.getComposingText());
            immediateConsonantPlaceholderVisible = true;
        }

        private void settleImmediateConsonantPlaceholderIfStandalone() {
            if (!immediateConsonantPlaceholderVisible || !composer.isLeadingOnly()) {
                return;
            }
            rememberSettledStandaloneConsonant(composer.leadingOnlyConsonant(), now);
            editor.finishComposingText();
            immediateConsonantPlaceholderVisible = false;
            composer.reset();
        }

        private boolean restoreSettledStandaloneAsDoubleInitial(
                Consonant consonant,
                Consonant replacement,
                long eventTimeMs
        ) {
            if (recentlySettledStandaloneConsonant != consonant
                    || !canRecoverSettledStandalone(eventTimeMs)
                    || composer.hasComposingText()) {
                return false;
            }
            editor.deleteSurroundingText(1);
            composer.reset();
            composer.inputConsonant(replacement);
            immediateConsonantPlaceholderVisible = true;
            clearSettledStandaloneRecovery();
            return true;
        }

        private void restoreSettledStandaloneForVowelIfNeeded(long eventTimeMs) {
            if (!canRecoverSettledStandalone(eventTimeMs) || composer.hasComposingText()) {
                return;
            }
            Consonant restored = recentlySettledStandaloneConsonant;
            editor.deleteSurroundingText(1);
            composer.reset();
            composer.inputConsonant(restored);
            immediateConsonantPlaceholderVisible = true;
            clearSettledStandaloneRecovery();
        }

        private boolean canRecoverSettledStandalone(long eventTimeMs) {
            if (recentlySettledStandaloneConsonant == null) {
                return false;
            }
            if (eventTimeMs - recentlySettledStandaloneConsonantTimeMs <= doubleConsonantRecoveryTimeoutMs()) {
                return true;
            }
            clearSettledStandaloneRecovery();
            return false;
        }

        private void clearSettledStandaloneRecoveryIfNot(Consonant consonant, long eventTimeMs) {
            if (recentlySettledStandaloneConsonant == null) {
                return;
            }
            if (recentlySettledStandaloneConsonant != consonant || !canRecoverSettledStandalone(eventTimeMs)) {
                clearSettledStandaloneRecovery();
            }
        }

        private void rememberSettledStandaloneConsonant(Consonant consonant, long eventTimeMs) {
            recentlySettledStandaloneConsonant = consonant;
            recentlySettledStandaloneConsonantTimeMs = consonant == null ? 0L : eventTimeMs;
        }

        private void clearSettledStandaloneRecovery() {
            recentlySettledStandaloneConsonant = null;
            recentlySettledStandaloneConsonantTimeMs = 0L;
        }

        private void commitTextIfNeeded(String text) {
            if (text != null && !text.isEmpty()) {
                commitText(text);
            }
        }

        private void commitText(String text) {
            clearSettledStandaloneRecovery();
            editor.commitText(text);
        }

        private void tick() {
            now += 80L;
        }
    }

    public static void main(String[] args) {
        boolean trace = false;
        for (String arg : args) {
            if ("--trace".equals(arg)) {
                trace = true;
            }
        }

        List<Case> cases = new ArrayList<>();
        cases.add(new Case("double_siot_then_eu",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.touch(Consonant.SIOT);
                    engine.vowel(HangulComposer.V_EU);
                    engine.finish();
                },
                "\uC4F0"));
        cases.add(new Case("sseu_second_drag_slow_release",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.touch(Consonant.SIOT);
                    engine.waitMs(520);
                    engine.gesture(Consonant.SIOT, HangulComposer.V_EU, true);
                    engine.finish();
                },
                "\uC4F0"));
        cases.add(new Case("jjeu_second_drag_slow_release",
                engine -> {
                    engine.touch(Consonant.JIEUT);
                    engine.touch(Consonant.JIEUT);
                    engine.waitMs(520);
                    engine.gesture(Consonant.JIEUT, HangulComposer.V_EU, true);
                    engine.finish();
                },
                "\uCBD4"));
        cases.add(new Case("ddeu_second_drag_slow_release",
                engine -> {
                    engine.touch(Consonant.DIGEUT);
                    engine.touch(Consonant.DIGEUT);
                    engine.waitMs(520);
                    engine.gesture(Consonant.DIGEUT, HangulComposer.V_EU, true);
                    engine.finish();
                },
                "\uB728"));
        cases.add(new Case("settled_siot_double_recovery",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.forceSettleStandalone();
                    engine.touch(Consonant.SIOT);
                    engine.vowel(HangulComposer.V_EU);
                    engine.finish();
                },
                "\uC4F0"));
        cases.add(new Case("late_siot_not_double",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.waitMs(520);
                    engine.touch(Consonant.SIOT);
                    engine.vowel(HangulComposer.V_EU);
                    engine.finish();
                },
                "\u3145\uC2A4"));
        cases.add(new Case("settled_siot_vowel_recovery",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.forceSettleStandalone();
                    engine.vowel(HangulComposer.V_EU);
                    engine.finish();
                },
                "\uC2A4"));
        cases.add(new Case("jja_fast_second_drag",
                engine -> {
                    engine.touch(Consonant.JIEUT);
                    engine.touch(Consonant.JIEUT);
                    engine.gesture(Consonant.JIEUT, HangulComposer.V_A, true);
                    engine.finish();
                },
                "\uC9DC"));
        cases.add(new Case("jja_settled_second_drag_recovery",
                engine -> {
                    engine.touch(Consonant.JIEUT);
                    engine.forceSettleStandalone();
                    engine.touch(Consonant.JIEUT);
                    engine.gesture(Consonant.JIEUT, HangulComposer.V_A, true);
                    engine.finish();
                },
                "\uC9DC"));
        cases.add(new Case("jja_settled_release_fallback_recovery",
                engine -> {
                    engine.touch(Consonant.JIEUT);
                    engine.forceSettleStandalone();
                    engine.gesture(Consonant.JIEUT, HangulComposer.V_A, false);
                    engine.finish();
                },
                "\uC9DC"));
        cases.add(new Case("sseugosipda",
                engine -> {
                    engine.touch(Consonant.SIOT);
                    engine.touch(Consonant.SIOT);
                    engine.vowel(HangulComposer.V_EU);
                    engine.stroke(Consonant.GIYEOK, HangulComposer.V_O);
                    engine.stroke(Consonant.SIOT, HangulComposer.V_I);
                    engine.touch(Consonant.PIEUP);
                    engine.stroke(Consonant.DIGEUT, HangulComposer.V_A);
                    engine.finish();
                },
                "\uC4F0\uACE0\uC2F6\uB2E4"));
        cases.add(new Case("galgga",
                engine -> {
                    engine.stroke(Consonant.GIYEOK, HangulComposer.V_A);
                    engine.touch(Consonant.RIEUL);
                    engine.touch(Consonant.GIYEOK);
                    engine.touch(Consonant.GIYEOK);
                    engine.vowel(HangulComposer.V_A);
                    engine.finish();
                },
                "\uAC08\uAE4C"));
        cases.add(new Case("late_galgga_not_double",
                engine -> {
                    engine.stroke(Consonant.GIYEOK, HangulComposer.V_A);
                    engine.touch(Consonant.RIEUL);
                    engine.touch(Consonant.GIYEOK);
                    engine.waitMs(520);
                    engine.touch(Consonant.GIYEOK);
                    engine.vowel(HangulComposer.V_A);
                    engine.finish();
                },
                "\uAC09\uAC00"));
        cases.add(new Case("jinjja",
                engine -> {
                    engine.stroke(Consonant.JIEUT, HangulComposer.V_I);
                    engine.touch(Consonant.NIEUN);
                    engine.touch(Consonant.JIEUT);
                    engine.touch(Consonant.JIEUT);
                    engine.vowel(HangulComposer.V_A);
                    engine.finish();
                },
                "\uC9C4\uC9DC"));
        cases.add(new Case("jette",
                engine -> {
                    engine.stroke(Consonant.JIEUT, HangulComposer.V_E);
                    engine.touch(Consonant.DIGEUT);
                    engine.touch(Consonant.DIGEUT);
                    engine.vowel(HangulComposer.V_AE);
                    engine.finish();
                },
                "\uC81C\uB54C"));
        cases.add(new Case("appa",
                engine -> {
                    engine.stroke(Consonant.IEUNG, HangulComposer.V_A);
                    engine.touch(Consonant.BIEUP);
                    engine.touch(Consonant.BIEUP);
                    engine.vowel(HangulComposer.V_A);
                    engine.finish();
                },
                "\uC544\uBE60"));
        cases.add(new Case("ajja",
                engine -> {
                    engine.stroke(Consonant.IEUNG, HangulComposer.V_A);
                    engine.touch(Consonant.JIEUT);
                    engine.touch(Consonant.JIEUT);
                    engine.vowel(HangulComposer.V_A);
                    engine.finish();
                },
                "\uC544\uC9DC"));
        cases.add(new Case("annyeong",
                engine -> {
                    engine.stroke(Consonant.IEUNG, HangulComposer.V_A);
                    engine.touch(Consonant.NIEUN);
                    engine.stroke(Consonant.NIEUN, HangulComposer.V_YEO);
                    engine.touch(Consonant.IEUNG);
                    engine.finish();
                },
                "\uC548\uB155"));
        cases.add(new Case("anyeo_without_second_nieun",
                engine -> {
                    engine.stroke(Consonant.IEUNG, HangulComposer.V_A);
                    engine.touch(Consonant.NIEUN);
                    engine.vowel(HangulComposer.V_YEO);
                    engine.finish();
                },
                "\uC544\uB140"));
        cases.add(new Case("yu_yu_yu_as_vowels",
                engine -> {
                    engine.vowel(HangulComposer.V_YU);
                    engine.vowel(HangulComposer.V_YU);
                    engine.vowel(HangulComposer.V_YU);
                    engine.finish();
                },
                "\u3160\u3160\u3160"));

        int failed = 0;
        for (Case testCase : cases) {
            Engine engine = new Engine(trace);
            testCase.scenario.run(engine);
            String actual = engine.text();
            if (testCase.expected.equals(actual)) {
                System.out.println("PASS " + testCase.name + " => " + escape(actual));
            } else {
                failed++;
                System.out.println("FAIL " + testCase.name
                        + " expected=" + escape(testCase.expected)
                        + " actual=" + escape(actual));
            }
        }
        failed += runCalibrationPlannerCases();
        failed += runGestureCases();
        if (failed > 0) {
            throw new AssertionError(failed + " simulation case(s) failed");
        }
        System.out.println("ALL PASS " + cases.size() + " text cases");
    }

    private static int runCalibrationPlannerCases() {
        int failed = 0;
        CalibrationPlanner.Plan hyeon = CalibrationPlanner.build("\uD604");
        failed += expectPlan("calibration_plan_hyeon", hyeon,
                new String[]{"\uD604", "\uD600", "\uD5C8", "\uD638"},
                new int[]{10, 5, 5, 5},
                25);

        CalibrationPlanner.Plan hyeo = CalibrationPlanner.build("\uD600");
        failed += expectPlan("calibration_plan_hyeo", hyeo,
                new String[]{"\uD600", "\uD5C8", "\uD638"},
                new int[]{10, 5, 5},
                20);

        if (failed == 0) {
            System.out.println("ALL PASS 2 calibration planner cases");
        }
        return failed;
    }

    private static int expectPlan(String name, CalibrationPlanner.Plan plan, String[] expectedTexts,
                                  int[] expectedRepeats, int expectedTotal) {
        boolean ok = plan.steps.size() == expectedTexts.length
                && plan.totalCharacters() == expectedTotal;
        if (ok) {
            for (int i = 0; i < expectedTexts.length; i++) {
                CalibrationPlanner.Step step = plan.steps.get(i);
                if (!expectedTexts[i].equals(step.text) || expectedRepeats[i] != step.repeats) {
                    ok = false;
                    break;
                }
            }
        }
        if (ok) {
            System.out.println("PASS " + name + " => " + escape(plan.summary()));
            return 0;
        }
        System.out.println("FAIL " + name + " expectedTotal=" + expectedTotal
                + " actualTotal=" + plan.totalCharacters()
                + " summary=" + escape(plan.summary()));
        return 1;
    }

    private static int runGestureCases() {
        GestureVowelMapper mapper = new GestureVowelMapper(new DisplayMetrics());
        List<GestureCase> cases = new ArrayList<>();
        cases.add(new GestureCase("gesture_single_o",
                points(0f, 0f, 0f, -4f),
                HangulComposer.V_O));
        cases.add(new GestureCase("gesture_long_up_i",
                points(0f, 0f, 0f, -16f),
                HangulComposer.V_I));
        cases.add(new GestureCase("gesture_smooth_down_right_ya",
                points(0f, 0f, 2f, 2f, 6f, 6f),
                HangulComposer.V_YA));
        cases.add(new GestureCase("gesture_a_then_down_right_ae",
                points(0f, 0f, 2f, 0f, 6f, 4f),
                HangulComposer.V_AE));
        cases.add(new GestureCase("gesture_o_right_wa",
                points(0f, 0f, 0f, -4f, 4f, -4f),
                HangulComposer.V_WA));
        cases.add(new GestureCase("gesture_o_left_wae",
                points(0f, 0f, 0f, -4f, -4f, -4f),
                HangulComposer.V_WAE));
        cases.add(new GestureCase("gesture_o_down_oe",
                points(0f, 0f, 0f, -4f, 0f, 4f),
                HangulComposer.V_OE));
        cases.add(new GestureCase("gesture_o_short_down_oe",
                points(0f, 0f, 0f, -1.7f, 0f, -0.2f),
                HangulComposer.V_OE));
        cases.add(new GestureCase("gesture_a_short_left_eu",
                points(0f, 0f, 1.7f, 0f, 0.2f, 0f),
                HangulComposer.V_EU));
        cases.add(new GestureCase("gesture_u_short_up_wi",
                points(0f, 0f, 0f, 1.7f, 0f, 0.2f),
                HangulComposer.V_WI));
        cases.add(new GestureCase("gesture_u_left_up_we",
                points(0f, 0f, 0f, 4f, -4f, 4f, -4f, 0f),
                HangulComposer.V_WE));
        cases.add(new GestureCase("gesture_eo_down_left_e",
                points(0f, 0f, -4f, 0f, -7f, 4f),
                HangulComposer.V_E));
        cases.add(new GestureCase("gesture_eo_then_down_left_not_yu",
                points(0f, 0f, -2f, 0f, -6f, 4f),
                HangulComposer.V_E));
        cases.add(new GestureCase("gesture_eo_short_left_then_down_left_not_yu",
                points(0f, 0f, -1.3f, 0f, -4f, 4f),
                HangulComposer.V_E));
        cases.add(new GestureCase("gesture_eo_clear_corner_down_left_e",
                points(0f, 0f, -4f, 0f, -8f, 4f),
                HangulComposer.V_E));
        cases.add(new GestureCase("gesture_a_up_right_ae",
                points(0f, 0f, 4f, 0f, 7f, -4f),
                HangulComposer.V_AE));
        cases.add(new GestureCase("gesture_left_right_eu",
                points(0f, 0f, -4f, 0f, 0f, 0f),
                HangulComposer.V_EU));
        cases.add(new GestureCase("gesture_short_up_down_i",
                points(0f, 0f, 0f, -2.1f, 0f, 0f),
                HangulComposer.V_I));
        cases.add(new GestureCase("gesture_u_up_wi",
                points(0f, 0f, 0f, 4f, 0f, 0f),
                HangulComposer.V_WI));
        cases.add(new GestureCase("gesture_long_eu_up_yi",
                points(0f, 0f, 16f, 0f, 16f, -6f),
                HangulComposer.V_YI));
        cases.add(new GestureCase("gesture_long_eu_tiny_up_stays_eu",
                points(0f, 0f, 16f, 0f, 16f, -3.5f),
                HangulComposer.V_EU));
        cases.add(new GestureCase("gesture_yo_return_i",
                points(0f, 0f, 4f, -4f, 0f, 0f),
                HangulComposer.V_I));
        cases.add(new GestureCase("gesture_yeo_up_ye",
                points(0f, 0f, -4f, -4f, -4f, -8f),
                HangulComposer.V_YE));
        cases.add(new GestureCase("gesture_yeo_down_ye",
                points(0f, 0f, -4f, -4f, -4f, 0f),
                HangulComposer.V_YE));
        cases.add(new GestureCase("gesture_ya_up_yae",
                points(0f, 0f, 4f, 4f, 4f, 0f),
                HangulComposer.V_YAE));
        cases.add(new GestureCase("gesture_yu_up_i",
                points(0f, 0f, -4f, 4f, -4f, 0f),
                HangulComposer.V_I));

        int failed = 0;
        for (GestureCase testCase : cases) {
            Integer actual = mapper.map(testCase.points, null, "sim-key");
            if (actual != null && actual == testCase.expected) {
                System.out.println("PASS " + testCase.name + " => " + actual);
            } else {
                failed++;
                System.out.println("FAIL " + testCase.name
                        + " expected=" + testCase.expected
                        + " actual=" + actual);
            }
        }
        failed += runSplitStrokeLengthCases();
        failed += runTypoGuardGestureCases();
        if (failed == 0) {
            System.out.println("ALL PASS " + cases.size() + " gesture cases");
        }
        return failed;
    }

    private static int runSplitStrokeLengthCases() {
        int failed = 0;
        GestureVowelMapper mapper = new GestureVowelMapper(new DisplayMetrics());
        mapper.setStrokeLengths(1f, 3f, 13.5f);
        Integer blocked = mapper.map(points(0f, 0f, 3f, 0f, 3f, -1f), null, "sim-key");
        if (blocked != null && blocked == HangulComposer.V_A) {
            System.out.println("PASS gesture_split_threshold_blocks_tiny_second_leg => " + blocked);
        } else {
            failed++;
            System.out.println("FAIL gesture_split_threshold_blocks_tiny_second_leg expected="
                    + HangulComposer.V_A + " actual=" + blocked);
        }

        mapper.setStrokeLengths(1f, 1f, 13.5f);
        Integer allowed = mapper.map(points(0f, 0f, 3f, 0f, 3f, -1f), null, "sim-key");
        if (allowed != null && allowed == HangulComposer.V_AE) {
            System.out.println("PASS gesture_split_threshold_allows_short_second_leg => " + allowed);
        } else {
            failed++;
            System.out.println("FAIL gesture_split_threshold_allows_short_second_leg expected="
                    + HangulComposer.V_AE + " actual=" + allowed);
        }

        mapper.setStrokeLengths(1f, 8f, 13.5f);
        Integer eoBlocked = mapper.map(points(0f, 0f, -1.2f, 0f, -2.2f, 1.2f), null, "sim-key");
        if (eoBlocked != null && eoBlocked == HangulComposer.V_EO) {
            System.out.println("PASS gesture_eo_derivation_threshold_blocks_tiny_second_leg => " + eoBlocked);
        } else {
            failed++;
            System.out.println("FAIL gesture_eo_derivation_threshold_blocks_tiny_second_leg expected="
                    + HangulComposer.V_EO + " actual=" + eoBlocked);
        }

        Integer euBlocked = mapper.map(points(0f, 0f, 16f, 0f, 16f, -4f), null, "sim-key");
        if (euBlocked != null && euBlocked == HangulComposer.V_EU) {
            System.out.println("PASS gesture_eu_derivation_setting_blocks_short_second_leg => " + euBlocked);
        } else {
            failed++;
            System.out.println("FAIL gesture_eu_derivation_setting_blocks_short_second_leg expected="
                    + HangulComposer.V_EU + " actual=" + euBlocked);
        }

        Integer euAllowed = mapper.map(points(0f, 0f, 16f, 0f, 16f, -9f), null, "sim-key");
        if (euAllowed != null && euAllowed == HangulComposer.V_YI) {
            System.out.println("PASS gesture_eu_derivation_setting_allows_long_second_leg => " + euAllowed);
        } else {
            failed++;
            System.out.println("FAIL gesture_eu_derivation_setting_allows_long_second_leg expected="
                    + HangulComposer.V_YI + " actual=" + euAllowed);
        }

        if (failed == 0) {
            System.out.println("ALL PASS 5 split stroke-length gesture cases");
        }
        return failed;
    }

    private static int runTypoGuardGestureCases() {
        int failed = 0;
        GestureVowelMapper mapper = new GestureVowelMapper(new DisplayMetrics());
        mapper.setBlockYeoUpToYe(true);

        Integer blockedUp = mapper.map(points(0f, 0f, -4f, -4f, -4f, -8f), null, "sim-key");
        if (blockedUp != null && blockedUp == HangulComposer.V_YEO) {
            System.out.println("PASS gesture_typo_guard_blocks_yeo_up_ye => " + blockedUp);
        } else {
            failed++;
            System.out.println("FAIL gesture_typo_guard_blocks_yeo_up_ye expected="
                    + HangulComposer.V_YEO + " actual=" + blockedUp);
        }

        Integer allowedDown = mapper.map(points(0f, 0f, -4f, -4f, -4f, 0f), null, "sim-key");
        if (allowedDown != null && allowedDown == HangulComposer.V_YE) {
            System.out.println("PASS gesture_typo_guard_allows_yeo_down_ye => " + allowedDown);
        } else {
            failed++;
            System.out.println("FAIL gesture_typo_guard_allows_yeo_down_ye expected="
                    + HangulComposer.V_YE + " actual=" + allowedDown);
        }

        if (failed == 0) {
            System.out.println("ALL PASS 2 typo guard gesture cases");
        }
        return failed;
    }

    private static List<GestureVowelMapper.Point> points(float... coordinates) {
        List<GestureVowelMapper.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            points.add(new GestureVowelMapper.Point(
                    coordinates[i] * GESTURE_TEST_PX_PER_MM,
                    coordinates[i + 1] * GESTURE_TEST_PX_PER_MM));
        }
        return points;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x20 && ch <= 0x7e) {
                escaped.append(ch);
            } else {
                escaped.append(String.format("\\u%04X", (int) ch));
            }
        }
        return escaped.toString();
    }
}
