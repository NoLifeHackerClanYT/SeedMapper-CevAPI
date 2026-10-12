package dev.xpple.seedmapper.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConfigMigrationTest {

    @Test
    void convertsLegacyNamedBlockColorKeysAndDropsUnsupportedNames() {
        JsonObject config = JsonParser.parseString("""
            {
              "BlockColors": {
                "soul_sand": 6704179,
                "7": 123456,
                "infested_stone": 7368816
              },
              "SeedMapThreads": 14
            }
            """).getAsJsonObject();

        LegacyConfigMigration.Result result = LegacyConfigMigration.migrateBlockColorKeys(
            config,
            Map.of("soul_sand", 24)
        );

        JsonObject blockColors = config.getAsJsonObject("BlockColors");
        assertEquals(6704179, blockColors.get("24").getAsInt());
        assertEquals(123456, blockColors.get("7").getAsInt());
        assertFalse(blockColors.has("soul_sand"));
        assertFalse(blockColors.has("infested_stone"));
        assertEquals(List.of("infested_stone"), result.droppedKeys());
        assertTrue(result.changed());
        assertEquals(14, config.get("SeedMapThreads").getAsInt());
    }

    @Test
    void leavesAlreadyNumericBlockColorKeysUntouched() {
        JsonObject config = JsonParser.parseString("""
            {
              "BlockColors": {
                "24": 6704179,
                "7": 123456
              }
            }
            """).getAsJsonObject();

        LegacyConfigMigration.Result result = LegacyConfigMigration.migrateBlockColorKeys(
            config,
            Map.of("soul_sand", 24)
        );

        assertFalse(result.changed());
        assertTrue(result.droppedKeys().isEmpty());
        assertEquals(6704179, config.getAsJsonObject("BlockColors").get("24").getAsInt());
        assertEquals(123456, config.getAsJsonObject("BlockColors").get("7").getAsInt());
    }
}
