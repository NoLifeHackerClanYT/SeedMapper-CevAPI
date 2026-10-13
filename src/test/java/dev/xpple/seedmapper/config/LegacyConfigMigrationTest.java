package dev.xpple.seedmapper.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void migratingAFileCreatesBackupAndPreservesUnrelatedSettings(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        String original = """
            {
              "BlockColors": {
                "soul_sand": 6704179,
                "infested_stone": 7368816
              },
              "HighlightDuration": "PT5M",
              "SeedMapThreads": 14
            }
            """;
        Files.writeString(configFile, original);

        LegacyConfigMigration.Result result = LegacyConfigMigration.migrateFile(
            configFile,
            Map.of("soul_sand", 24)
        );

        assertTrue(result.changed());
        Path backup = LegacyConfigMigration.backupPath(configFile);
        assertTrue(Files.exists(backup));
        assertEquals(original, Files.readString(backup));

        JsonObject migrated = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
        JsonObject blockColors = migrated.getAsJsonObject("BlockColors");
        assertEquals(6704179, blockColors.get("24").getAsInt());
        assertFalse(blockColors.has("soul_sand"));
        assertFalse(blockColors.has("infested_stone"));
        assertEquals("PT5M", migrated.get("HighlightDuration").getAsString());
        assertEquals(14, migrated.get("SeedMapThreads").getAsInt());
    }
}
