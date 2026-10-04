package com.rewind;

import com.rewind.regionlocker.HistoricalRegionState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalDataTest {
    private static final Gson GSON = new Gson();
    private static Map<Quest, LocalDate> expectedQuests = new HashMap<>();
    private static Reader resource(String name) {
        return new InputStreamReader(Objects.requireNonNull(HistoricalDataTest.class.getResourceAsStream(name)), StandardCharsets.UTF_8);
    }
    private static Date date(String value) {
        return Date.from(LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
    private static Release release(String value) {
        return Release.getRELEASES().stream().filter(r -> r.getDate().getLocalDate().equals(LocalDate.parse(value)))
            .findFirst().orElseThrow(AssertionError::new);
    }
    @BeforeClass public static void load() throws Exception {
        List<Release> releases = new ArrayList<>();
        for (String file : Arrays.asList("releases.json", "releases-2005-2007.json")) {
            try (Reader reader = resource(file)) { releases.addAll(Arrays.asList(GSON.fromJson(reader, Release[].class))); }
        }
        Release.setReleases(releases.toArray(new Release[0]));
        HistoricalRegionState.clearRegionGates();
        for (Release release : Release.getRELEASES()) {
            if (release.getRegions() == null) continue;
            for (Integer regionId : release.getRegions()) {
                if (regionId != null) HistoricalRegionState.gateRegion(regionId, release.getDate().getLocalDate());
            }
        }
        try (Reader reader = resource("items.json")) {
            EntityDefinition.itemDefinitions = GSON.fromJson(reader, new TypeToken<Map<Integer, EntityDefinition>>(){}.getType());
        }
        try (Reader reader = resource("item-release-overrides.json")) {
            EntityDefinition.itemReleaseOverrides = GSON.fromJson(reader, new TypeToken<Map<Integer, String>>(){}.getType());
        }
        try (Reader reader = resource("quest-item-release-overrides.json")) {
            Map<String, String> questItemOverrides = GSON.fromJson(reader, new TypeToken<Map<String, String>>(){}.getType());
            EntityDefinition.applyNamedItemReleaseOverrides(questItemOverrides);
        }
        try (Reader reader = resource("monsters.json")) {
            EntityDefinition.monsterDefinition = GSON.fromJson(reader, new TypeToken<Map<Integer, EntityDefinition>>(){}.getType());
        }
        try (BufferedReader reader = new BufferedReader(resource("quest-dates.tsv"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t");
                expectedQuests.put(Quest.valueOf(fields[0]), LocalDate.parse(fields[1]));
            }
        }
    }
    @Test public void everySelectorResolvesToItsLabelDateAndCutoff() {
        for (ReleaseDate value : ReleaseDate.values()) {
            assertEquals(value.getLocalDate(), Release.getReleaseByDate(value).getDate().getLocalDate());
            assertTrue(HistoricalCutoff.isSupported(value.getLocalDate()));
        }
        assertEquals(LocalDate.of(2001,1,28), ReleaseDate.JANUARY_2001.getLocalDate());
        assertEquals(LocalDate.of(2001,9,30), ReleaseDate.SEPTEMBER_2001.getLocalDate());
        assertEquals(LocalDate.of(2003,9,30), ReleaseDate.SEPTEMBER_2003.getLocalDate());
    }
    @Test public void allHistoricalQuestsAndSubquestsHaveExactDatesAtEveryRelease() {
        assertEquals(146, expectedQuests.size());
        for (Release r : Release.getRELEASES()) {
            Set<Quest> expected = expectedQuests.entrySet().stream()
                .filter(e -> !e.getValue().isAfter(r.getDate().getLocalDate())).map(Map.Entry::getKey).collect(Collectors.toSet());
            List<Quest> actual = Release.getQuests(r);
            assertEquals(r.getDate().toString(), expected, new HashSet<>(actual));
            assertEquals(actual.size(), new HashSet<>(actual).size());
        }
    }
    @Test public void everyEntityDateIsInclusiveAndNeverLeaksPastBackup() {
        for (Map<Integer, EntityDefinition> map : Arrays.asList(EntityDefinition.itemDefinitions, EntityDefinition.monsterDefinition)) {
            for (EntityDefinition def : map.values()) {
                String value = def.getReleaseDate();
                if (value == null) { assertFalse(EntityDefinition.isUnlocked(def, date("2007-08-10"))); continue; }
                LocalDate introduced = LocalDate.parse(value);
                if (introduced.isAfter(HistoricalCutoff.OSRS_BACKUP)) {
                    assertFalse(EntityDefinition.isUnlocked(def, date("2099-01-01")));
                } else {
                    assertTrue(def.getName(), EntityDefinition.isUnlocked(def, date(value)));
                    assertFalse(def.getName(), EntityDefinition.isUnlocked(def, date(introduced.minusDays(1).toString())));
                }
            }
        }
    }
    @Test public void verifiedLegacyItemDateGapsAreRecoveredWithoutOpeningUnknownItems() throws Exception {
        assertEquals(3, EntityDefinition.itemReleaseOverrides.size());

        // Half plain pizza is a real consumable state from the 11 June 2001 pizza update.
        assertFalse(EntityDefinition.isItemUnlocked(2291, date("2001-06-10")));
        assertTrue(EntityDefinition.isItemUnlocked(2291, date("2001-06-11")));
        assertTrue(EntityDefinition.isItemUnlocked(2292, date("2001-06-11")));

        // ID 9947 was introduced in a hidden update between 6 and 13 November 2006.
        // Use the end of the documented window so it can never unlock early.
        assertFalse(EntityDefinition.isItemUnlocked(9947, date("2006-11-12")));
        assertTrue(EntityDefinition.isItemUnlocked(9947, date("2006-11-13")));

        // Null-date interface/modern placeholders remain fail-closed unless explicitly verified.
        assertFalse(EntityDefinition.isItemUnlocked(11117, date("2007-08-10")));
    }

    @Test public void missingAndMalformedEntityDatesAreLocked() throws Exception {
        assertFalse(EntityDefinition.isItemUnlocked(Integer.MAX_VALUE, date("2007-08-10")));
        assertFalse(EntityDefinition.isMonsterUnlocked(Integer.MAX_VALUE, date("2007-08-10")));
        for (String value : Arrays.asList("not-a-date", "2005-02-30", "", "2007-08-11")) {
            EntityDefinition def = GSON.fromJson("{\"releaseDate\":\"" + value + "\"}", EntityDefinition.class);
            assertFalse(EntityDefinition.isUnlocked(def, date("2007-08-10")));
        }
    }
    @Test public void skillsAndSailingStayWithinHistoricalBounds() {
        assertFalse(Release.getSkills(release("2001-01-04")).contains(Skill.CRAFTING));
        assertTrue(Release.getSkills(release("2001-05-08")).contains(Skill.CRAFTING));
        assertFalse(Release.getSkills(release("2005-01-17")).contains(Skill.SLAYER));
        assertTrue(Release.getSkills(release("2005-01-26")).contains(Skill.SLAYER));
        assertEquals(23, Release.getSkills(release("2007-08-10")).size());
        for (Release r : Release.getRELEASES()) assertFalse(Release.getSkills(r).contains(Skill.SAILING));
        assertTrue(HistoricalPermanentExclusions.isSailingWidget(InterfaceID.Stats.SAILING));
        assertTrue(HistoricalPermanentExclusions.isSailingWidget(InterfaceID.SAILING_SIDEPANEL << 16));
        assertTrue(HistoricalPermanentExclusions.isSailingMenuAction("Open", "<col=fff>Sailing</col>"));
        assertFalse(HistoricalPermanentExclusions.isSailingMenuAction("Travel", "Captain Barnaby"));
    }
    @Test public void prayerReleaseBoundariesAreCorrect() {
        assertFalse(Release.getPrayers(release("2005-08-30")).contains(Prayer.SMITE));
        assertTrue(Release.getPrayers(release("2005-09-06")).containsAll(Arrays.asList(Prayer.SMITE, Prayer.REDEMPTION, Prayer.RETRIBUTION)));
        assertFalse(Release.getPrayers(release("2006-08-15")).contains(Prayer.EAGLE_EYE));
        assertTrue(Release.getPrayers(release("2006-08-22")).contains(Prayer.EAGLE_EYE));
        assertFalse(Release.getPrayers(release("2007-07-17")).contains(Prayer.PIETY));
        assertTrue(Release.getPrayers(release("2007-07-24")).contains(Prayer.PIETY));
        assertFalse(Release.getPrayers(release("2007-08-10")).contains(Prayer.RIGOUR));
    }
    @Test public void allThreeSpellbooksUnlockAndModernSpellsStayLocked() {
        assertFalse(HistoricalSpellRestrictions.allowed("Ice Barrage", Release.getSpells(release("2005-04-11"))));
        assertTrue(HistoricalSpellRestrictions.allowed("<col=fff>Ice Barrage</col> -> Goblin", Release.getSpells(release("2005-04-18"))));
        assertFalse(HistoricalSpellRestrictions.allowed("Vengeance", Release.getSpells(release("2006-07-17"))));
        assertTrue(HistoricalSpellRestrictions.allowed("Vengeance", Release.getSpells(release("2006-07-24"))));
        assertFalse(HistoricalSpellRestrictions.allowed("Dream", Release.getSpells(release("2007-05-08"))));
        assertTrue(HistoricalSpellRestrictions.allowed("Dream", Release.getSpells(release("2007-05-15"))));
        List<RewindSpell> last = Release.getSpells(release("2007-08-10"));
        for (String name : Arrays.asList("Wind Surge", "Tan Leather", "Recharge Dragonstone", "Ourania Teleport", "Teleport to Boat", "Kourend Castle Teleport"))
            assertFalse(name, HistoricalSpellRestrictions.allowed(name, last));
        assertFalse(HistoricalSpellRestrictions.allowed("Vengeance Other", Collections.singletonList(RewindSpell.VENGEANCE)));
        assertTrue(HistoricalSpellRestrictions.allowedWidget(InterfaceID.MagicSpellbook.ICE_BARRAGE, last));
        assertFalse(HistoricalSpellRestrictions.allowedWidget(InterfaceID.MagicSpellbook.TELEPORT_ME_TO_BOAT, last));
    }
    @Test public void regionStateKeepsLegacyUndergroundSupportWithoutLeakingDatedRegions() {
        Release last = release("2007-08-10");
        HistoricalRegionState.setSelectedDate(last.getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(last));
        assertTrue(HistoricalRegionState.isRegionUnlocked(12850)); // Lumbridge
        assertTrue(HistoricalRegionState.isRegionUnlocked(15148)); // Harmony, not Feldip
        assertTrue(HistoricalRegionState.isRegionUnlocked(10835)); // Dorgesh-Kaan
        assertFalse(HistoricalRegionState.isRegionUnlocked((20 << 8) | 75)); // unknown normal map space
        assertTrue(HistoricalRegionState.isRegionUnlocked(12437)); // Wizards' Tower basement
        assertFalse(HistoricalRegionState.isRegionUnlocked((20 << 8) | 150)); // unknown underground space still fails closed

        HistoricalRegionState.setSelectedDate(date("2005-03-13"));
        HistoricalRegionState.replaceWith(Release.getRegions(release("2005-03-07")));
        assertFalse(HistoricalRegionState.isRegionUnlocked(12693)); // Lumbridge Swamp Caves: 14 Mar 2005
        HistoricalRegionState.setSelectedDate(date("2005-03-14"));
        assertTrue(HistoricalRegionState.isRegionUnlocked(12693));

        HistoricalRegionState.setSelectedDate(release("2005-01-31").getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(release("2005-01-31")));
        assertFalse(HistoricalRegionState.isRegionUnlocked(15148));
    }
    @Test public void sailingObjectsExcludeLegacyPassengerPlanks() {
        Integer[] ids = GSON.fromJson(resource("sailing-objects.json"), Integer[].class);
        Set<Integer> locked = new HashSet<>(Arrays.asList(ids));
        assertEquals(ids.length, locked.size());
        assertTrue(locked.contains(60552));
        assertFalse(locked.contains(17392));
    }
}
