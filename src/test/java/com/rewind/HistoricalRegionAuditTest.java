package com.rewind;

import com.rewind.regionlocker.HistoricalRegionState;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalRegionAuditTest {
    private static class Area {
        String name;
        String date;
        List<Integer> regions;
    }

    private static class Exclusion {
        String name;
        List<Integer> regions;
        String reason;
    }

    private static Area[] loadAreas() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            HistoricalRegionAuditTest.class.getResourceAsStream("region-boundaries.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Area[].class);
        }
    }

    private static Exclusion[] loadExclusions() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
            HistoricalRegionAuditTest.class.getResourceAsStream("region-audit-exclusions.json"), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, Exclusion[].class);
        }
    }

    @BeforeClass public static void load() throws Exception { HistoricalDataTest.load(); }

    @Test public void reviewedAreasUnlockTogetherAtTheirDocumentedBoundary() throws Exception {
        Area[] areas = loadAreas();
        for (Release release : Release.getRELEASES()) {
            HistoricalRegionState.setSelectedDate(release.getDate().getDate());
            HistoricalRegionState.replaceWith(Release.getRegions(release));
            for (Area area : areas) {
                boolean expected = !release.getDate().getLocalDate().isBefore(LocalDate.parse(area.date));
                for (int region : area.regions) {
                    assertEquals(area.name + " / " + region + " at " + release.getDate(),
                        expected, HistoricalRegionState.isRegionUnlocked(region));
                }
            }
        }
    }

    @Test public void regionAssignmentsAreUniqueAndWithinThePackedIdRange() {
        Set<Integer> seen = new HashSet<>();
        for (Release release : Release.getRELEASES()) {
            for (int region : release.getRegions()) {
                assertTrue("Invalid region " + region, region >= 0 && region <= 65535);
                assertTrue("Duplicate region " + region, seen.add(region));
            }
        }
    }

    @Test public void intentionalAuditExclusionsRemainFailClosedAndDoNotOverlapReviewedAreas() throws Exception {
        Set<Integer> reviewed = new HashSet<>();
        for (Area area : loadAreas()) reviewed.addAll(area.regions);

        Release last = Release.getRELEASES().get(Release.getRELEASES().size() - 1);
        HistoricalRegionState.setSelectedDate(last.getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(last));

        Set<Integer> excluded = new HashSet<>();
        for (Exclusion exclusion : loadExclusions()) {
            assertNotNull(exclusion.name, exclusion.reason);
            assertNotNull(exclusion.reason, exclusion.name);
            assertFalse(exclusion.name + " needs an audit rationale", exclusion.reason.trim().isEmpty());
            for (int region : exclusion.regions) {
                assertTrue("Duplicate audit exclusion " + region, excluded.add(region));
                assertFalse("Region cannot be both reviewed and excluded: " + region, reviewed.contains(region));
                assertFalse(exclusion.name + " / " + region + " must remain fail-closed",
                    HistoricalRegionState.isRegionUnlocked(region));
            }
        }
    }

    @Test public void everyConfiguredRegionUnlocksAtItsReleaseBoundary() {
        List<Release> releases = Release.getRELEASES();
        for (int i = 0; i < releases.size(); i++) {
            Release release = releases.get(i);
            HistoricalRegionState.setSelectedDate(release.getDate().getDate());
            HistoricalRegionState.replaceWith(Release.getRegions(release));
            for (int region : release.getRegions()) {
                assertTrue(release.getDate() + " / " + region,
                    HistoricalRegionState.isRegionUnlocked(region));
                if (i > 0) {
                    Release before = releases.get(i - 1);
                    HistoricalRegionState.setSelectedDate(before.getDate().getDate());
                    HistoricalRegionState.replaceWith(Release.getRegions(before));
                    assertFalse("Region unlocked before its configured boundary: " + region,
                        HistoricalRegionState.isRegionUnlocked(region));
                    HistoricalRegionState.setSelectedDate(release.getDate().getDate());
                    HistoricalRegionState.replaceWith(Release.getRegions(release));
                }
            }
        }
    }

    @Test public void ernestTheChickenBasementUnlocksWithTheQuestBoundary() {
        Release before = Release.getRELEASES().stream()
            .filter(r -> r.getDate().getLocalDate().equals(LocalDate.parse("2001-01-04")))
            .findFirst().orElseThrow(AssertionError::new);
        Release release = Release.getRELEASES().stream()
            .filter(r -> r.getDate().getLocalDate().equals(LocalDate.parse("2001-01-21")))
            .findFirst().orElseThrow(AssertionError::new);

        HistoricalRegionState.setSelectedDate(before.getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(before));
        assertFalse(HistoricalRegionState.isRegionUnlocked(12440));

        HistoricalRegionState.setSelectedDate(release.getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(release));
        assertTrue(HistoricalRegionState.isRegionUnlocked(12440));
    }

    @Test public void fixingHistoricalMinigamesDoesNotOpenModernDedicatedAreas() {
        Release last = Release.getRELEASES().get(Release.getRELEASES().size() - 1);
        HistoricalRegionState.setSelectedDate(last.getDate().getDate());
        HistoricalRegionState.replaceWith(Release.getRegions(last));
        for (int region : new int[]{9043, 10063, 10064, 9807, 13151, 12895, 14642,
            13136, 12889, 12611, 14160, 9033, 12119}) {
            assertFalse("Post-backup/dedicated modern region " + region,
                HistoricalRegionState.isRegionUnlocked(region));
        }
    }
}
