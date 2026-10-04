package com.rewind;

import com.google.gson.Gson;
import com.rewind.regionlocker.HistoricalRegionState;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalQuestRegionAuditTest
{
    private static class Entry
    {
        String date;
        String dateKey;
        int region;
        List<String> quests;
    }

    @BeforeClass
    public static void load() throws Exception
    {
        HistoricalDataTest.load();
    }

    @Test
    public void everyAuditedQuestRegionUnlocksByItsHistoricalBoundary() throws Exception
    {
        Entry[] entries;
        try (InputStreamReader reader = new InputStreamReader(
            HistoricalQuestRegionAuditTest.class.getResourceAsStream("quest-region-audit.json"),
            StandardCharsets.UTF_8))
        {
            entries = new Gson().fromJson(reader, Entry[].class);
        }

        assertNotNull(entries);
        assertTrue("Quest region audit unexpectedly small", entries.length > 150);

        for (Entry entry : entries)
        {
            Release release = Release.getRELEASES().stream()
                .filter(r -> r.getDate().getLocalDate().equals(LocalDate.parse(entry.date)))
                .findFirst().orElseThrow(AssertionError::new);

            HistoricalRegionState.setSelectedDate(release.getDate().getDate());
            HistoricalRegionState.replaceWith(Release.getRegions(release));
            assertTrue(entry.date + " / " + entry.region + " / " + entry.quests,
                HistoricalRegionState.isRegionUnlocked(entry.region));
        }
    }
}
