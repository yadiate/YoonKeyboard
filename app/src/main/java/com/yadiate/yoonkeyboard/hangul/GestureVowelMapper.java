package com.yadiate.yoonkeyboard.hangul;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

public class GestureVowelMapper {
    public enum Direction {
        RIGHT, LEFT, UP, DOWN, DOWN_RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT
    }

    private static final float CARDINAL_DOMINANCE_RATIO = 2.4142137f;
    private static final float BACKTRACK_AXIS_DOMINANCE_RATIO = 1.15f;
    private static final float MIN_LONG_GESTURE_MM = 5f;
    private static final float MIN_SHORT_LONG_GAP_MM = 1f;
    private static final float DERIVATION_TURN_MIN_ANGLE_DEGREES = 50f;
    private static final float DERIVATION_CLEAR_CORNER_MIN_ANGLE_DEGREES = 40f;
    private static final float DERIVATION_CLEAR_CORNER_MIN_LEG_RATIO = 1.45f;
    private static final int MAX_DERIVATION_LEGS = 4;

    private float xPixelsPerMm = 1f;
    private float yPixelsPerMm = 1f;
    private float firstSegmentMm = 3.6f;
    private float derivationSegmentMm = 4.75f;
    private float longGestureMm = 13.5f;
    private boolean blockYeoUpToYe;
    private GestureCalibration.Profile calibrationProfile;
    private String activeCalibrationKey = "";

    public GestureVowelMapper(DisplayMetrics metrics) {
        setDisplayMetrics(metrics);
    }

    public void setDisplayMetrics(DisplayMetrics metrics) {
        float fallbackDpi = metrics.densityDpi > 0 ? metrics.densityDpi : metrics.density * 160f;
        float xDpi = reasonableDpi(metrics.xdpi) ? metrics.xdpi : fallbackDpi;
        float yDpi = reasonableDpi(metrics.ydpi) ? metrics.ydpi : fallbackDpi;
        xPixelsPerMm = Math.max(1f, xDpi / 25.4f);
        yPixelsPerMm = Math.max(1f, yDpi / 25.4f);
    }

    public void setStrokeLengths(float minSegmentMm, float longGestureMm) {
        setStrokeLengths(minSegmentMm, Math.max(minSegmentMm + 0.25f, minSegmentMm * 1.25f),
                longGestureMm);
    }

    public void setStrokeLengths(float firstSegmentMm, float derivationSegmentMm, float longGestureMm) {
        this.longGestureMm = Math.max(MIN_LONG_GESTURE_MM, longGestureMm);
        this.firstSegmentMm = Math.max(0.5f,
                Math.min(firstSegmentMm, this.longGestureMm - MIN_SHORT_LONG_GAP_MM));
        this.derivationSegmentMm = Math.max(this.firstSegmentMm + 0.25f,
                Math.min(derivationSegmentMm, this.longGestureMm - MIN_SHORT_LONG_GAP_MM));
    }

    public void setBlockYeoUpToYe(boolean blockYeoUpToYe) {
        this.blockYeoUpToYe = blockYeoUpToYe;
    }

    public void setCalibrationProfile(GestureCalibration.Profile calibrationProfile) {
        this.calibrationProfile = calibrationProfile != null && calibrationProfile.hasData()
                ? calibrationProfile
                : null;
    }

    public Integer map(List<Point> points) {
        return map(points, null);
    }

    public Integer map(List<Point> points, Consonant consonant) {
        return map(points, consonant, "");
    }

    public Integer map(List<Point> points, Consonant consonant, String calibrationKey) {
        String previousCalibrationKey = activeCalibrationKey;
        activeCalibrationKey = calibrationKey == null ? "" : calibrationKey;
        try {
            return mapWithActiveCalibration(points, consonant);
        } finally {
            activeCalibrationKey = previousCalibrationKey;
        }
    }

