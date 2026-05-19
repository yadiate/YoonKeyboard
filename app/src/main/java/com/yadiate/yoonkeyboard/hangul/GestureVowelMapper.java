package com.yadiate.yoonkeyboard.hangul;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

public class GestureVowelMapper {
    public enum Direction {
        RIGHT, LEFT, UP, DOWN, DOWN_RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT
    }

    private static final float REFERENCE_VOWEL_TOLERANCE_DEGREES =
            GestureCalibration.DEFAULT_DIAGONAL_TOLERANCE_DEGREES;
    private static final float CARDINAL_DOMINANCE_RATIO = 2.4142137f;
    private static final float DIAGONAL_CONTINUATION_MAX_ANGLE_CHANGE_DEGREES = REFERENCE_VOWEL_TOLERANCE_DEGREES;
    private static final float SMOOTH_DIAGONAL_MAX_PATH_RATIO = 1.32f;
    private static final float SMOOTH_DIAGONAL_MAX_CARDINAL_ANGLE_CHANGE_DEGREES = REFERENCE_VOWEL_TOLERANCE_DEGREES;
    private static final float CLEAR_CORNER_MIN_FIRST_STROKE_RATIO = 0.78f;
    private static final float CLEAR_CORNER_MIN_PATH_RATIO = 1.14f;
    private static final float CLEAR_CORNER_MIN_ANGLE_CHANGE_DEGREES = 12f;
    private static final float EARLY_DIRECTION_SAMPLE_MM = 1.2f;
    private static final float YEO_SMOOTH_MIN_SCORE = 0.66f;
    private static final float E_CORNER_WIN_MARGIN = 0.02f;
    private static final float CURVED_E_MIN_SCORE_DOWN_LEFT = 0.44f;
    private static final float CURVED_E_MIN_SCORE_UP_LEFT = 0.54f;
    private static final float BACKTRACK_AXIS_DOMINANCE_RATIO = 1.15f;
    private static final float MIN_LONG_GESTURE_MM = 5f;
    private static final float MIN_SHORT_LONG_GAP_MM = 1f;
    private static final float LONG_CARDINAL_AXIS_WEIGHT = 0.62f;
    private static final float LONG_CARDINAL_LENGTH_BONUS = 0.42f;
    private static final float LONG_CARDINAL_LENGTH_MARGIN_WEIGHT = 0.18f;
    private static final float DIAGONAL_SCORE_WEIGHT = 0.90f;
    private static final float DIAGONAL_DIRECTION_BONUS = 0.10f;
    private static final float LONG_CARDINAL_WIN_MARGIN = 0.03f;

    private float xPixelsPerMm = 1f;
    private float yPixelsPerMm = 1f;
    private float minSegmentMm = 3.6f;
    private float longGestureMm = 13.5f;
    private GestureCalibration.Profile calibrationProfile;

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
        this.longGestureMm = Math.max(MIN_LONG_GESTURE_MM, longGestureMm);
        this.minSegmentMm = Math.max(0.5f,
                Math.min(minSegmentMm, this.longGestureMm - MIN_SHORT_LONG_GAP_MM));
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
        if (points.size() < 2) {
            return null;
        }

