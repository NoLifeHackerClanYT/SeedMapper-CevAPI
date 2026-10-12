package dev.xpple.seedmapper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Migrates configuration shapes used by older CevAPI builds before BetterConfig
 * attempts to deserialize them into the current strongly typed fields.
 */
public final class LegacyConfigMigration {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BLOCK_COLORS = "BlockColors";
    private static final String BACKUP_SUFFIX = ".pre-26.2-blockcolors.bak";

    private LegacyConfigMigration() {
    }

    public static Result migrateFile(Path configFile, Map<String, Integer> blockIds) throws IOException {
        if (!Files.isRegularFile(configFile)) {
            return Result.unchanged();
        }

        JsonObject config;
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return Result.unchanged();
            }
            config = root.getAsJsonObject();
        }

        Result result = migrateBlockColorKeys(config, blockIds);
        if (!result.changed()) {
            return result;
        }

        Path backup = backupPath(configFile);
        if (!Files.exists(backup)) {
            Files.copy(configFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }

        Path parent = configFile.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, configFile.getFileName().toString(), ".migration.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
                writer.write(System.lineSeparator());
            }
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return result;
    }

    static Result migrateBlockColorKeys(JsonObject config, Map<String, Integer> blockIds) {
        JsonElement blockColorsElement = config.get(BLOCK_COLORS);
        if (blockColorsElement == null || !blockColorsElement.isJsonObject()) {
            return Result.unchanged();
        }

        JsonObject oldBlockColors = blockColorsElement.getAsJsonObject();
        boolean needsMigration = oldBlockColors.keySet().stream().anyMatch(key -> !isIntegerKey(key));
        if (!needsMigration) {
            return Result.unchanged();
        }

        JsonObject migratedBlockColors = new JsonObject();
        List<String> droppedKeys = new ArrayList<>();

        // Preserve already-migrated numeric keys first. If a mixed config contains both
        // a numeric ID and its old name, the explicit numeric entry wins.
        for (Map.Entry<String, JsonElement> entry : oldBlockColors.entrySet()) {
            if (isIntegerKey(entry.getKey())) {
                migratedBlockColors.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }

        for (Map.Entry<String, JsonElement> entry : oldBlockColors.entrySet()) {
            String oldKey = entry.getKey();
            if (isIntegerKey(oldKey)) {
                continue;
            }

            Integer blockId = blockIds.get(oldKey);
            if (blockId == null) {
                droppedKeys.add(oldKey);
                continue;
            }

            String newKey = Integer.toString(blockId);
            if (!migratedBlockColors.has(newKey)) {
                migratedBlockColors.add(newKey, entry.getValue().deepCopy());
            }
        }

        config.add(BLOCK_COLORS, migratedBlockColors);
        return new Result(true, List.copyOf(droppedKeys));
    }

    public static Path backupPath(Path configFile) {
        return configFile.resolveSibling(configFile.getFileName() + BACKUP_SUFFIX);
    }

    private static boolean isIntegerKey(String key) {
        try {
            Integer.parseInt(key);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public record Result(boolean changed, List<String> droppedKeys) {
        private static Result unchanged() {
            return new Result(false, List.of());
        }
    }
}
