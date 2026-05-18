package com.example.eightwayime.hangul;

import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

public class GestureVowelMapper {
    public enum Direction {
        RIGHT, LEFT, UP, DOWN, DOWN_RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT
    }

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
        this.minSegmentMm = Math.max(1f, minSegmentMm);
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
        float firstDirectionDistance = 0f;
        for (int i = 1; i < points.size(); i++) {
            Point point = points.get(i);
            float dx = xDistanceMm(anchor, point);
            float dy = yDistanceMm(anchor, point);
            if (Math.hypot(dx, dy) < minSegmentMm) {
                continue;
            }
            Direction direction = directionFor(dx, dy, consonant);
            if (directions.isEmpty() || directions.get(directions.size() - 1) != direction) {
                directions.add(direction);
            }
            if (firstDirection == null) {
                firstDirection = direction;
            }
            if (direction == firstDirection) {
                firstDirectionDistance = pointsDistanceMm(start, point);
            }
            anchor = point;
        }

        Point end = points.get(points.size() - 1);
        float totalDistance = pointsDistanceMm(start, end);
        Direction overallDirection = directionFor(xDistanceMm(start, end), yDistanceMm(start, end), consonant);
        boolean longSingle = isLongGesture(overallDirection, totalDistance, consonant);

        Integer overall = mapOverallDirection(start, end, totalDistance, longSingle, consonant);
        if (directions.isEmpty()) {
            return overall;
        }

        boolean longFirst = isLongGesture(firstDirection, firstDirectionDistance, consonant);
        Integer mapped = mapDirections(directions, longSingle, longFirst);
        if (mapped != null) {
            return mapped;
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
        float fallbackMinMm = Math.max(1f, minSegmentMm * 0.55f);
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
            switch (first) {
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
            }
        }

        if (is(first, Direction.RIGHT) && isVertical(second)) {
            return longFirst ? HangulComposer.V_YI : HangulComposer.V_AE;
        }
        if (is(first, Direction.LEFT) && isVertical(second)) {
            return longFirst ? HangulComposer.V_YI : HangulComposer.V_E;
        }
        if (is(first, Direction.DOWN_RIGHT) && isVertical(second)) {
            return HangulComposer.V_YAE;
        }
        if (is(first, Direction.UP_LEFT) && isVertical(second)) {
            return HangulComposer.V_YE;
        }
        if (is(first, Direction.UP) && is(second, Direction.RIGHT) && is(third, Direction.DOWN)) {
            return HangulComposer.V_WAE;
        }
        if (is(first, Direction.UP) && is(second, Direction.RIGHT)) {
            return HangulComposer.V_WA;
        }
        if (is(first, Direction.UP) && is(second, Direction.DOWN)) {
            return HangulComposer.V_OE;
        }
        if (is(first, Direction.DOWN) && is(second, Direction.LEFT) && is(third, Direction.UP)) {
            return HangulComposer.V_WE;
        }
        if (is(first, Direction.DOWN) && is(second, Direction.UP)) {
            return HangulComposer.V_WI;
        }
        if (is(first, Direction.DOWN) && is(second, Direction.LEFT)) {
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
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        Direction fallback;
        if (angle >= -22.5 && angle < 22.5) {
            fallback = Direction.RIGHT;
        } else if (angle >= 22.5 && angle < 67.5) {
            fallback = Direction.DOWN_RIGHT;
        } else if (angle >= 67.5 && angle < 112.5) {
            fallback = Direction.DOWN;
        } else if (angle >= 112.5 && angle < 157.5) {
            fallback = Direction.DOWN_LEFT;
        } else if (angle >= -67.5 && angle < -22.5) {
            fallback = Direction.UP_RIGHT;
        } else if (angle >= -112.5 && angle < -67.5) {
            fallback = Direction.UP;
        } else if (angle >= -157.5 && angle < -112.5) {
            fallback = Direction.UP_LEFT;
        } else {
            fallback = Direction.LEFT;
        }
        Direction calibrated = calibratedDiagonalDirection((float) angle, (float) Math.hypot(dx, dy), consonant);
        return calibrated == null ? fallback : calibrated;
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
