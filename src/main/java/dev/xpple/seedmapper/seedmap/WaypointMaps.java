package dev.xpple.seedmapper.seedmap;

import dev.xpple.simplewaypoints.api.Waypoint;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class WaypointMaps {
    private WaypointMaps() {
    }

    static Map<String, Waypoint> orEmpty(@Nullable Map<String, Waypoint> waypoints) {
        return waypoints == null ? Map.of() : waypoints;
    }
}