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

    public Integer map(List<Point> points) {
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
            Direction direction = directionFor(dx, dy);
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

        if (directions.isEmpty()) {
            return null;
        }

        boolean longSingle = pointsDistanceMm(start, points.get(points.size() - 1)) > longGestureMm;
        boolean longFirst = firstDirectionDistance > longGestureMm;
        return mapDirections(directions, longSingle, longFirst);
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

    private Direction directionFor(float dx, float dy) {
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle >= -22.5 && angle < 22.5) {
            return Direction.RIGHT;
        }
        if (angle >= 22.5 && angle < 67.5) {
            return Direction.DOWN_RIGHT;
        }
        if (angle >= 67.5 && angle < 112.5) {
            return Direction.DOWN;
        }
        if (angle >= 112.5 && angle < 157.5) {
            return Direction.DOWN_LEFT;
        }
        if (angle >= -67.5 && angle < -22.5) {
            return Direction.UP_RIGHT;
        }
        if (angle >= -112.5 && angle < -67.5) {
            return Direction.UP;
        }
        if (angle >= -157.5 && angle < -112.5) {
            return Direction.UP_LEFT;
        }
        return Direction.LEFT;
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

        public Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
