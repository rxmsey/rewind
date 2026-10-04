package com.rewind;

import lombok.Getter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

@Getter
public class EntityDefinition {
    private static final int FIRST_LEGACY_PICKPOCKET_POUCH = 22521;
    private static final int LAST_LEGACY_PICKPOCKET_POUCH = 22538;
    private static final LocalDate THIEVING_RELEASE = LocalDate.of(2002, 4, 30);
    private int id;
    private String name;
    private String releaseDate;
    private String lastUpdated;
    static Map<Integer, EntityDefinition> itemDefinitions;
    static Map<Integer, EntityDefinition> monsterDefinition;
    static Map<Integer, String> itemReleaseOverrides = Collections.emptyMap();
    private static Map<String, LocalDate> earliestMonsterReleaseByName = Collections.emptyMap();

    static void applyNamedItemReleaseOverrides(Map<String, String> namedOverrides) {
        Map<Integer, String> merged = new HashMap<>();
        if (itemReleaseOverrides != null) {
            merged.putAll(itemReleaseOverrides);
        }
        if (namedOverrides != null) {
            for (Map.Entry<String, String> entry : namedOverrides.entrySet()) {
                try {
                    int id = ItemID.class.getField(entry.getKey()).getInt(null);
                    merged.put(id, entry.getValue());
                } catch (ReflectiveOperationException ignored) {
                    // Unknown constants stay fail-closed.
                }
            }
        }
        itemReleaseOverrides = Collections.unmodifiableMap(merged);
    }

    static void indexMonsterDefinitions() {
        Map<String, LocalDate> indexed = new HashMap<>();
        if (monsterDefinition != null) {
            for (EntityDefinition def : monsterDefinition.values()) {
                if (def == null || def.name == null || def.releaseDate == null) continue;
                try {
                    LocalDate release = LocalDate.parse(def.releaseDate);
                    // Keep the earliest known release even when it is post-backup. This lets
                    // an otherwise unknown/new NPC id inherit a verified modern name date
                    // and remain locked instead of falling through as an ordinary NPC.
                    indexed.merge(normalizeName(def.name), release,
                        (first, second) -> first.isBefore(second) ? first : second);
                } catch (DateTimeParseException ignored) {
                    // Invalid source dates remain locked.
                }
            }
        }
        earliestMonsterReleaseByName = indexed;
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // Unknown IDs and missing/invalid dates remain locked. IDs are never used as a date proxy.
    static boolean isUnlocked(EntityDefinition def, Date selected) {
        return isUnlockedDate(def == null ? null : def.releaseDate, selected);
    }

    private static boolean isUnlockedDate(String releaseDate, Date selected) {
        if (releaseDate == null || selected == null) return false;
        try {
            LocalDate introduced = LocalDate.parse(releaseDate);
            LocalDate date = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return HistoricalCutoff.isSupported(introduced) && !introduced.isAfter(date);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    public static boolean isItemUnlocked(int id, Date selected) throws java.text.ParseException {
        if (id >= FIRST_LEGACY_PICKPOCKET_POUCH && id <= LAST_LEGACY_PICKPOCKET_POUCH) {
            LocalDate date = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return !date.isBefore(THIEVING_RELEASE);
        }
        EntityDefinition def = itemDefinitions == null ? null : itemDefinitions.get(id);
        if (def == null) return false;

        String release = def.releaseDate;
        if (itemReleaseOverrides != null) {
            String override = itemReleaseOverrides.get(id);
            if (override != null) {
                try {
                    if (release == null || LocalDate.parse(override).isBefore(LocalDate.parse(release))) {
                        release = override;
                    }
                } catch (DateTimeParseException ignored) {
                    // Invalid override stays ignored.
                }
            }
        }
        return isUnlockedDate(release, selected);
    }

    public static boolean isMonsterUnlocked(int id, Date selected) throws java.text.ParseException {
        return isUnlocked(monsterDefinition == null ? null : monsterDefinition.get(id), selected);
    }

    public static boolean isMonsterUnlocked(int id, String visibleName, Date selected) throws java.text.ParseException {
        EntityDefinition definition = monsterDefinition == null ? null : monsterDefinition.get(id);
        if (isUnlocked(definition, selected)) return true;
        if (visibleName == null || selected == null) return false;
        LocalDate earliest = earliestMonsterReleaseByName.get(normalizeName(visibleName));
        if (earliest != null) {
            LocalDate date = selected.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return HistoricalCutoff.isSupported(earliest) && !earliest.isAfter(date);
        }

        // The source is a monster dataset, so many ordinary non-combat NPCs have no row.
        // Preserve truly unknown names to avoid deleting legitimate legacy bankers/shopkeepers,
        // but a known definition with no verified date is fail-closed.
        return definition == null;
    }
}
