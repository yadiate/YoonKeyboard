package com.yadiate.yoonkeyboard.hangul;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

public class GestureVowelMapper {
    public enum Direction {
        RIGHT, LEFT, UP, DOWN, DOWN_RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT
    }

    private static final float REFERENCE_VOWEL_TOLERANCE_DEGREES = 35f;
    private static final float CARDINAL_DOMINANCE_RATIO = 2.4142137f;
    private static final float DIAGONAL_CONTINUATION_MAX_ANGLE_CHANGE_DEGREES = REFERENCE_VOWEL_TOLERANCE_DEGREES;
    private static final float SMOOTH_DIAGONAL_MAX_PATH_RATIO = 1.32f;
    private static final float SMOOTH_DIAGONAL_MAX_CARDINAL_ANGLE_CHANGE_DEGREES = REFERENCE_VOWEL_TOLERANCE_DEGREES;
    private static final float CLEAR_CORNER_MIN_FIRST_STROKE_RATIO = 0.78f;
    private static final float CLEAR_CORNER_MIN_PATH_RATIO = 1.14f;
    private static final float CLEAR_CORNER_MIN_ANGLE_CHANGE_DEGREES = 12f;

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
        this.minSegmentMm = Math.max(0.5f, minSegmentMm);
        this.longGestureMm = Math.max(this.minSegmentMm + 1f, longGestureMm);
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

        Integer overall = mapOverallDirection(start, end, totalDistance, longSingle, consonant);
        if (directions.isEmpty()) {
            return overall;
        }

        boolean longFirst = isLongGesture(firstDirection, firstDirectionDistance, consonant);
        float pathRatio = coarsePathDistanceMm(points) / Math.max(totalDistance, 0.001f);
        Integer smoothDiagonal = mapSmoothDiagonalTrace(overallDx, overallDy, totalDistance, pathRatio,
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

    private boolean is(Direction actual, Direction expected) {
        return actual == expected;
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

    private Integer mapSmoothDiagonalTrace(float dx, float dy, float totalDistanceMm, float pathRatio,
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
        if (angleChange > SMOOTH_DIAGONAL_MAX_CARDINAL_ANGLE_CHANGE_DEGREES) {
            return null;
        }
        if (hasClearCorner(firstDirection, firstDirectionDistance, directions, angleChange, pathRatio)) {
            return null;
        }
        return mapSingleDirection(diagonal, longSingle);
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
        float diff = Math.abs(GestureCalibration.angleDiff(angle, referenceCenterAngle(expected)));
        return diff <= REFERENCE_VOWEL_TOLERANCE_DEGREES ? expected : null;
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

    private boolean isLongGesture(Direction direction, float distanceMm, Consonant consonant) {
        if (direction == null) {
            return distanceMm > longGestureMm;
        }
        if (calibrationProfile != null) {
            if (direction == Direction.RIGHT || direction == Direction.LEFT) {
                return distanceMm > calibrationProfile.longHorizontalMm(consonant, longGestureMm);
            }
            if (direction == Direction.UP || direction == Direction.DOWN) {
                return distanceMm > calibrationProfile.longVerticalMm(consonant, longGestureMm);
            }
        }
        return distanceMm > longGestureMm;
    }

    private Direction directionFor(float dx, float dy) {
        return directionFor(dx, dy, null);
    }

    private Direction directionFor(float dx, float dy, Consonant consonant) {
        Direction fallback = dominantAxisDirection(dx, dy);
        float angle = angleFor(dx, dy);
        Direction calibrated = isCardinal(fallback)
                ? null
                : calibratedDiagonalDirection(angle, (float) Math.hypot(dx, dy), consonant);
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
}