    private Integer mapWithActiveCalibration(List<Point> points, Consonant consonant) {
        if (points == null || points.size() < 2) {
            return null;
        }

        Point start = points.get(0);
        Point end = points.get(points.size() - 1);
        Integer backtrackVowel = mapShortBacktrack(points, consonant);
        if (backtrackVowel != null) {
            return backtrackVowel;
        }

        List<StrokeLeg> legs = derivationLegs(points, consonant);
        if (legs.isEmpty()) {
            float totalDistance = pointsDistanceMm(start, end);
            if (totalDistance < firstSegmentMinMm()) {
                Integer stableVowel = mapFirstStableIntent(points, consonant);
                return stableVowel == null ? null : stableVowel;
            }
            Direction overallDirection = directionFor(xDistanceMm(start, end), yDistanceMm(start, end), consonant);
            legs.add(new StrokeLeg(overallDirection,
                    dominantCardinalAxisDirection(xDistanceMm(start, end), yDistanceMm(start, end)),
                    totalDistance));
        }

        StrokeLeg first = legs.get(0);
        float totalDistance = pointsDistanceMm(start, end);
        boolean longInitial = isLongGesture(first.direction,
                legs.size() == 1 ? totalDistance : first.distanceMm, consonant);
        Integer vowel = mapSingleDirection(first.direction, longInitial);
        if (vowel == null) {
            return null;
        }
        for (int i = 1; i < legs.size(); i++) {
            vowel = deriveVowel(vowel, first.direction, legs.get(i));
        }
        vowel = constrainInitialDiagonalFlip(points, consonant, vowel);
        return vowel;
    }

    private Integer mapFirstStableIntent(List<Point> points, Consonant consonant) {
        StrokeLeg stable = firstStableIntent(points, consonant);
        if (stable == null) {
            return null;
        }
        Integer baseVowel = mapSingleDirection(stable.direction,
                isLongGesture(stable.direction, stable.distanceMm, consonant));
        Integer derivedVowel = deriveShortReverseVowel(baseVowel, stable, points.get(points.size() - 1), consonant);
        return derivedVowel == null ? baseVowel : derivedVowel;
    }

