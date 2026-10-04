package com.rewind;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import net.runelite.api.gameval.ItemID;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalQuestItemAuditTest
{
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
    public void questRequiredItemsAreAvailableByTheQuestRelease() throws Exception
    {
        int checked = 0;
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

                String[] fields = line.split("\t", 4);
                assertEquals("Malformed quest item audit row: " + line, 4, fields.length);
                String quest = fields[0];
                String releaseDate = fields[1];
                String constant = fields[2];
                String name = fields[3];

                Field field = ItemID.class.getField(constant);
                int itemId = field.getInt(null);
                assertTrue(quest + " requires " + name + " (" + constant + ") by " + releaseDate,
                    EntityDefinition.isItemUnlocked(itemId, date(releaseDate)));
                checked++;
            }
        }
        assertTrue("Quest item audit unexpectedly empty", checked > 1000);
    }

    @Test
    public void shieldOfArravAndDragonSlayerCriticalItemsAreCovered() throws Exception
    {
        for (String constant : new String[]{
            "THE_SHIELD_OF_ARRAV", "INTELLIGENCE_REPORT", "PHOENIXKEY2", "PHOENIX_CROSSBOW",
            "ARRAVSHIELD1", "ARRAVSHIELD2", "ARRAVCERTIFICATE_LFT", "ARRAVCERTIFICATE_RHT",
            "ARRAVCERTIFICATE"})
        {
            int id = ItemID.class.getField(constant).getInt(null);
            assertTrue(constant, EntityDefinition.isItemUnlocked(id, date("2001-01-04")));
        }

        for (String constant : new String[]{
            "MAPPART1", "MAPPART2", "MAPPART3", "MELZARKEY", "REDKEY", "ORANGEKEY",
            "YELLOWKEY", "BLUEKEY", "MAGENTAKEY", "GREENKEY", "DRAGONMAP"})
        {
            int id = ItemID.class.getField(constant).getInt(null);
            assertTrue(constant, EntityDefinition.isItemUnlocked(id, date("2001-09-23")));
        }
    }
}
