package com.rewind;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalQuestItemAuditTest
{
    private static final Set<String> MODERN_OR_OPTIONAL = new HashSet<>();

    static
    {
        String[] exact = {
            "CHRONICLE", "RING_OF_RECOIL", "RING_OF_CHAROS_UNLOCKED", "RING_OF_ELEMENTS_CHARGED",
            "TABLET_KHARYLL", "SANFEW_SALVE_1_DOSE", "SUMMER_PIE", "ALUFT_SEED_POD",
            "BLANKRUNE_HIGH", "RAKE", "FLAMTAER_BRACELET", "COSTUMENEEDLE"
        };
        java.util.Collections.addAll(MODERN_OR_OPTIONAL, exact);
    }

    private static boolean skip(String constant)
    {
        return MODERN_OR_OPTIONAL.contains(constant)
            || constant.startsWith("POH_TABLET_")
            || constant.startsWith("TELEPORTSCROLL_")
            || constant.startsWith("TELETAB_")
            || constant.startsWith("NZONE_TELETAB_")
            || constant.startsWith("LUNAR_TABLET_")
            || constant.startsWith("ARDY_CAPE_")
            || constant.startsWith("LUMBRIDGE_RING_")
            || constant.startsWith("TRAIL_AMULET_OF_GLORY_")
            || constant.startsWith("JEWL_NECKLACE_OF_SKILLS_")
            || constant.startsWith("MAGIC_STRUNG_LYRE_")
            || constant.startsWith("NECKLACE_OF_PASSAGE_")
            || constant.startsWith("RCU_POUCH_");
    }

    private static Date date(String value)
    {
        return Date.from(LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    @BeforeClass
    public static void load() throws Exception
    {
        HistoricalDataTest.load();
    }

    @Test
    public void historicalQuestItemsAreAvailableByTheirQuestBoundary() throws Exception
    {
        int checked = 0;
        java.util.List<String> locked = new java.util.ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            HistoricalQuestItemAuditTest.class.getResourceAsStream("quest-item-boundaries.tsv"),
            StandardCharsets.UTF_8)))
        {
            String line = reader.readLine();
            assertNotNull("Missing quest item audit header", line);
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    continue;
                }

                String[] fields = line.split("\\t", 4);
                assertEquals("Malformed quest item audit row: " + line, 4, fields.length);
                String quest = fields[0];
                String releaseDate = fields[1];
                String constant = fields[2];
                String name = fields[3];

                if (skip(constant))
                {
                    continue;
                }

                Field field = ItemID.class.getField(constant);
                int itemId = field.getInt(null);
                if (!EntityDefinition.isItemUnlocked(itemId, date(releaseDate)))
                {
                    locked.add(quest + "\t" + releaseDate + "\t" + constant + "\t" + name);
                }
                checked++;
            }
        }
        assertTrue("Quest item audit unexpectedly small", checked > 800);
        for (String failure : locked)
        {
            System.err.println("LOCKED_HISTORICAL_QUEST_ITEM\t" + failure);
        }
        assertTrue("Locked historical quest items: " + locked, locked.isEmpty());
    }

    @Test
    public void shieldOfArravCriticalItemsAreAvailableAtLaunch() throws Exception
    {
        for (String constant : new String[]{
            "THE_SHIELD_OF_ARRAV", "INTELLIGENCE_REPORT", "PHOENIXKEY2", "PHOENIX_CROSSBOW",
            "ARRAVSHIELD1", "ARRAVSHIELD2", "ARRAVCERTIFICATE_LFT", "ARRAVCERTIFICATE_RHT",
            "ARRAVCERTIFICATE"})
        {
            int id = ItemID.class.getField(constant).getInt(null);
            assertTrue(constant, EntityDefinition.isItemUnlocked(id, date("2001-01-04")));
        }
    }
}