    private StrokeLeg firstStableIntent(List<Point> points, Consonant consonant) {
        if (points == null || points.size() < 2) {
            return null;
        }
        Point start = points.get(0);
        float minDistance = firstSegmentMinMm();
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float dx = xDistanceMm(start, point);
            float dy = yDistanceMm(start, point);
            float distance = (float) Math.hypot(dx, dy);
            if (distance < minDistance) {
                continue;
            }
            return new StrokeLeg(directionFor(dx, dy, consonant), dominantCardinalAxisDirection(dx, dy),
                    distance, point);
        }
        return null;
    }

    private Integer deriveShortReverseVowel(Integer baseVowel, StrokeLeg firstLeg, Point end, Consonant consonant) {
        if (baseVowel == null || firstLeg == null || end == null) {
            return null;
        }
        Direction reverseAxis = oppositeCardinal(firstLeg.axis);
        if (reverseAxis == null) {
            return null;
        }
        float dx = xDistanceMm(firstLeg.endPoint, end);
        float dy = yDistanceMm(firstLeg.endPoint, end);
        float distance = (float) Math.hypot(dx, dy);
        if (distance < shortReverseDerivationMinMm()) {
            return null;
        }
        Direction direction = dominantCardinalAxisDirection(dx, dy);
        if (direction != reverseAxis) {
            return null;
        }
        StrokeLeg reverseLeg = new StrokeLeg(directionFor(dx, dy, consonant), direction, distance, end);
        Integer derived = deriveVowel(baseVowel, firstLeg.direction, reverseLeg);
        return derived == baseVowel ? null : derived;
    }

    private Integer constrainInitialDiagonalFlip(List<Point> points, Consonant consonant, Integer vowel) {
        if (!isInitialDiagonalVowel(vowel)) {
            return vowel;
        }
        StableCardinal stable = firstStableCardinal(points, consonant);
        if (stable == null) {
            return vowel;
        }
        Integer baseVowel = mapSingleDirection(stable.direction, false);
        if (baseVowel == null) {
            return vowel;
        }
        Point end = points.get(points.size() - 1);
        float dx = xDistanceMm(stable.point, end);
        float dy = yDistanceMm(stable.point, end);
        if (Math.hypot(dx, dy) < derivationSegmentMinMm()) {
            return baseVowel;
        }
        StrokeLeg leg = new StrokeLeg(directionFor(dx, dy, consonant),
                dominantCardinalAxisDirection(dx, dy), (float) Math.hypot(dx, dy));
        Integer derived = deriveVowel(baseVowel, stable.direction, leg);
        return derived == null ? baseVowel : derived;
    }

    private boolean isInitialDiagonalVowel(Integer vowel) {
        return vowel != null
                && (vowel == HangulComposer.V_YA
                || vowel == HangulComposer.V_YEO
                || vowel == HangulComposer.V_YO
                || vowel == HangulComposer.V_YU);
    }

    private StableCardinal firstStableCardinal(List<Point> points, Consonant consonant) {
        if (points == null || points.size() < 2) {
            return null;
        }
        Point start = points.get(0);
        float minDistance = firstCardinalIntentMinMm();
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float dx = xDistanceMm(start, point);
            float dy = yDistanceMm(start, point);
            if (Math.hypot(dx, dy) < minDistance) {
                continue;
            }
            Direction direction = directionFor(dx, dy, consonant);
            return isCardinal(direction) ? new StableCardinal(direction, point) : null;
        }
        return null;
    }

    private List<StrokeLeg> derivationLegs(List<Point> points, Consonant consonant) {
        List<StrokeLeg> legs = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return legs;
        }
        Point legStart = points.get(0);
        Point sampleAnchor = legStart;
        Direction previousDirection = null;
        float previousAngle = Float.NaN;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float dx = xDistanceMm(sampleAnchor, point);
            float dy = yDistanceMm(sampleAnchor, point);
            float distance = (float) Math.hypot(dx, dy);
            float segmentMinMm = previousDirection == null ? firstSegmentMinMm() : derivationSegmentMinMm();
            if (distance < segmentMinMm) {
                continue;
            }
            Direction direction = directionFor(dx, dy, consonant);
            float angle = angleFor(dx, dy);
            float previousLegDistance = pointsDistanceMm(legStart, sampleAnchor);
            boolean sharpTurn = isDerivationTurn(previousDirection, direction, previousAngle,
                    angle, previousLegDistance);
            if (sharpTurn) {
                if (legs.size() >= MAX_DERIVATION_LEGS) {
                    break;
                }
                int previousLegCount = legs.size();
                addStrokeLeg(legs, legStart, sampleAnchor, consonant);
                if (legs.size() > previousLegCount) {
                    legStart = sampleAnchor;
                }
            }
            previousDirection = direction;
            previousAngle = angle;
            sampleAnchor = point;
        }
        addStrokeLeg(legs, legStart, points.get(points.size() - 1), consonant);
        return legs;
    }

    private boolean isDerivationTurn(Direction previousDirection, Direction direction,
                                     float previousAngle, float angle, float previousLegDistance) {
        if (previousDirection == null || direction == previousDirection) {
            return false;
        }
        float angleChange = Math.abs(GestureCalibration.angleDiff(angle, previousAngle));
        if (angleChange >= DERIVATION_TURN_MIN_ANGLE_DEGREES) {
            return true;
        }
        if (isCardinal(previousDirection)
                && !isCardinal(direction)
                && previousLegDistance >= firstSegmentMinMm()) {
            return true;
        }
        if (angleChange >= DERIVATION_CLEAR_CORNER_MIN_ANGLE_DEGREES
                && previousLegDistance >= firstSegmentMinMm() * DERIVATION_CLEAR_CORNER_MIN_LEG_RATIO) {
            return true;
        }
        return angleChange >= DERIVATION_CLEAR_CORNER_MIN_ANGLE_DEGREES
                && !isCardinal(previousDirection)
                && isCardinal(direction);
    }

    private void addStrokeLeg(List<StrokeLeg> legs, Point start, Point end, Consonant consonant) {
        if (legs.size() >= MAX_DERIVATION_LEGS) {
            return;
        }
        float dx = xDistanceMm(start, end);
        float dy = yDistanceMm(start, end);
        float minDistance = legs.isEmpty() ? firstSegmentMinMm() : derivationSegmentMinMm();
        if (Math.hypot(dx, dy) < minDistance) {
            return;
        }
        legs.add(new StrokeLeg(directionFor(dx, dy, consonant), dominantCardinalAxisDirection(dx, dy),
                (float) Math.hypot(dx, dy)));
    }

    private Integer deriveVowel(Integer currentVowel, Direction firstDirection, StrokeLeg leg) {
        if (currentVowel == null || leg == null) {
            return currentVowel;
        }
        Direction axis = axisForVowelDerivation(currentVowel, leg);
        switch (currentVowel) {
            case HangulComposer.V_O:
                if (axis == Direction.LEFT) {
                    return HangulComposer.V_WAE;
                }
                if (axis == Direction.RIGHT) {
                    return HangulComposer.V_WA;
                }
                if (axis == Direction.DOWN) {
                    return HangulComposer.V_OE;
                }
                return currentVowel;
            case HangulComposer.V_U:
                if (axis == Direction.LEFT) {
                    return HangulComposer.V_WEO;
                }
                if (axis == Direction.RIGHT) {
                    return HangulComposer.V_WE;
                }
                if (axis == Direction.UP) {
                    return HangulComposer.V_WI;
                }
                return currentVowel;
            case HangulComposer.V_EO:
                if (axis == Direction.RIGHT) {
                    return HangulComposer.V_EU;
                }
                if (isVertical(axis)) {
                    return HangulComposer.V_E;
                }
                return currentVowel;
            case HangulComposer.V_A:
                if (axis == Direction.LEFT) {
                    return HangulComposer.V_EU;
                }
                if (isVertical(axis)) {
                    return HangulComposer.V_AE;
                }
                return currentVowel;
            case HangulComposer.V_YO:
                return isOppositeDiagonal(firstDirection, leg.direction) || axis == Direction.DOWN
                        ? HangulComposer.V_I
                        : currentVowel;
            case HangulComposer.V_YEO:
                if (isOppositeDiagonal(firstDirection, leg.direction)) {
                    return HangulComposer.V_I;
                }
                if (axis == Direction.UP && blockYeoUpToYe) {
                    return currentVowel;
                }
                return isVertical(axis) ? HangulComposer.V_YE : currentVowel;
            case HangulComposer.V_YU:
                return isOppositeDiagonal(firstDirection, leg.direction) || axis == Direction.UP
                        ? HangulComposer.V_I
                        : currentVowel;
            case HangulComposer.V_YA:
                if (isOppositeDiagonal(firstDirection, leg.direction)) {
                    return HangulComposer.V_I;
                }
                return axis == Direction.UP ? HangulComposer.V_YAE : currentVowel;
            case HangulComposer.V_WA:
                return isVertical(axis) ? HangulComposer.V_WAE : currentVowel;
            case HangulComposer.V_WEO:
                return isVertical(axis) ? HangulComposer.V_WE : currentVowel;
            case HangulComposer.V_EU:
                return isVertical(axis) && leg.distanceMm >= strictDerivationSegmentMinMm()
                        ? HangulComposer.V_YI
                        : currentVowel;
            default:
                return currentVowel;
        }
    }

    private Direction axisForVowelDerivation(Integer currentVowel, StrokeLeg leg) {
        if (leg == null || currentVowel == null) {
            return null;
        }
        switch (currentVowel) {
            case HangulComposer.V_EO:
            case HangulComposer.V_A:
                return hasVerticalComponent(leg.direction) && !isCardinal(leg.direction)
                        ? verticalComponent(leg.direction)
                        : leg.axis;
            case HangulComposer.V_O:
            case HangulComposer.V_U:
                return hasHorizontalComponent(leg.direction) && !isCardinal(leg.direction)
                        ? horizontalComponent(leg.direction)
                        : leg.axis;
            case HangulComposer.V_YO:
            case HangulComposer.V_YEO:
            case HangulComposer.V_YU:
            case HangulComposer.V_YA:
            case HangulComposer.V_WA:
            case HangulComposer.V_WEO:
            case HangulComposer.V_EU:
                return hasVerticalComponent(leg.direction)
                        ? verticalComponent(leg.direction)
                        : leg.axis;
            default:
                return leg.axis;
        }
    }

    private boolean isOppositeDiagonal(Direction firstDirection, Direction nextDirection) {
        return (firstDirection == Direction.UP_RIGHT && nextDirection == Direction.DOWN_LEFT)
                || (firstDirection == Direction.DOWN_LEFT && nextDirection == Direction.UP_RIGHT)
                || (firstDirection == Direction.UP_LEFT && nextDirection == Direction.DOWN_RIGHT)
                || (firstDirection == Direction.DOWN_RIGHT && nextDirection == Direction.UP_LEFT);
    }

    public Trace trace(List<Point> points) {
        if (points.size() < 2) {
            return new Trace(0f, 0f, 0f, 0f, 0f, 0L);
        }
        Point start = points.get(0);
        Point end = points.get(points.size() - 1);
        float dx = xDistanceMm(start, end);
        float dy = yDistanceMm(start, end);
        float distance = (float) Math.hypot(dx, dy);
        float path = 0f;
        for (int i = 1; i < points.size(); i++) {
            path += pointsDistanceMm(points.get(i - 1), points.get(i));
        }
        long duration = (start.timeMs > 0L && end.timeMs >= start.timeMs) ? end.timeMs - start.timeMs : 0L;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        return new Trace(dx, dy, distance, angle, path, duration);
    }

    private Integer mapShortBacktrack(List<Point> points, Consonant consonant) {
        BacktrackMatch horizontal = findShortBacktrack(points, true);
        BacktrackMatch vertical = findShortBacktrack(points, false);
        if (horizontal.matched && (!vertical.matched || horizontal.strength >= vertical.strength)) {
            return HangulComposer.V_EU;
        }
        if (vertical.matched) {
            if (consonant != null) {
                return vertical.firstSign < 0 ? HangulComposer.V_OE : HangulComposer.V_WI;
            }
            return HangulComposer.V_I;
        }
        return null;
    }

    private BacktrackMatch findShortBacktrack(List<Point> points, boolean horizontal) {
        if (points == null || points.size() < 3) {
            return BacktrackMatch.NO_MATCH;
        }
        Point start = points.get(0);
        float firstMinMm = shortBacktrackFirstSegmentMinMm();
        float reverseMinMm = shortBacktrackReverseSegmentMinMm();
        int firstSign = 0;
        float extremeAxis = 0f;
        float firstLeg = 0f;
        float minPerp = 0f;
        float maxPerp = 0f;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float axis = horizontal ? xDistanceMm(start, point) : yDistanceMm(start, point);
            float perp = horizontal ? yDistanceMm(start, point) : xDistanceMm(start, point);
            minPerp = Math.min(minPerp, perp);
            maxPerp = Math.max(maxPerp, perp);
            if (firstSign == 0) {
                float absAxis = Math.abs(axis);
                float absPerp = Math.abs(perp);
                if (absAxis < firstMinMm || absAxis < absPerp * BACKTRACK_AXIS_DOMINANCE_RATIO) {
                    continue;
                }
                firstSign = axis >= 0f ? 1 : -1;
                extremeAxis = axis;
                firstLeg = absAxis;
                continue;
            }

            boolean extendedFirstLeg = firstSign > 0 ? axis > extremeAxis : axis < extremeAxis;
            if (extendedFirstLeg) {
                extremeAxis = axis;
                firstLeg = Math.abs(extremeAxis);
            }
        }

        if (firstSign == 0) {
            return BacktrackMatch.NO_MATCH;
        }
        if (!horizontal && firstLeg >= stableVerticalBacktrackBaseLimitMm()) {
            return BacktrackMatch.NO_MATCH;
        }
        Point end = points.get(points.size() - 1);
        float finalAxis = horizontal ? xDistanceMm(start, end) : yDistanceMm(start, end);
        float reverseAxis = firstSign > 0 ? extremeAxis - finalAxis : finalAxis - extremeAxis;
        if (reverseAxis < reverseMinMm) {
            return BacktrackMatch.NO_MATCH;
        }
        if (Math.abs(finalAxis) > shortBacktrackReturnLimitMm(firstLeg)) {
            return BacktrackMatch.NO_MATCH;
        }
        float perpSpan = maxPerp - minPerp;
        if (perpSpan > shortBacktrackPerpendicularLimitMm(Math.min(firstLeg, reverseAxis))) {
            return BacktrackMatch.NO_MATCH;
        }
        float strength = firstLeg + reverseAxis - perpSpan * 0.45f;
        return new BacktrackMatch(true, strength, firstSign);
    }

    private boolean isVertical(Direction direction) {
        return direction == Direction.UP || direction == Direction.DOWN;
    }

    private boolean hasVerticalComponent(Direction direction) {
        return direction == Direction.UP
                || direction == Direction.DOWN
                || direction == Direction.UP_RIGHT
                || direction == Direction.UP_LEFT
                || direction == Direction.DOWN_RIGHT
                || direction == Direction.DOWN_LEFT;
    }

    private boolean hasHorizontalComponent(Direction direction) {
        return direction == Direction.RIGHT
                || direction == Direction.LEFT
                || direction == Direction.UP_RIGHT
                || direction == Direction.DOWN_RIGHT
                || direction == Direction.UP_LEFT
                || direction == Direction.DOWN_LEFT;
    }

    private Direction verticalComponent(Direction direction) {
        return direction == Direction.UP
                || direction == Direction.UP_RIGHT
                || direction == Direction.UP_LEFT
                ? Direction.UP
                : Direction.DOWN;
    }

    private Direction horizontalComponent(Direction direction) {
        return direction == Direction.RIGHT
                || direction == Direction.UP_RIGHT
                || direction == Direction.DOWN_RIGHT
                ? Direction.RIGHT
                : Direction.LEFT;
    }

    private Direction oppositeCardinal(Direction direction) {
        if (direction == Direction.RIGHT) {
            return Direction.LEFT;
        }
        if (direction == Direction.LEFT) {
            return Direction.RIGHT;
        }
        if (direction == Direction.UP) {
            return Direction.DOWN;
        }
        if (direction == Direction.DOWN) {
            return Direction.UP;
        }
        return null;
    }

    private boolean isCardinal(Direction direction) {
        return direction == Direction.RIGHT
                || direction == Direction.LEFT
                || direction == Direction.UP
                || direction == Direction.DOWN;
    }

    private Integer mapSingleDirection(Direction direction, boolean longSingle) {
        switch (direction) {
            case RIGHT:
                return longSingle ? HangulComposer.V_EU : HangulComposer.V_A;
            case LEFT:
                return longSingle ? HangulComposer.V_EU : HangulComposer.V_EO;
            case UP:
                return longSingle ? HangulComposer.V_I : HangulComposer.V_O;
            case DOWN:
                return longSingle ? HangulComposer.V_I : HangulComposer.V_U;
            case DOWN_RIGHT:
                return HangulComposer.V_YA;
            case UP_LEFT:
                return HangulComposer.V_YEO;
            case UP_RIGHT:
                return HangulComposer.V_YO;
            case DOWN_LEFT:
                return HangulComposer.V_YU;
            default:
                return null;
        }
    }

    private float firstSegmentMinMm() {
        return Math.max(0.5f, firstSegmentMm * 0.55f);
    }

    private float derivationSegmentMinMm() {
        return Math.max(firstSegmentMinMm() + 0.15f, derivationSegmentMm * 0.55f);
    }

    private float strictDerivationSegmentMinMm() {
        return Math.max(derivationSegmentMinMm(), derivationSegmentMm);
    }

    private float shortBacktrackFirstSegmentMinMm() {
        return Math.max(0.8f, firstSegmentMinMm());
    }

    private float stableVerticalBacktrackBaseLimitMm() {
        return Math.max(firstSegmentMinMm() + 0.5f, firstSegmentMinMm() * 1.35f);
    }

    private float firstCardinalIntentMinMm() {
        return Math.max(0.8f, firstSegmentMinMm() * 0.5f);
    }

    private float shortReverseDerivationMinMm() {
        return Math.max(0.8f, firstSegmentMinMm() * 0.65f);
    }

    private float shortBacktrackReverseSegmentMinMm() {
        return Math.max(shortBacktrackFirstSegmentMinMm() + 0.15f, derivationSegmentMinMm());
    }

    private float shortBacktrackReturnLimitMm(float firstLegMm) {
        return Math.max(0.9f, Math.min(firstSegmentMinMm(), firstLegMm * 0.65f));
    }

    private float shortBacktrackPerpendicularLimitMm(float axisLegMm) {
        return Math.max(0.9f, Math.min(firstSegmentMinMm(), axisLegMm * 0.60f));
    }

    private boolean isLongGesture(Direction direction, float distanceMm, Consonant consonant) {
        return distanceMm > longGestureThreshold(direction, consonant);
    }

    private float longGestureThreshold(Direction direction, Consonant consonant) {
        if (calibrationProfile != null) {
            if (direction == Direction.RIGHT || direction == Direction.LEFT) {
                return calibrationProfile.longHorizontalMm(activeCalibrationKey, consonant, longGestureMm);
            }
            if (direction == Direction.UP || direction == Direction.DOWN) {
                return calibrationProfile.longVerticalMm(activeCalibrationKey, consonant, longGestureMm);
            }
        }
        return longGestureMm;
    }

    private Direction directionFor(float dx, float dy, Consonant consonant) {
        float angle = angleFor(dx, dy);
        Direction calibrated = calibratedDiagonalDirection(angle, (float) Math.hypot(dx, dy), consonant);
        Direction fallback = dominantAxisDirection(dx, dy);
        return calibrated == null ? fallback : calibrated;
    }

    private float angleFor(float dx, float dy) {
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    private Direction dominantAxisDirection(float dx, float dy) {
        float absDx = Math.abs(dx);
        float absDy = Math.abs(dy);
        if (absDx >= absDy * CARDINAL_DOMINANCE_RATIO) {
            return dx >= 0 ? Direction.RIGHT : Direction.LEFT;
        }
        if (absDy >= absDx * CARDINAL_DOMINANCE_RATIO) {
            return dy >= 0 ? Direction.DOWN : Direction.UP;
        }
        if (dx >= 0 && dy >= 0) {
            return Direction.DOWN_RIGHT;
        }
        if (dx >= 0) {
            return Direction.UP_RIGHT;
        }
        if (dy >= 0) {
            return Direction.DOWN_LEFT;
        }
        return Direction.UP_LEFT;
    }

    private Direction dominantCardinalAxisDirection(float dx, float dy) {
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? Direction.RIGHT : Direction.LEFT;
        }
        return dy >= 0 ? Direction.DOWN : Direction.UP;
    }

    private Direction calibratedDiagonalDirection(float angle, float distanceMm, Consonant consonant) {
        if (calibrationProfile == null) {
            return null;
        }
        Direction bestDirection = null;
        float bestRatio = Float.MAX_VALUE;
        for (GestureCalibration.DirectionClass directionClass : diagonalClasses()) {
            GestureCalibration.DirectionProfile profile = calibrationProfile.directionProfile(
                    activeCalibrationKey, consonant, directionClass);
            if (profile == null || distanceMm < profile.minDistanceMm) {
                continue;
            }
            float diff = Math.abs(GestureCalibration.angleDiff(angle, profile.centerAngle));
            float ratio = diff / Math.max(1f, profile.tolerance);
            if (ratio <= 1f && ratio < bestRatio) {
                bestRatio = ratio;
                bestDirection = directionForClass(directionClass);
            }
        }
        return bestDirection;
    }

    private GestureCalibration.DirectionClass[] diagonalClasses() {
        return new GestureCalibration.DirectionClass[]{
                GestureCalibration.DirectionClass.TOP_RIGHT,
                GestureCalibration.DirectionClass.TOP_LEFT,
                GestureCalibration.DirectionClass.BOTTOM_RIGHT,
                GestureCalibration.DirectionClass.BOTTOM_LEFT
        };
    }

    private Direction directionForClass(GestureCalibration.DirectionClass directionClass) {
        switch (directionClass) {
            case TOP_RIGHT:
                return Direction.UP_RIGHT;
            case TOP_LEFT:
                return Direction.UP_LEFT;
            case BOTTOM_RIGHT:
                return Direction.DOWN_RIGHT;
            case BOTTOM_LEFT:
                return Direction.DOWN_LEFT;
            default:
                return Direction.RIGHT;
        }
    }

    private boolean reasonableDpi(float dpi) {
        return dpi >= 80f && dpi <= 900f;
    }

    private float xDistanceMm(Point first, Point second) {
        return (second.x - first.x) / xPixelsPerMm;
    }

    private float yDistanceMm(Point first, Point second) {
        return (second.y - first.y) / yPixelsPerMm;
    }

    private float pointsDistanceMm(Point first, Point second) {
        return (float) Math.hypot(xDistanceMm(first, second), yDistanceMm(first, second));
    }

    public static class Point {
        public final float x;
        public final float y;
        public final long timeMs;

        public Point(float x, float y) {
            this(x, y, 0L);
        }

        public Point(float x, float y, long timeMs) {
            this.x = x;
            this.y = y;
            this.timeMs = timeMs;
        }
    }

    public static class Trace {
        public final float dxMm;
        public final float dyMm;
        public final float distanceMm;
        public final float angleDegrees;
        public final float pathMm;
        public final long durationMs;

        Trace(float dxMm, float dyMm, float distanceMm, float angleDegrees, float pathMm, long durationMs) {
            this.dxMm = dxMm;
            this.dyMm = dyMm;
            this.distanceMm = distanceMm;
            this.angleDegrees = angleDegrees;
            this.pathMm = pathMm;
            this.durationMs = durationMs;
        }
    }

    private static class StrokeLeg {
        final Direction direction;
        final Direction axis;
        final float distanceMm;
        final Point endPoint;

        StrokeLeg(Direction direction, Direction axis, float distanceMm) {
            this(direction, axis, distanceMm, null);
        }

        StrokeLeg(Direction direction, Direction axis, float distanceMm, Point endPoint) {
            this.direction = direction;
            this.axis = axis;
            this.distanceMm = distanceMm;
            this.endPoint = endPoint;
        }
    }

    private static class StableCardinal {
        final Direction direction;
        final Point point;

        StableCardinal(Direction direction, Point point) {
            this.direction = direction;
            this.point = point;
        }
    }

    private static class BacktrackMatch {
        static final BacktrackMatch NO_MATCH = new BacktrackMatch(false, 0f, 0);

        final boolean matched;
        final float strength;
        final int firstSign;

        BacktrackMatch(boolean matched, float strength, int firstSign) {
            this.matched = matched;
            this.strength = strength;
            this.firstSign = firstSign;
        }
    }
}