        List<Direction> directions = new ArrayList<>();
        Point start = points.get(0);
        Point anchor = start;
        Direction firstDirection = null;
        float firstDirectionAngle = Float.NaN;
        float firstDirectionDistance = 0f;
        float firstSegmentMinMm = firstSegmentMinMm();
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float dx = xDistanceMm(anchor, point);
            float dy = yDistanceMm(anchor, point);
            float segmentMinMm = shouldResetStrokeLengthForNextDirection(directions)
                    ? firstSegmentMinMm
                    : minSegmentMm;
            if (Math.hypot(dx, dy) < segmentMinMm) {
                continue;
            }
            Direction direction = directionFor(dx, dy, consonant);
            if (directions.isEmpty() || directions.get(directions.size() - 1) != direction) {
                directions.add(direction);
            }
            if (firstDirection == null) {
                firstDirection = direction;
                firstDirectionAngle = angleFor(xDistanceMm(start, point), yDistanceMm(start, point));
            }
            if (direction == firstDirection) {
                firstDirectionDistance = pointsDistanceMm(start, point);
            }
            anchor = point;
        }

        Point end = points.get(points.size() - 1);
        float totalDistance = pointsDistanceMm(start, end);
        float overallDx = xDistanceMm(start, end);
        float overallDy = yDistanceMm(start, end);
        Direction overallDirection = directionFor(overallDx, overallDy, consonant);
        float overallAngle = angleFor(overallDx, overallDy);
        boolean longSingle = isLongGesture(overallDirection, totalDistance, consonant);
        Integer shortBacktrack = mapShortBacktrack(points);
        if (shortBacktrack != null) {
            return shortBacktrack;
        }

        Integer overall = mapOverallDirection(start, end, totalDistance, longSingle, consonant);
        if (directions.isEmpty()) {
            return overall;
        }

        boolean longFirst = isLongGesture(firstDirection, firstDirectionDistance, consonant);
        float pathRatio = coarsePathDistanceMm(points) / Math.max(totalDistance, 0.001f);
        Integer longCardinal = mapScoredLongCardinalCandidate(overallDx, overallDy, totalDistance, pathRatio,
                firstDirection, firstDirectionAngle, firstDirectionDistance, directions, overallAngle, consonant);
        if (longCardinal != null) {
            return longCardinal;
        }
        Integer curvedE = mapCurvedLeftVerticalToE(points, overallDx, overallDy, totalDistance, pathRatio,
                firstDirection, firstDirectionAngle, firstDirectionDistance, directions, overallDirection);
        if (curvedE != null) {
            return curvedE;
        }
        Integer smoothDiagonal = mapSmoothDiagonalTrace(points, overallDx, overallDy, totalDistance, pathRatio,
                firstDirection, firstDirectionAngle, firstDirectionDistance, directions, longSingle, consonant);
        if (smoothDiagonal != null) {
            return smoothDiagonal;
        }
        Integer mapped = mapDirections(directions, longSingle, longFirst);
        if (mapped != null) {
            return mapped;
        }
        Integer diagonalContinuation = mapStableDiagonalContinuation(firstDirection, firstDirectionAngle,
                firstDirectionDistance, directions, pathRatio, overallDirection, overallAngle, longSingle);
        if (diagonalContinuation != null) {
            return diagonalContinuation;
        }
        if (isCardinal(firstDirection)) {
            return mapSingleDirection(firstDirection, longFirst);
        }
        return overall;
    }

    private Integer mapScoredLongCardinalCandidate(float dx, float dy, float totalDistanceMm, float pathRatio,
                                                   Direction firstDirection, float firstDirectionAngle,
                                                   float firstDirectionDistance, List<Direction> directions,
                                                   float overallAngle, Consonant consonant) {
        Direction longDirection = dominantCardinalAxisDirection(dx, dy);
        float longThreshold = longGestureThreshold(longDirection, consonant);
        if (totalDistanceMm <= longThreshold) {
            return null;
        }
        if (pathRatio > SMOOTH_DIAGONAL_MAX_PATH_RATIO) {
            return null;
        }
        if (isCardinal(firstDirection) && !Float.isNaN(firstDirectionAngle)) {
            float angleChange = Math.abs(GestureCalibration.angleDiff(firstDirectionAngle, overallAngle));
            if (hasClearCorner(firstDirection, firstDirectionDistance, directions, angleChange, pathRatio)) {
                return null;
            }
        }

        float lengthMargin = clamp((totalDistanceMm - longThreshold) / Math.max(1f, longThreshold), 0f, 1f);
        float longScore = axisConfidence(dx, dy) * LONG_CARDINAL_AXIS_WEIGHT
                + LONG_CARDINAL_LENGTH_BONUS
                + lengthMargin * LONG_CARDINAL_LENGTH_MARGIN_WEIGHT;
        float diagonalScore = diagonalScore(overallAngle, directionFor(dx, dy, consonant), consonant);
        return longScore > diagonalScore + LONG_CARDINAL_WIN_MARGIN
                ? mapSingleDirection(longDirection, true)
                : null;
    }

    private Integer mapCurvedLeftVerticalToE(List<Point> points, float dx, float dy, float totalDistanceMm,
                                             float pathRatio, Direction firstDirection, float firstDirectionAngle,
                                             float firstDirectionDistance, List<Direction> directions,
                                             Direction overallDirection) {
        if (dx >= -firstSegmentMinMm()
                || Math.abs(dy) < firstSegmentMinMm() * 0.45f
                || totalDistanceMm < firstSegmentMinMm()) {
            return null;
        }
        boolean leftDiagonal = overallDirection == Direction.DOWN_LEFT
                || overallDirection == Direction.UP_LEFT
                || firstDirection == Direction.DOWN_LEFT
                || firstDirection == Direction.UP_LEFT;
        if (!leftDiagonal) {
            return null;
        }

        float horizontalHalfPath = axisReachPathFraction(points, true, 0.50f, dx, dy);
        float verticalHalfPath = axisReachPathFraction(points, false, 0.50f, dx, dy);
        float horizontalLead = verticalHalfPath - horizontalHalfPath;
        float earlyHorizontal = axisProgressAtPathFraction(points, true, 0.30f, dx, dy);
        float earlyVertical = axisProgressAtPathFraction(points, false, 0.30f, dx, dy);
        float earlyAngle = angleAtDistanceMm(points, earlyDirectionSampleMm());
        float angleChange = Float.isNaN(firstDirectionAngle)
                ? 0f
                : Math.abs(GestureCalibration.angleDiff(firstDirectionAngle, angleFor(dx, dy)));

        Direction second = directions.size() > 1 ? directions.get(1) : null;
        float leadScore = clamp((horizontalLead - 0.04f) / 0.22f, 0f, 1f);
        float earlyLeadScore = clamp((earlyHorizontal - earlyVertical - 0.10f) / 0.36f, 0f, 1f);
        float pathScore = clamp((pathRatio - 1.05f) / 0.26f, 0f, 1f);
        float turnScore = clamp((angleChange - 6f) / 32f, 0f, 1f);
        float firstLeftScore = firstDirection == Direction.LEFT
                ? 1f
                : (Float.isNaN(earlyAngle) ? 0f : angleCloseness(earlyAngle, 180f, 52f));
        float sequenceScore = firstDirection == Direction.LEFT && second != null && isVerticalExtension(second)
                ? 1f
                : 0f;
        float score = leadScore * 0.30f
                + earlyLeadScore * 0.28f
                + pathScore * 0.18f
                + turnScore * 0.12f
                + firstLeftScore * 0.08f
                + sequenceScore * 0.04f;
        float threshold = dy >= 0f ? CURVED_E_MIN_SCORE_DOWN_LEFT : CURVED_E_MIN_SCORE_UP_LEFT;
        return score >= threshold ? HangulComposer.V_E : null;
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

    private Integer mapOverallDirection(Point start, Point end, float totalDistanceMm, boolean longSingle,
                                        Consonant consonant) {
        float fallbackMinMm = firstSegmentMinMm();
        if (totalDistanceMm < fallbackMinMm) {
            return null;
        }
        float totalDx = xDistanceMm(start, end);
        float totalDy = yDistanceMm(start, end);
        List<Direction> overallDirection = new ArrayList<>();
        overallDirection.add(directionFor(totalDx, totalDy, consonant));
        return mapDirections(overallDirection, longSingle, longSingle);
    }

    private Integer mapDirections(List<Direction> directions, boolean longSingle, boolean longFirst) {
        Direction first = directions.get(0);
        Direction second = directions.size() > 1 ? directions.get(1) : null;
        Direction third = directions.size() > 2 ? directions.get(2) : null;

        if (directions.size() == 1) {
            return mapSingleDirection(first, longSingle);
        }

        if (isHorizontalBacktrack(first, second)) {
            return HangulComposer.V_EU;
        }
        if (isVerticalBacktrack(first, second)) {
            return HangulComposer.V_I;
        }
        if (is(first, Direction.RIGHT) && isVerticalExtension(second)) {
            return longFirst ? HangulComposer.V_YI : HangulComposer.V_AE;
        }
        if (is(first, Direction.LEFT) && isVerticalExtension(second)) {
            return longFirst ? HangulComposer.V_YI : HangulComposer.V_E;
        }
        if (is(first, Direction.DOWN_RIGHT) && isVerticalExtension(second)) {
            return HangulComposer.V_YAE;
        }
        if (is(first, Direction.UP_LEFT) && isVerticalExtension(second)) {
            return HangulComposer.V_YE;
        }
        if (is(first, Direction.UP) && isRightward(second) && (isDownward(second) || isDownward(third))) {
            return HangulComposer.V_WAE;
        }
        if (is(first, Direction.UP) && isRightward(second)) {
            return HangulComposer.V_WA;
        }
        if (is(first, Direction.UP) && isDownward(second)) {
            return HangulComposer.V_OE;
        }
        if (is(first, Direction.DOWN) && isLeftward(second) && (isUpward(second) || isUpward(third))) {
            return HangulComposer.V_WE;
        }
        if (is(first, Direction.DOWN) && isUpward(second)) {
            return HangulComposer.V_WI;
        }
        if (is(first, Direction.DOWN) && isLeftward(second)) {
            return HangulComposer.V_WEO;
        }
        return null;
    }

    private Integer mapShortBacktrack(List<Point> points) {
        BacktrackMatch horizontal = findShortBacktrack(points, true);
        BacktrackMatch vertical = findShortBacktrack(points, false);
        if (horizontal.matched && (!vertical.matched || horizontal.score >= vertical.score)) {
            return HangulComposer.V_EU;
        }
        if (vertical.matched) {
            return HangulComposer.V_I;
        }
        return null;
    }

    private BacktrackMatch findShortBacktrack(List<Point> points, boolean horizontal) {
        if (points == null || points.size() < 3) {
            return BacktrackMatch.NO_MATCH;
        }
        Point start = points.get(0);
        float minMm = shortBacktrackSegmentMinMm();
        int firstSign = 0;
        float extremeAxis = 0f;
        float extremePerp = 0f;
        float firstLeg = 0f;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float axis = horizontal ? xDistanceMm(start, point) : yDistanceMm(start, point);
            float perp = horizontal ? yDistanceMm(start, point) : xDistanceMm(start, point);
            if (firstSign == 0) {
                float absAxis = Math.abs(axis);
                float absPerp = Math.abs(perp);
                if (absAxis < minMm || absAxis < absPerp * BACKTRACK_AXIS_DOMINANCE_RATIO) {
                    continue;
                }
                firstSign = axis >= 0f ? 1 : -1;
                extremeAxis = axis;
                extremePerp = perp;
                firstLeg = absAxis;
                continue;
            }

            boolean extendedFirstLeg = firstSign > 0 ? axis > extremeAxis : axis < extremeAxis;
            if (extendedFirstLeg) {
                extremeAxis = axis;
                extremePerp = perp;
                firstLeg = Math.abs(extremeAxis);
                continue;
            }

            float reverseAxis = firstSign > 0 ? extremeAxis - axis : axis - extremeAxis;
            float reversePerp = Math.abs(perp - extremePerp);
            if (reverseAxis >= minMm && reverseAxis >= reversePerp * BACKTRACK_AXIS_DOMINANCE_RATIO) {
                float score = firstLeg + reverseAxis - reversePerp * 0.35f;
                return new BacktrackMatch(true, score);
            }
        }
        return BacktrackMatch.NO_MATCH;
    }

    private boolean is(Direction actual, Direction expected) {
        return actual == expected;
    }

    private boolean isHorizontalBacktrack(Direction first, Direction second) {
        return (first == Direction.RIGHT && second == Direction.LEFT)
                || (first == Direction.LEFT && second == Direction.RIGHT);
    }

    private boolean isVerticalBacktrack(Direction first, Direction second) {
        return (first == Direction.UP && second == Direction.DOWN)
                || (first == Direction.DOWN && second == Direction.UP);
    }

    private boolean isVertical(Direction direction) {
        return direction == Direction.UP || direction == Direction.DOWN;
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

    private Integer mapStableDiagonalContinuation(Direction firstDirection, float firstDirectionAngle,
                                                  float firstDirectionDistance, List<Direction> directions,
                                                  float pathRatio, Direction overallDirection, float overallAngle,
                                                  boolean longSingle) {
        if (!isCardinal(firstDirection)
                || overallDirection == null
                || isCardinal(overallDirection)
                || Float.isNaN(firstDirectionAngle)) {
            return null;
        }
        float angleChange = Math.abs(GestureCalibration.angleDiff(firstDirectionAngle, overallAngle));
        if (angleChange > DIAGONAL_CONTINUATION_MAX_ANGLE_CHANGE_DEGREES) {
            return null;
        }
        if (hasClearCorner(firstDirection, firstDirectionDistance, directions, angleChange, pathRatio)) {
            return null;
        }
        return mapSingleDirection(overallDirection, longSingle);
    }

    private Integer mapSmoothDiagonalTrace(List<Point> points, float dx, float dy, float totalDistanceMm,
                                           float pathRatio,
                                           Direction firstDirection, float firstDirectionAngle,
                                           float firstDirectionDistance, List<Direction> directions,
                                           boolean longSingle, Consonant consonant) {
        if (firstDirection == null
                || !isCardinal(firstDirection)
                || totalDistanceMm < firstSegmentMinMm()
                || pathRatio > SMOOTH_DIAGONAL_MAX_PATH_RATIO) {
            return null;
        }
        Direction diagonal = referenceTransitionDiagonalDirection(firstDirection, angleFor(dx, dy),
                totalDistanceMm, consonant);
        if (diagonal == null) {
            return null;
        }
        if (Float.isNaN(firstDirectionAngle)) {
            return null;
        }
        float angleChange = Math.abs(GestureCalibration.angleDiff(firstDirectionAngle, angleFor(dx, dy)));
        if (angleChange > SMOOTH_DIAGONAL_MAX_CARDINAL_ANGLE_CHANGE_DEGREES
                && firstDirectionDistance > earlyDirectionSampleMm()) {
            return null;
        }
        if (diagonal == Direction.UP_LEFT) {
            float smoothScore = smoothDiagonalIntentScore(points, diagonal, angleFor(dx, dy),
                    firstDirectionAngle, pathRatio, consonant);
            float cornerScore = leftEIntentCornerScore(points, firstDirection, firstDirectionAngle,
                    firstDirectionDistance, directions, pathRatio, angleChange);
            if (cornerScore >= smoothScore - E_CORNER_WIN_MARGIN || smoothScore < YEO_SMOOTH_MIN_SCORE) {
                return null;
            }
        }
        if (hasClearCorner(firstDirection, firstDirectionDistance, directions, angleChange, pathRatio)) {
            return null;
        }
        return mapSingleDirection(diagonal, longSingle);
    }

    private float smoothDiagonalIntentScore(List<Point> points, Direction diagonal, float overallAngle,
                                            float firstDirectionAngle, float pathRatio, Consonant consonant) {
        GestureCalibration.DirectionClass directionClass = classForDirection(diagonal);
        float centerAngle = calibratedCenterAngle(consonant, directionClass, diagonal);
        float tolerance = diagonalToleranceDegrees(consonant, directionClass);
        float overallScore = angleCloseness(overallAngle, centerAngle, tolerance);
        float earlyAngle = angleAtDistanceMm(points, earlyDirectionSampleMm());
        float earlyScore = Float.isNaN(earlyAngle)
                ? overallScore
                : angleCloseness(earlyAngle, centerAngle, tolerance + 10f);
        float straightScore = 1f - clamp((pathRatio - 1.02f) / 0.30f, 0f, 1f);
        float turnScore = Float.isNaN(firstDirectionAngle)
                ? 1f
                : 1f - clamp(Math.abs(GestureCalibration.angleDiff(firstDirectionAngle, overallAngle))
                / 48f, 0f, 1f);
        return overallScore * 0.34f
                + earlyScore * 0.30f
                + straightScore * 0.22f
                + turnScore * 0.14f;
    }

    private float leftEIntentCornerScore(List<Point> points, Direction firstDirection, float firstDirectionAngle,
                                         float firstDirectionDistance, List<Direction> directions,
                                         float pathRatio, float angleChange) {
        if (firstDirection != Direction.LEFT) {
            return 0f;
        }
        Direction second = directions.size() > 1 ? directions.get(1) : null;
        float sequenceScore = second != null && isVerticalExtension(second) ? 1f : 0f;
        float firstStrokeScore = clamp((firstDirectionDistance - earlyDirectionSampleMm())
                / Math.max(0.8f, minSegmentMm * 0.65f), 0f, 1f);
        float pathScore = clamp((pathRatio - 1.08f) / 0.24f, 0f, 1f);
        float turnScore = clamp((angleChange - 8f) / 30f, 0f, 1f);
        float earlyAngle = angleAtDistanceMm(points, earlyDirectionSampleMm());
        float earlyLeftScore = Float.isNaN(earlyAngle) ? 0f : angleCloseness(earlyAngle, 180f, 45f);
        return sequenceScore * 0.32f
                + firstStrokeScore * 0.24f
                + pathScore * 0.22f
                + turnScore * 0.16f
                + earlyLeftScore * 0.06f;
    }

    private Direction referenceTransitionDiagonalDirection(Direction firstDirection, float angle,
                                                          float distanceMm, Consonant consonant) {
        Direction expected = continuationDiagonalFor(firstDirection);
        if (expected == null) {
            return null;
        }
        Direction calibrated = calibratedDiagonalDirection(angle, distanceMm, consonant);
        if (calibrated == expected) {
            return calibrated;
        }
        GestureCalibration.DirectionClass expectedClass = classForDirection(expected);
        float centerAngle = calibratedCenterAngle(consonant, expectedClass, expected);
        float diff = Math.abs(GestureCalibration.angleDiff(angle, centerAngle));
        return diff <= diagonalToleranceDegrees(consonant, expectedClass) ? expected : null;
    }

    private Direction continuationDiagonalFor(Direction firstDirection) {
        switch (firstDirection) {
            case RIGHT:
                return Direction.DOWN_RIGHT;
            case LEFT:
                return Direction.UP_LEFT;
            case UP:
                return Direction.UP_RIGHT;
            case DOWN:
                return Direction.DOWN_LEFT;
            default:
                return null;
        }
    }

    private float referenceCenterAngle(Direction direction) {
        switch (direction) {
            case DOWN_RIGHT:
                return 45f;
            case UP_LEFT:
                return -135f;
            case UP_RIGHT:
                return -45f;
            case DOWN_LEFT:
                return 135f;
            default:
                return 0f;
        }
    }

    private float angleCloseness(float angle, float centerAngle, float toleranceDegrees) {
        float diff = Math.abs(GestureCalibration.angleDiff(angle, centerAngle));
        return 1f - clamp(Math.min(diff, toleranceDegrees) / toleranceDegrees, 0f, 1f);
    }

    private float angleAtDistanceMm(List<Point> points, float targetDistanceMm) {
        if (points == null || points.size() < 2) {
            return Float.NaN;
        }
        Point start = points.get(0);
        Point candidate = points.get(points.size() - 1);
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            if (pointsDistanceMm(start, point) >= targetDistanceMm) {
                candidate = point;
                break;
            }
        }
        float dx = xDistanceMm(start, candidate);
        float dy = yDistanceMm(start, candidate);
        if (Math.hypot(dx, dy) < 0.001f) {
            return Float.NaN;
        }
        return angleFor(dx, dy);
    }

    private float axisReachPathFraction(List<Point> points, boolean horizontal, float axisProgress,
                                        float totalDx, float totalDy) {
        if (points == null || points.size() < 2) {
            return 1f;
        }
        float totalAxis = horizontal ? Math.abs(totalDx) : Math.abs(totalDy);
        if (totalAxis < 0.001f) {
            return 1f;
        }
        float totalPath = rawPathDistanceMm(points);
        if (totalPath < 0.001f) {
            return 1f;
        }
        Point start = points.get(0);
        Point previous = start;
        float path = 0f;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            path += pointsDistanceMm(previous, point);
            float currentAxis = horizontal
                    ? Math.abs(xDistanceMm(start, point))
                    : Math.abs(yDistanceMm(start, point));
            if (currentAxis / totalAxis >= axisProgress) {
                return clamp(path / totalPath, 0f, 1f);
            }
            previous = point;
        }
        return 1f;
    }

    private float axisProgressAtPathFraction(List<Point> points, boolean horizontal, float pathFraction,
                                             float totalDx, float totalDy) {
        if (points == null || points.size() < 2) {
            return 0f;
        }
        float totalAxis = horizontal ? Math.abs(totalDx) : Math.abs(totalDy);
        if (totalAxis < 0.001f) {
            return 0f;
        }
        float totalPath = rawPathDistanceMm(points);
        if (totalPath < 0.001f) {
            return 0f;
        }
        float targetPath = totalPath * clamp(pathFraction, 0f, 1f);
        Point start = points.get(0);
        Point previous = start;
        float path = 0f;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float segment = pointsDistanceMm(previous, point);
            if (path + segment >= targetPath) {
                float t = segment < 0.001f ? 1f : clamp((targetPath - path) / segment, 0f, 1f);
                float interpolatedX = previous.x + (point.x - previous.x) * t;
                float interpolatedY = previous.y + (point.y - previous.y) * t;
                Point interpolated = new Point(interpolatedX, interpolatedY, point.timeMs);
                float currentAxis = horizontal
                        ? Math.abs(xDistanceMm(start, interpolated))
                        : Math.abs(yDistanceMm(start, interpolated));
                return clamp(currentAxis / totalAxis, 0f, 1f);
            }
            path += segment;
            previous = point;
        }
        return 1f;
    }

    private float rawPathDistanceMm(List<Point> points) {
        float path = 0f;
        for (int i = 1; i < points.size(); i++) {
            path += pointsDistanceMm(points.get(i - 1), points.get(i));
        }
        return path;
    }

    private float earlyDirectionSampleMm() {
        return Math.max(EARLY_DIRECTION_SAMPLE_MM, firstSegmentMinMm());
    }

    private boolean hasClearCorner(Direction firstDirection, float firstDirectionDistance,
                                   List<Direction> directions, float angleChange, float pathRatio) {
        if (firstDirectionDistance < minSegmentMm * CLEAR_CORNER_MIN_FIRST_STROKE_RATIO
                || angleChange < CLEAR_CORNER_MIN_ANGLE_CHANGE_DEGREES
                || pathRatio < CLEAR_CORNER_MIN_PATH_RATIO) {
            return false;
        }
        if (directions.size() < 2) {
            return true;
        }
        Direction second = directions.get(1);
        return (is(firstDirection, Direction.LEFT) || is(firstDirection, Direction.RIGHT))
                ? isVerticalExtension(second)
                : isHorizontalExtension(second);
    }

    private boolean isVerticalExtension(Direction direction) {
        return direction == Direction.UP
                || direction == Direction.DOWN
                || direction == Direction.UP_RIGHT
                || direction == Direction.DOWN_RIGHT
                || direction == Direction.UP_LEFT
                || direction == Direction.DOWN_LEFT;
    }

    private boolean isHorizontalExtension(Direction direction) {
        return direction == Direction.LEFT
                || direction == Direction.RIGHT
                || direction == Direction.UP_RIGHT
                || direction == Direction.DOWN_RIGHT
                || direction == Direction.UP_LEFT
                || direction == Direction.DOWN_LEFT;
    }

    private boolean shouldResetStrokeLengthForNextDirection(List<Direction> directions) {
        if (directions.isEmpty()) {
            return true;
        }
        if (directions.size() < 2) {
            return false;
        }
        Direction first = directions.get(0);
        Direction second = directions.get(1);
        return (is(first, Direction.UP) && isRightward(second))
                || (is(first, Direction.DOWN) && isLeftward(second));
    }

    private boolean isRightward(Direction direction) {
        return direction == Direction.RIGHT
                || direction == Direction.UP_RIGHT
                || direction == Direction.DOWN_RIGHT;
    }

    private boolean isLeftward(Direction direction) {
        return direction == Direction.LEFT
                || direction == Direction.UP_LEFT
                || direction == Direction.DOWN_LEFT;
    }

    private boolean isDownward(Direction direction) {
        return direction == Direction.DOWN
                || direction == Direction.DOWN_RIGHT
                || direction == Direction.DOWN_LEFT;
    }

    private boolean isUpward(Direction direction) {
        return direction == Direction.UP
                || direction == Direction.UP_RIGHT
                || direction == Direction.UP_LEFT;
    }

    private float firstSegmentMinMm() {
        return Math.max(1f, minSegmentMm * 0.55f);
    }

    private float shortBacktrackSegmentMinMm() {
        return Math.max(0.5f, minSegmentMm);
    }

    private boolean isLongGesture(Direction direction, float distanceMm, Consonant consonant) {
        return distanceMm > longGestureThreshold(direction, consonant);
    }

    private float longGestureThreshold(Direction direction, Consonant consonant) {
        if (calibrationProfile != null) {
            if (direction == Direction.RIGHT || direction == Direction.LEFT) {
                return calibrationProfile.longHorizontalMm(consonant, longGestureMm);
            }
            if (direction == Direction.UP || direction == Direction.DOWN) {
                return calibrationProfile.longVerticalMm(consonant, longGestureMm);
            }
        }
        return longGestureMm;
    }

    private Direction directionFor(float dx, float dy) {
        return directionFor(dx, dy, null);
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

    private float axisConfidence(float dx, float dy) {
        float major = Math.max(Math.abs(dx), Math.abs(dy));
        if (major <= 0f) {
            return 0f;
        }
        float minor = Math.min(Math.abs(dx), Math.abs(dy));
        return clamp((major - minor) / major, 0f, 1f);
    }

    private float diagonalScore(float angle, Direction mappedDirection, Consonant consonant) {
        float bestCloseness = 0f;
        for (GestureCalibration.DirectionClass directionClass : diagonalClasses()) {
            Direction direction = directionForClass(directionClass);
            float centerAngle = calibratedCenterAngle(consonant, directionClass, direction);
            float tolerance = diagonalToleranceDegrees(consonant, directionClass);
            float diff = Math.abs(GestureCalibration.angleDiff(angle, centerAngle));
            float closeness = 1f - Math.min(diff, tolerance) / tolerance;
            bestCloseness = Math.max(bestCloseness, closeness);
        }
        float bonus = isCardinal(mappedDirection) ? 0f : DIAGONAL_DIRECTION_BONUS;
        return bestCloseness * DIAGONAL_SCORE_WEIGHT + bonus;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private Direction calibratedDiagonalDirection(float angle, float distanceMm, Consonant consonant) {
        if (calibrationProfile == null) {
            return null;
        }
        Direction bestDirection = null;
        float bestScore = Float.MAX_VALUE;
        for (GestureCalibration.DirectionClass directionClass : diagonalClasses()) {
            GestureCalibration.DirectionProfile profile = calibrationProfile.directionProfile(consonant, directionClass);
            if (profile == null || distanceMm < profile.minDistanceMm) {
                continue;
            }
            float diff = Math.abs(GestureCalibration.angleDiff(angle, profile.centerAngle));
            float score = diff / Math.max(1f, profile.tolerance);
            if (score <= 1f && score < bestScore) {
                bestScore = score;
                bestDirection = directionForClass(directionClass);
            }
        }
        return bestDirection;
    }

    private float calibratedCenterAngle(Consonant consonant, GestureCalibration.DirectionClass directionClass,
                                        Direction fallbackDirection) {
        GestureCalibration.DirectionProfile profile = calibrationProfile == null
                ? null
                : calibrationProfile.directionProfile(consonant, directionClass);
        return profile == null ? referenceCenterAngle(fallbackDirection) : profile.centerAngle;
    }

    private float diagonalToleranceDegrees(Consonant consonant, GestureCalibration.DirectionClass directionClass) {
        GestureCalibration.DirectionProfile profile = calibrationProfile == null
                ? null
                : calibrationProfile.directionProfile(consonant, directionClass);
        return profile == null
                ? REFERENCE_VOWEL_TOLERANCE_DEGREES
                : clamp(profile.tolerance,
                GestureCalibration.MIN_DIAGONAL_TOLERANCE_DEGREES,
                GestureCalibration.MAX_DIAGONAL_TOLERANCE_DEGREES);
    }

    private GestureCalibration.DirectionClass[] diagonalClasses() {
        return new GestureCalibration.DirectionClass[]{
                GestureCalibration.DirectionClass.TOP_RIGHT,
                GestureCalibration.DirectionClass.TOP_LEFT,
                GestureCalibration.DirectionClass.BOTTOM_RIGHT,
                GestureCalibration.DirectionClass.BOTTOM_LEFT
        };
    }

    private GestureCalibration.DirectionClass classForDirection(Direction direction) {
        switch (direction) {
            case UP_RIGHT:
                return GestureCalibration.DirectionClass.TOP_RIGHT;
            case UP_LEFT:
                return GestureCalibration.DirectionClass.TOP_LEFT;
            case DOWN_RIGHT:
                return GestureCalibration.DirectionClass.BOTTOM_RIGHT;
            case DOWN_LEFT:
                return GestureCalibration.DirectionClass.BOTTOM_LEFT;
            default:
                return null;
        }
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

    private float coarsePathDistanceMm(List<Point> points) {
        float path = 0f;
        Point anchor = points.get(0);
        float segmentMin = firstSegmentMinMm();
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float distance = pointsDistanceMm(anchor, point);
            if (distance < segmentMin) {
                continue;
            }
            path += distance;
            anchor = point;
        }
        Point end = points.get(points.size() - 1);
        if (anchor != end) {
            path += pointsDistanceMm(anchor, end);
        }
        return path;
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

    private static class BacktrackMatch {
        static final BacktrackMatch NO_MATCH = new BacktrackMatch(false, 0f);

        final boolean matched;
        final float score;

        BacktrackMatch(boolean matched, float score) {
            this.matched = matched;
            this.score = score;
        }
    }
}
