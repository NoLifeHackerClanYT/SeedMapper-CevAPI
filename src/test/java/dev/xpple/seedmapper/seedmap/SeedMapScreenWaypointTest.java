package dev.xpple.seedmapper.seedmap;

import dev.xpple.simplewaypoints.api.Waypoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SeedMapScreenWaypointTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Waypoint> normalize(Map<String, Waypoint> waypoints) throws Exception {
        Class<?> helper = Class.forName("dev.xpple.seedmapper.seedmap.WaypointMaps");
        Method method = helper.getDeclaredMethod("orEmpty", Map.class);
        method.setAccessible(true);
        return (Map<String, Waypoint>) method.invoke(null, new Object[]{waypoints});
    }

    @Test
    public void nullWaypointMapBecomesEmptyMap() throws Exception {
        Map<String, Waypoint> result = normalize(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void existingWaypointMapIsPreserved() throws Exception {
        Map<String, Waypoint> existing = new HashMap<>();
        assertSame(existing, normalize(existing));
    }
}