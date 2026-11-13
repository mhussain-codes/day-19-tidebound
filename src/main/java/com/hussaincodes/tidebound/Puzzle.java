package com.hussaincodes.tidebound;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record Puzzle(String name, String story, List<Point> route, Set<Point> reefs,
              Point source, Point dock, Map<Point, Boolean> solution) {

    static List<Puzzle> all() {
        return List.of(
            create("First Light", "A quiet start. Bend the beam around the old breakwater.",
                path(3,0, 3,2, 1,2, 1,6), reefs(2,3, 4,1, 0,4, 5,3)),
            create("Low Tide", "The south channel is open, but the rocks are closer now.",
                path(3,0, 3,4, 5,4, 5,6), reefs(2,2, 4,1, 1,5, 6,3)),
            create("Glasswater", "A narrow eastern turn hides the safe passage.",
                path(3,0, 3,1, 5,1, 5,5, 1,5, 1,6), reefs(2,3, 4,3, 0,2, 6,5)),
            create("The Blue Cut", "Keep the signal north until it clears the blue cut.",
                path(3,0, 3,2, 1,2, 1,5, 5,5, 5,6), reefs(2,4, 4,1, 0,3, 6,2)),
            create("Fog Bell", "The bell marks a tight loop through the fog bank.",
                path(3,0, 3,1, 5,1, 5,4, 2,4, 2,6), reefs(1,2, 4,3, 3,5, 6,4)),
            create("Split Current", "A longer bend. Read the water before you send.",
                path(3,0, 3,3, 5,3, 5,5, 1,5, 1,6), reefs(2,1, 4,1, 0,4, 6,2)),
            create("Last Watch", "One last reef field stands between you and the harbor.",
                path(3,0, 3,2, 0,2, 0,5, 4,5, 4,6), reefs(2,4, 1,4, 5,1, 6,3)),
            create("Home Harbor", "Bring the final light home. Make every turn count.",
                path(3,0, 3,1, 6,1, 6,4, 2,4, 2,6), reefs(2,2, 4,2, 1,3, 5,5))
        );
    }

    List<Point> turns() {
        List<Point> result = new ArrayList<>();
        for (int i = 1; i < route.size() - 1; i++) {
            Direction before = Direction.between(route.get(i - 1), route.get(i));
            Direction after = Direction.between(route.get(i), route.get(i + 1));
            if (before != after) result.add(route.get(i));
        }
        return result;
    }

    private static Puzzle create(String name, String story, List<Point> points, Set<Point> reefs) {
        Map<Point, Boolean> answers = new HashMap<>();
        for (int i = 1; i < points.size() - 1; i++) {
            Direction before = Direction.between(points.get(i - 1), points.get(i));
            Direction after = Direction.between(points.get(i), points.get(i + 1));
            if (before != after) answers.put(points.get(i), mirrorFor(before, after));
        }
        Point start = points.getFirst();
        Point finish = points.getLast();
        return new Puzzle(name, story, List.copyOf(points), Set.copyOf(reefs), start, finish, Map.copyOf(answers));
    }

    // true is '/', false is '\\'.
    private static boolean mirrorFor(Direction in, Direction out) {
        if ((in == Direction.EAST && out == Direction.NORTH)
            || (in == Direction.NORTH && out == Direction.EAST)
            || (in == Direction.WEST && out == Direction.SOUTH)
            || (in == Direction.SOUTH && out == Direction.WEST)) return true;
        if ((in == Direction.EAST && out == Direction.SOUTH)
            || (in == Direction.SOUTH && out == Direction.EAST)
            || (in == Direction.WEST && out == Direction.NORTH)
            || (in == Direction.NORTH && out == Direction.WEST)) return false;
        throw new IllegalArgumentException("Routes must turn by 90 degrees");
    }

    private static List<Point> path(int... coordinates) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            Point start = new Point(coordinates[i], coordinates[i + 1]);
            if (points.isEmpty()) {
                points.add(start);
                continue;
            }
            Point previous = points.getLast();
            Direction direction = Direction.between(previous, start);
            int row = previous.x;
            int col = previous.y;
            while (row != start.x || col != start.y) {
                row += direction.dr;
                col += direction.dc;
                Point next = new Point(row, col);
                if (points.contains(next)) throw new IllegalArgumentException("Route crosses itself");
                points.add(next);
            }
        }
        return points;
    }

    private static Set<Point> reefs(int... coordinates) {
        Set<Point> reefs = new HashSet<>();
        for (int i = 0; i < coordinates.length; i += 2) reefs.add(new Point(coordinates[i], coordinates[i + 1]));
        return reefs;
    }
}

enum Direction {
    NORTH(-1, 0), EAST(0, 1), SOUTH(1, 0), WEST(0, -1);

    final int dr;
    final int dc;

    Direction(int dr, int dc) { this.dr = dr; this.dc = dc; }

    Direction reflect(boolean slash) {
        if (slash) return switch (this) {
            case NORTH -> EAST; case EAST -> NORTH; case SOUTH -> WEST; case WEST -> SOUTH;
        };
        return switch (this) {
            case NORTH -> WEST; case WEST -> NORTH; case SOUTH -> EAST; case EAST -> SOUTH;
        };
    }

    static Direction between(Point from, Point to) {
        int dr = Integer.compare(to.x, from.x);
        int dc = Integer.compare(to.y, from.y);
        if (dr != 0 && dc != 0) throw new IllegalArgumentException("Route segments must be straight");
        if (dr < 0) return NORTH;
        if (dr > 0) return SOUTH;
        if (dc < 0) return WEST;
        return EAST;
    }
}
