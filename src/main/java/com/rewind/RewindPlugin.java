package com.rewind;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.rewind.regionlocker.RegionBorderOverlay;
import com.rewind.regionlocker.RegionLocker;
import com.rewind.regionlocker.RegionLockerOverlay;
import com.rewind.regionlocker.HistoricalRegionState;
import com.rewind.regionlocker.HistoricalSceneMaskOverlay;
import com.rewind.regionlocker.HistoricalMinimapMaskOverlay;
import com.rewind.regionlocker.HistoricalMinimapInputBlocker;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "Rewind",
        configName = "chronoplugin", // Preserve RuneLite's enabled/disabled preference.
		description = "Experience Old School RuneScape through its historical timeline.",
		tags = {"time traveler", "by release"}
)
public class RewindPlugin extends Plugin {
    private static final String COMPLETION_FIGHT_CAVES_KEY = "completionFightCaves";
    private static final String COMPLETION_FIGHTER_TORSO_KEY = "completionFighterTorso";
    private static final String COMPLETION_VOID_TOP_KEY = "completionVoidTop";
    private static final String COMPLETION_VOID_ROBE_KEY = "completionVoidRobe";
    private static final String COMPLETION_VOID_GLOVES_KEY = "completionVoidGloves";
    private static final String COMPLETION_VOID_HELM_KEY = "completionVoidHelm";
    private static final String COMPLETION_RUNE_DEFENDER_KEY = "completionRuneDefender";
    private static final String COMPLETION_CASTLE_WARS_KEY = "completionCastleWarsReward";
    private static final String COMPLETION_AGILITY_PYRAMID_KEY = "completionAgilityPyramid";
    private static final String COMPLETION_TEMPLE_TREKKING_KEY = "completionTempleTrekking";
    private static final String COMPLETION_TROUBLE_BREWING_KEY = "completionTroubleBrewing";
    private static final String COMPLETION_STRONGHOLD_SECURITY_KEY = "completionStrongholdSecurity";
    private static final String COMPLETION_PYRAMID_PLUNDER_KEY = "completionPyramidPlunder";
    private static final String COMPLETION_FISHING_TRAWLER_KEY = "completionFishingTrawler";
    private static final String COMPLETION_BLAST_FURNACE_KEY = "completionBlastFurnace";
    private static final String COMPLETION_SHADES_MORTTON_KEY = "completionShadesMortton";
    private static final String COMPLETION_SLAYER_TOWER_KEY = "completionSlayerTower";
    private static final String COMPLETION_MAGE_ARENA_CAPE_KEY = "completionMageArenaCape";

    // Preserve existing release and region preferences across the Rewind rebrand.
	public static final String CONFIG_GROUP_KEY = "chrono";
	public static final String CONFIG_RELEASE_DATE_KEY = "releasedate";
	private static final int GRAND_EXCHANGE_REGION = 12598;
    private static final int REGULAR_FIRE_OBJECT = 26185;
    private static final int FORESTERS_CAMPFIRE_OBJECT = 49927;
    private static final int DEATH_OFFICE_DEATH_NPC_ID = 9855;
    private static final Set<Integer> TUTORIAL_ISLAND_REGIONS = new HashSet<>(Arrays.asList(
        12336, 12335, 12592, 12080, 12079, 12436));
    private static final int PYRAMID_PLUNDER_REGION = 7749;
    private static final Set<Integer> CASTLE_WARS_REGIONS =
        new HashSet<>(Arrays.asList(9520, 9620));
    private static final Set<Integer> AGILITY_PYRAMID_REGIONS =
        new HashSet<>(Arrays.asList(12105, 13356));
    private static final int MORTTON_REGION = 13875;
    private static final Set<Integer> SLAYER_TOWER_REGIONS =
        new HashSet<>(Arrays.asList(13623, 13723));

	private static final int SOUND_EFFECT_FAIL = 2277;
	private static final int SOUND_EFFECT_INACTIVE = 2673;
	private static final List<String> MENU_BLACKLIST = Arrays.asList("Use", "Take", "Wield","Empty", "Eat", "Wear", "Read", "Check", "Teleport", "Commune", "Drink", "Bury", "Scatter");

	@Inject
	private Client client;

	@Inject
	@Getter
	private RewindConfig config;

	@Inject
	private RegionLockerOverlay regionLockerOverlay;

	@Inject
	private RegionBorderOverlay regionBorderOverlay;

	@Inject
	private HistoricalSceneMaskOverlay historicalSceneMaskOverlay;

	@Inject
	private HistoricalMinimapMaskOverlay historicalMinimapMaskOverlay;

	@Inject
	private HistoricalMinimapInputBlocker historicalMinimapInputBlocker;

	@Inject
	private MouseManager mouseManager;

	@Inject
	@Getter
	private ConfigManager configManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RewindItemOverlay itemOverlay;

	@Inject
	private RewindSkillOverlay skillOverlay;

	@Inject
	private RewindAbilityOverlay abilityOverlay;

	@Inject
	private Gson gson;

	@Inject
	private RenderCallbackManager renderCallbackManager;
    private volatile boolean maskScene;

	@Inject
	private ClientToolbar clientToolbar;

    @Inject
    private RewindCompletionOverlay completionOverlay;

	@Getter
	private Release currentRelease;

	@Getter
	@Setter
	private int hoveredRegion = -1;

	private RewindPanel panel;
	private NavigationButton navButton;

    private Set<Integer> sailingObjects = Collections.emptySet();
    private Set<String> unlockedQuestNames = Collections.emptySet();
    private final Set<String> completionSnapshot = new HashSet<>();
    private boolean completionSnapshotReady;
    private boolean completionSeedPending;
    private volatile boolean tutorialBypass;
    private NPC slayerTowerTarget;

	@Getter
	private boolean mapEnabled;

	/* Widgets */

	private final RenderCallback drawListener = new RenderCallback() {
        @Override public boolean addEntity(Renderable entity, boolean ui) { return shouldDraw(entity, ui); }
        @Override public boolean drawTile(Scene scene, Tile tile) {
            return !maskScene || HistoricalRegionState.isTileUnlocked(
                WorldPoint.fromLocalInstance(scene, tile.getLocalLocation(), tile.getPlane()));
        }
        @Override public boolean drawObject(Scene scene, TileObject object) {
            return !maskScene || HistoricalRegionState.isTileUnlocked(
                WorldPoint.fromLocalInstance(scene, object.getLocalLocation(), object.getPlane()));
        }
    };

	@Provides
	RewindConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RewindConfig.class);
	}

	@Override
	protected void startUp() {
		loadDefinitions();
		currentRelease = Release.getReleaseByDate(config.release());
        updateUnlockedQuestNames();
		HistoricalRegionState.setSelectedDate(config.release().getDate());
		HistoricalRegionState.replaceWith(Release.getRegions(currentRelease));
		overlayManager.add(itemOverlay);
		overlayManager.add(skillOverlay);
		overlayManager.add(abilityOverlay);
		overlayManager.add(regionLockerOverlay);
        overlayManager.add(completionOverlay);
		overlayManager.add(historicalSceneMaskOverlay);
		overlayManager.add(historicalMinimapMaskOverlay);
        mouseManager.registerMouseListener(historicalMinimapInputBlocker);

		panel = new RewindPanel(this);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Rewind")
				.priority(5)
				.icon(icon)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
        maskScene = config.maskLockedScene();
        updateAdditionalRegions();
        renderCallbackManager.register(drawListener);
        reloadScene();
        clientThread.invokeLater(() -> {
            updateTutorialBypass();
            refreshWidgets();
        });
	}

	@Override
	protected void shutDown() {
		RegionLocker.renderLockedRegions = false;
        HistoricalRegionState.setBypassRestrictions(false);
        tutorialBypass = false;
		overlayManager.remove(itemOverlay);
		skillOverlay.restoreAllSkills();
		overlayManager.remove(skillOverlay);
		overlayManager.remove(abilityOverlay);
		overlayManager.remove(regionLockerOverlay);
        overlayManager.remove(completionOverlay);
		overlayManager.remove(regionBorderOverlay);
		overlayManager.remove(historicalSceneMaskOverlay);
		overlayManager.remove(historicalMinimapMaskOverlay);
        mouseManager.unregisterMouseListener(historicalMinimapInputBlocker);
        historicalMinimapInputBlocker.clear();
		clientToolbar.removeNavigation(navButton);
        renderCallbackManager.unregister(drawListener);
        reloadScene();
        // Rebuild the native spellbook after Rewind is disabled so its script callback
        // no longer filters the spell array.
        clientThread.invokeLater(this::redrawSpellbook);
        for (RewindPrayer prayer : RewindPrayer.values()) {
            Widget widget = client.getWidget(prayer.getPackedID());
            if (widget != null) widget.setOpacity(0);
        }
	}

	private void loadDefinitions() {
        Integer[] objects = loadDefinitionResource(Integer[].class, "sailing-objects.json");
        sailingObjects = new HashSet<>(Arrays.asList(objects));
        Type defMapType = new TypeToken<Map<Integer, EntityDefinition>>() {}.getType();
        EntityDefinition.itemDefinitions = loadDefinitionResource(defMapType, "items.json");
        Type overrideMapType = new TypeToken<Map<Integer, String>>() {}.getType();
        EntityDefinition.itemReleaseOverrides = loadDefinitionResource(overrideMapType, "item-release-overrides.json");
        Type namedOverrideMapType = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> questItemOverrides = loadDefinitionResource(namedOverrideMapType, "quest-item-release-overrides.json");
        EntityDefinition.applyNamedItemReleaseOverrides(questItemOverrides);
        EntityDefinition.monsterDefinition = loadDefinitionResource(defMapType, "monsters.json");
        EntityDefinition.indexMonsterDefinitions();
        Release[] base = loadDefinitionResource(Release[].class, "releases.json");
        Release[] continued = loadDefinitionResource(Release[].class, "releases-2005-2007.json");
        Release[] merged = Arrays.copyOf(base, base.length + continued.length);
        System.arraycopy(continued, 0, merged, base.length, continued.length);
        Release.setReleases(merged);
        HistoricalRegionState.clearRegionGates();
        for (Release release : Release.getRELEASES())
        {
            if (release.getRegions() == null) continue;
            LocalDate releaseDate = release.getDate().getLocalDate();
            for (Integer regionId : release.getRegions())
            {
                if (regionId != null) HistoricalRegionState.gateRegion(regionId, releaseDate);
            }
        }
    }

    private <T> T loadDefinitionResource(Type type, String resource) {
        try (InputStream stream = RewindPlugin.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing resource: " + resource);
            try (InputStreamReader reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, type);
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot read " + resource, ex);
        }
    }

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
        if(!e.getGroup().equals(CONFIG_GROUP_KEY)) return;
        maskScene = config.maskLockedScene();
        if (e.getKey().equals("maskLockedScene")) reloadScene();
		if (e.getKey().equals("unlockGrandExchange")) {
			updateAdditionalRegions();
			reloadScene();
            if (panel != null) {
                final boolean enabled = Boolean.parseBoolean(e.getNewValue());
                SwingUtilities.invokeLater(() -> panel.updateGrandExchangeState(enabled));
            }
		}

		if (e.getKey().equals("unlockHomeTeleport")) {
            final boolean enabled = Boolean.parseBoolean(e.getNewValue());
            clientThread.invokeLater(this::updateSpells);
            if (panel != null) {
                SwingUtilities.invokeLater(() -> panel.updateHomeTeleportState(enabled));
            }
		}

		if(e.getKey().equals(CONFIG_RELEASE_DATE_KEY)) {
			currentRelease = Release.getReleaseByDate(config.release());
            updateUnlockedQuestNames();
			HistoricalRegionState.setSelectedDate(config.release().getDate());
			HistoricalRegionState.replaceWith(Release.getRegions(currentRelease));
            updateAdditionalRegions();
            // Changing timeline changes which objectives exist. Re-seed instead of
            // treating already-completed objectives in the newly selected date as
            // fresh completions.
            completionSnapshotReady = false;
            clientThread.invokeLater(() -> {
                refreshWidgets();
                seedCompletionSnapshot();
                if (panel != null) panel.refresh();
            });
            reloadScene();

			panel.updateDescription(currentRelease.getDescription());
		}
	}

    @Subscribe
    public void onClientTick(net.runelite.api.events.ClientTick event) {
        updateTutorialBypass();
        boolean regionBypass = tutorialBypass || isCutsceneActive() || isDeathOfficeActive();
        HistoricalRegionState.setBypassRestrictions(regionBypass);
        if (regionBypass)
        {
            historicalMinimapInputBlocker.clear();
        }
        else
        {
            historicalMinimapInputBlocker.refresh();
        }
    }

    private boolean isCutsceneActive()
    {
        return client.getGameState() == GameState.LOGGED_IN
            && client.getVarbitValue(VarbitID.CUTSCENE_STATUS) == 1;
    }

    private boolean isDeathOfficeActive()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }
        for (NPC npc : client.getNpcs())
        {
            if (npc != null && npc.getId() == DEATH_OFFICE_DEATH_NPC_ID)
            {
                return true;
            }
        }
        return false;
    }

	@Subscribe
	public void onGameStateChanged(GameStateChanged e){
        historicalMinimapInputBlocker.clear();
        if (e.getGameState() == GameState.LOGGED_IN) {
            // RuneLite can fire StatChanged while the player's levels are still
            // being populated during login. Seeding here can therefore snapshot
            // level 1, then immediately announce an old milestone (for example
            // Mining 20) when the real level arrives. Keep completion detection
            // disabled until the first logged-in game tick, then seed once from
            // the fully loaded player state.
            completionSnapshotReady = false;
            completionSeedPending = true;
            completionSnapshot.clear();
            clientThread.invokeLater(() -> {
                refreshWidgets();
                if (panel != null) panel.refresh();
            });
        } else if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING) {
            completionSnapshotReady = false;
            completionSeedPending = false;
            completionSnapshot.clear();
            slayerTowerTarget = null;
        }
	}

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e) throws ParseException {
        if (tutorialBypass || isCutsceneActive() || isDeathOfficeActive()) return;

        String option = HistoricalSpellRestrictions.clean(e.getMenuOption());
        String target = HistoricalSpellRestrictions.clean(e.getMenuTarget());
        Widget widget = e.getWidget();
        int group = widget == null ? -1 : widget.getId() >>> 16;
        if (HistoricalPermanentExclusions.isSailingMenuAction(option, target)
            || (widget != null && HistoricalPermanentExclusions.isSailingWidget(widget.getId()))) {
            deny(e); return;
        }
        // The scene mask is visual only. Enforce the same region boundary on direct
        // movement/object/ground-item actions so blacked-out regions are not still usable.
        if (isSceneLocationAction(e.getMenuAction())) {
            WorldPoint actionLocation = getMenuWorldPoint(e);
            if (actionLocation != null && !HistoricalRegionState.isTileUnlocked(actionLocation)) {
                deny(e); return;
            }
        }
        // Greyed pre-release skills should not still open their modern skill guides.
        if (widget != null && option.toLowerCase(Locale.ROOT).startsWith("view ")) {
            Skill skill = RewindSkillOverlay.skillForWidget(widget);
            if (skill != null && (HistoricalPermanentExclusions.isSkillPermanentlyLocked(skill)
                || !Release.getSkills(currentRelease).contains(skill))) {
                deny(e); return;
            }
        }
        // Quick prayers are a post-2007 feature and can activate locked prayers in one click.
        if ((option.toLowerCase(java.util.Locale.ROOT).contains("quick-prayer")
            || target.toLowerCase(java.util.Locale.ROOT).contains("quick-prayer"))
            && !option.toLowerCase(java.util.Locale.ROOT).contains("deactivate")) {
            deny(e); return;
        }
        if (option.equals("Activate") && (group == InterfaceID.PRAYERBOOK
            || Arrays.stream(RewindPrayer.values()).anyMatch(p -> p.getName().equalsIgnoreCase(target)))) {
            boolean allowed = Arrays.stream(RewindPrayer.values()).anyMatch(p ->
                p.getName().equalsIgnoreCase(target) && Release.getPrayers(currentRelease).contains(p.getPrayer()));
            if (!allowed) { deny(e); return; }
        }
        if (group == InterfaceID.PRAYERBOOK && isPrayerActivation(option)
            && !isPrayerWidgetUnlocked(widget, target)) {
            deny(e); return;
        }
        List<RewindSpell> spells = getUnlockedSpells();
        if (option.equals("Cast") || option.toLowerCase(java.util.Locale.ROOT).contains("autocast")) {
            if (!HistoricalSpellRestrictions.allowed(target, spells)) { deny(e); return; }
        }
        if (group == InterfaceID.MAGIC_SPELLBOOK && e.getMenuAction() == net.runelite.api.MenuAction.WIDGET_TARGET
            && !HistoricalSpellRestrictions.allowedWidget(widget.getId(), spells)) {
            deny(e); return;
        }
        // Re-check a previously selected spell/item after changing the historical date.
        if (e.getMenuAction().name().startsWith("WIDGET_TARGET_ON_")) {
            Widget selected = client.getSelectedWidget();
            if (selected != null && selected.getId() >>> 16 == InterfaceID.MAGIC_SPELLBOOK
                && !HistoricalSpellRestrictions.allowedWidget(selected.getId(), spells)) {
                deny(e); return;
            }
            if (selected != null && selected.getItemId() >= 0 && !isItemUnlocked(selected.getItemId())) {
                deny(e); return;
            }
        }
        if ((e.getMenuAction().name().startsWith("GAME_OBJECT_")
            || e.getMenuAction() == net.runelite.api.MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
            && sailingObjects.contains(e.getId())) {
            deny(e); return;
        }
        // Forester's Campfires are a modern Firemaking mechanic. Preserve normal
        // historical tinderbox Firemaking, but prevent adding logs to an existing
        // fire/campfire and prevent tending the modern campfire object itself.
        if (e.getId() == FORESTERS_CAMPFIRE_OBJECT
            && (option.equalsIgnoreCase("Tend-to") || option.equalsIgnoreCase("Tend"))) {
            deny(e);
            addWarningMessage("Bonfire Firemaking was not available by " + config.release().getName() + ".", false);
            return;
        }
        if (e.getMenuAction() == net.runelite.api.MenuAction.WIDGET_TARGET_ON_GAME_OBJECT
            && (e.getId() == REGULAR_FIRE_OBJECT || e.getId() == FORESTERS_CAMPFIRE_OBJECT)) {
            Widget selected = client.getSelectedWidget();
            if (selected != null && selected.getItemId() >= 0 && isLogItem(selected.getItemId())) {
                deny(e);
                addWarningMessage("Bonfire Firemaking was not available by " + config.release().getName() + ".", false);
                return;
            }
        }
        NPC npc = e.getMenuEntry().getNpc();
        if (npc != null && !option.equals("Examine")
            && !isNpcUnlocked(npc)) {
            deny(e); return;
        }
        // Match actual item operations plus the known item-use verbs. Shop stock gets an
        // explicit group check because RuneScape may expose Buy actions as generic widget ops.
        // Do not gate every widget with an item id: several historical interfaces reuse
        // pseudo-item ids purely as icons (for example Construction/build menus).
        boolean itemAction = e.isItemOp() || MENU_BLACKLIST.contains(option)
            || e.getMenuAction().name().startsWith("GROUND_ITEM_")
            || e.getMenuAction() == net.runelite.api.MenuAction.WIDGET_TARGET
            || group == InterfaceID.SHOPMAIN;
        boolean disposal = option.equals("Drop") || option.equals("Destroy") || option.equals("Examine")
            || option.startsWith("Deposit") || option.equals("Release") || option.equals("Remove");
        int itemId = e.getItemId();
        if (e.getMenuAction().name().startsWith("GROUND_ITEM_")) itemId = e.getId();
        if ((widget != null && widget.getId() == InterfaceID.GeOffers.SETUP_CONFIRM)
            || (group == InterfaceID.GE_OFFERS && option.equalsIgnoreCase("Confirm"))) {
            itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
        }
        if (config.unlockGrandExchange() && isGrandExchangeGroup(group)
            && itemId >= 0 && !isItemUnlocked(itemId)) {
            deny(e);
            addWarningMessage("This item was not available by " + config.release().getName() + ".", false);
            return;
        }
        if (itemAction && !disposal && itemId >= 0 && !isItemUnlocked(itemId)) {
            deny(e);
            addWarningMessage("This item is unavailable or has no verified release date for "
                + config.release().getName() + ".", false);
        }
    }

    private boolean isLogItem(int itemId) {
        ItemComposition item = client.getItemDefinition(itemId);
        if (item == null || item.getName() == null) {
            return false;
        }
        return item.getName().trim().toLowerCase(Locale.ROOT).endsWith("logs");
    }

    private void deny(MenuOptionClicked event) {
        event.consume();
        client.playSoundEffect(SOUND_EFFECT_INACTIVE);
    }

    private void reloadScene() {
        clientThread.invokeLater(() -> {
            if (client.getGameState() == GameState.LOGGED_IN) client.setGameState(GameState.LOADING);
        });
    }

    private void refreshWidgets() {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (tutorialBypass)
        {
            skillOverlay.restoreAllSkills();
            abilityOverlay.restoreAllAbilities();
            restoreSpellWidgets();
            redrawSpellbook();
            redrawQuests();
            return;
        }
        updatePrayers();
        updateSpells();
        redrawQuests();
    }

    public boolean isTutorialBypass()
    {
        return tutorialBypass;
    }

    private void updateTutorialBypass()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        boolean bypass = TUTORIAL_ISLAND_REGIONS.contains(player.getWorldLocation().getRegionID());
        if (bypass == tutorialBypass)
        {
            return;
        }

        tutorialBypass = bypass;
        HistoricalRegionState.setBypassRestrictions(bypass);
        completionSnapshotReady = false;
        completionSeedPending = !bypass;
        completionSnapshot.clear();
        historicalMinimapInputBlocker.clear();

        if (bypass)
        {
            skillOverlay.restoreAllSkills();
            abilityOverlay.restoreAllAbilities();
            restoreSpellWidgets();
            redrawSpellbook();
            redrawQuests();
        }
        else
        {
            refreshWidgets();
        }

        if (panel != null)
        {
            SwingUtilities.invokeLater(panel::refresh);
        }
        reloadScene();
    }

    private void restoreSpellWidgets()
    {
        for (RewindSpell spell : RewindSpell.values())
        {
            Widget widget = client.getWidget(spell.getPackedID());
            if (widget != null)
            {
                widget.setHidden(false);
            }
        }
    }

    /**
     * RuneLite's quest-list script calls this callback once for each quest row while it
     * builds the native list. Force historically unavailable quests to be filtered out,
     * but leave already-filtered rows alone so RuneLite's own search/status filters and
     * native Free/Members/release-date grouping continue to work normally.
     */
    @Subscribe(priority = -1) // run after RuneLite's native Quest List/Spellbook filtering
    public void onScriptCallbackEvent(ScriptCallbackEvent event) {
        if (currentRelease == null || tutorialBypass) return;

        if ("questFilter".equals(event.getEventName())) {
            int[] intStack = client.getIntStack();
            int intStackSize = client.getIntStackSize();
            if (intStack == null || intStackSize < 2) return;

            int row = intStack[intStackSize - 1];
            Object[] displayName = client.getDBTableField(row, DBTableID.Quest.COL_DISPLAYNAME, 0);
            if (displayName == null || displayName.length == 0 || !(displayName[0] instanceof String)) return;

            if (!unlockedQuestNames.contains((String) displayName[0])) {
                intStack[intStackSize - 2] = 1;
            }
            return;
        }

        if ("spellbookSort".equals(event.getEventName())) {
            filterHistoricalSpells();
        }
    }

    /**
     * The game builds an array containing the spells which will be laid out in the
     * current spellbook. Filter that array instead of hiding/painting over widgets.
     * This is the same callback used by RuneLite's core Spellbook plugin, so the
     * remaining spells retain their native widgets, tooltips and menu behaviour.
     */
    private void filterHistoricalSpells() {
        int[] stack = client.getIntStack();
        int size = client.getIntStackSize();
        if (stack == null || size < 3) return;

        int spellbookEnumId = stack[size - 3];
        int spellArrayId = stack[size - 2];
        int numSpells = stack[size - 1];

        EnumComposition spellbook = client.getEnum(spellbookEnumId);
        int[] spells = client.getArray(spellArrayId);
        if (spellbook == null || spells == null || numSpells <= 0) return;

        Set<Integer> allowedWidgets = new HashSet<>();
        for (RewindSpell spell : getUnlockedSpells()) {
            allowedWidgets.add(spell.getPackedID());
        }

        int write = 0;
        for (int read = 0; read < numSpells; ++read) {
            int enumIndex = spells[read];
            ItemComposition spellDefinition = client.getItemDefinition(spellbook.getIntValue(enumIndex));
            int spellWidget = spellDefinition.getIntValue(ParamID.SPELL_BUTTON);
            if (allowedWidgets.contains(spellWidget)) {
                spells[write++] = enumIndex;
            } else {
                // The native redraw only lays out entries left in the spell array.
                // Explicitly hide excluded widgets during that same redraw pass so
                // their old/default bounds cannot remain stacked under Home Teleport
                // and produce a giant ghost context menu.
                Widget spell = client.getWidget(spellWidget);
                if (spell != null) spell.setHidden(true);
            }
        }

        stack[size - 1] = write;
    }

    private void updateUnlockedQuestNames() {
        if (currentRelease == null) {
            unlockedQuestNames = Collections.emptySet();
            return;
        }
        Set<String> names = new HashSet<>();
        for (Quest quest : Release.getQuests(currentRelease)) {
            names.add(quest.getName());
        }
        unlockedQuestNames = names;
    }

    private void redrawQuests() {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        Widget container = client.getWidget(InterfaceID.Questlist.CONTAINER);
        if (container == null) return;
        Object[] onVarTransmitListener = container.getOnVarTransmitListener();
        if (onVarTransmitListener == null) return;
        clientThread.invokeLater(() -> client.runScript(onVarTransmitListener));
    }

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() == InterfaceID.TOPLEVEL_OSRS_STRETCH || e.getGroupId() == InterfaceID.TOPLEVEL) {
            this.updatePrayers();
		}
		else if(e.getGroupId() == InterfaceID.MAGIC_SPELLBOOK) {
			this.updateSpells();
		}
	}





	@VisibleForTesting
	boolean shouldDraw(Renderable renderable, boolean drawingUI) {
        if (tutorialBypass) return true;

		if (renderable instanceof NPC)
		{
			NPC npc = (NPC) renderable;

			if(npc.getInteracting() == client.getLocalPlayer()) {
				return true;
			}

			try {
				return isNpcUnlocked(npc);
            } catch(ParseException e) {
                return false;
			}
		}

		return true;
	}

	private void addWarningMessage(String message, boolean playSound) {
		final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(message)
				.append(ChatColorType.NORMAL);

		chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(chatMessage.build())
				.build());

        if (playSound) client.playSoundEffect(SOUND_EFFECT_FAIL);
	}

	public boolean isItemUnlocked(int itemId) throws ParseException {
        if (tutorialBypass) return true;

        ItemComposition item = client.getItemDefinition(itemId);
        // A bank note is the same historical item, not an independent modern item.
        if (item.getNote() != -1) itemId = item.getLinkedNoteId();
        return EntityDefinition.isItemUnlocked(itemId, config.release().getDate());
	}

    private void updatePrayers() {
        if (tutorialBypass)
        {
            abilityOverlay.restoreAllAbilities();
            for (RewindPrayer prayer : RewindPrayer.values())
            {
                Widget parent = client.getWidget(prayer.getPackedID());
                if (parent != null) parent.setOpacity(0);
            }
            return;
        }

        List<Prayer> unlocked = Release.getPrayers(currentRelease);
        for (RewindPrayer prayer : RewindPrayer.values()) {
            Widget parent = client.getWidget(prayer.getPackedID());
            if (parent != null) parent.setOpacity(unlocked.contains(prayer.getPrayer()) ? 0 : 160);
        }
    }

    List<RewindSpell> getUnlockedSpells() {
        List<RewindSpell> unlocked = new ArrayList<>(Release.getSpells(currentRelease));
        if (config.unlockHomeTeleport()) {
            addSpell(unlocked, RewindSpell.LUMBRIDGE_HOME_TELEPORT);
            addSpell(unlocked, RewindSpell.EDGEVILLE_HOME_TELEPORT);
            addSpell(unlocked, RewindSpell.LUNAR_HOME_TELEPORT);
        }
        return unlocked;
    }

    private static void addSpell(List<RewindSpell> spells, RewindSpell spell) {
        if (!spells.contains(spell)) spells.add(spell);
    }

    private void updateSpells() {
        redrawSpellbook();
    }

    private void redrawSpellbook() {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        Widget universe = client.getWidget(InterfaceID.MagicSpellbook.UNIVERSE);
        if (universe == null || universe.getOnInvTransmitListener() == null) return;
        client.createScriptEventBuilder(universe.getOnInvTransmitListener())
            .setSource(universe)
            .build()
            .run();
    }

    /**
     * Completion Log v1 deliberately uses only state RuneLite can read reliably:
     * historical quest completion and curated level milestones for skills which
     * existed by the selected date. Boss/activity objectives can be layered onto
     * this model once their persistent completion signals are verified.
     */
    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!completionSnapshotReady || currentRelease == null) return;
        Skill skill = event.getSkill();
        Integer target = getHistoricalSkillTargets().get(skill);
        if (target != null && event.getLevel() >= target) {
            String key = "skill:" + skill.name() + ":" + target;
            if (completionSnapshot.add(key)) {
                showCompletionPopup(skill.getName() + " " + target);
            }
        }
        checkQuestCompletions();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        int containerId = event.getContainerId();
        ItemContainer container = event.getItemContainer();
        if (container == null) return;

        // These minigame-owned containers are themselves reliable completion
        // signals: they are only populated after the player has produced/earned
        // the corresponding reward.
        if (containerId == InventoryID.TRAWLER_REWARDINV && hasAnyItem(container))
        {
            recordActivity(
                COMPLETION_FISHING_TRAWLER_KEY,
                "activity:fishing_trawler",
                "Complete a Fishing Trawler trip",
                isFishingTrawlerAvailable(currentRelease));
            return;
        }

        if (containerId == InventoryID.BLAST_FURNACE_BARS_INV && hasAnyItem(container))
        {
            recordActivity(
                COMPLETION_BLAST_FURNACE_KEY,
                "activity:blast_furnace",
                "Smelt bars at the Blast Furnace",
                isBlastFurnaceAvailable(currentRelease));
            return;
        }

        // Ownership-based objectives only inspect player-owned containers. This
        // prevents reward shops themselves from granting completion just by opening.
        if (containerId != InventoryID.INV
            && containerId != InventoryID.WORN
            && containerId != InventoryID.BANK)
        {
            return;
        }

        boolean torsoBefore = isFighterTorsoComplete();
        boolean runeDefenderBefore = isRuneDefenderComplete();
        boolean voidBefore = isVoidSetComplete();
        boolean mageArenaCapeBefore = isMageArenaCapeComplete();
        boolean castleWarsBefore = isCastleWarsComplete();
        boolean agilityPyramidBefore = isAgilityPyramidComplete();
        boolean templeTrekkingBefore = isTempleTrekkingComplete();
        boolean troubleBrewingBefore = isTroubleBrewingComplete();
        boolean strongholdBefore = isStrongholdSecurityComplete();
        boolean pyramidPlunderBefore = isPyramidPlunderComplete();

        int regionId = currentRegionId();

        for (Item item : container.getItems())
        {
            if (item == null || item.getQuantity() <= 0) continue;

            switch (item.getId())
            {
                case ItemID.RUNE_PARRYINGDAGGER:
                case ItemID.RUNE_PARRYINGDAGGER_T:
                case ItemID.RUNE_PARRYINGDAGGER_T_TROUVER:
                    writeProfileFlag(COMPLETION_RUNE_DEFENDER_KEY);
                    break;
                case ItemID.BARBASSAULT_PENANCE_FIGHTER_TORSO:
                    writeProfileFlag(COMPLETION_FIGHTER_TORSO_KEY);
                    break;
                case ItemID.PEST_VOID_KNIGHT_TOP:
                    writeProfileFlag(COMPLETION_VOID_TOP_KEY);
                    break;
                case ItemID.PEST_VOID_KNIGHT_ROBES:
                    writeProfileFlag(COMPLETION_VOID_ROBE_KEY);
                    break;
                case ItemID.PEST_VOID_KNIGHT_GLOVES:
                    writeProfileFlag(COMPLETION_VOID_GLOVES_KEY);
                    break;
                case ItemID.GAME_PEST_MAGE_HELM:
                case ItemID.GAME_PEST_ARCHER_HELM:
                case ItemID.GAME_PEST_MELEE_HELM:
                    writeProfileFlag(COMPLETION_VOID_HELM_KEY);
                    break;
                default:
                    break;
            }

            ItemComposition definition = client.getItemDefinition(item.getId());
            String itemName = definition == null ? "" : definition.getName();
            String lowerName = itemName == null ? "" : itemName.toLowerCase(Locale.ROOT);

            // Castle Wars decorative rewards are untradeable; seeing one in the
            // player's inventory at Castle Wars proves a ticket-shop purchase.
            if (containerId == InventoryID.INV
                && CASTLE_WARS_REGIONS.contains(regionId)
                && lowerName.contains("decorative"))
            {
                writeProfileFlag(COMPLETION_CASTLE_WARS_KEY);
            }

            // These untradeable rewards safely backfill completion from inventory,
            // equipment, or bank when Rewind first encounters them.
            if (lowerName.equals("saradomin cape")
                || lowerName.equals("zamorak cape")
                || lowerName.equals("guthix cape"))
            {
                writeProfileFlag(COMPLETION_MAGE_ARENA_CAPE_KEY);
            }

            if (lowerName.equals("fancy boots") || lowerName.equals("fighting boots"))
            {
                writeProfileFlag(COMPLETION_STRONGHOLD_SECURITY_KEY);
            }
            if (lowerName.startsWith("reward token")
                && !lowerName.contains("gnome"))
            {
                writeProfileFlag(COMPLETION_TEMPLE_TREKKING_KEY);
            }
            if (lowerName.equals("pieces of eight"))
            {
                writeProfileFlag(COMPLETION_TROUBLE_BREWING_KEY);
            }

            // Pyramid Top and Pyramid Plunder artefacts are tradeable, so only
            // credit them when they appear in the inventory inside the activity.
            if (containerId == InventoryID.INV
                && AGILITY_PYRAMID_REGIONS.contains(regionId)
                && lowerName.equals("pyramid top"))
            {
                writeProfileFlag(COMPLETION_AGILITY_PYRAMID_KEY);
            }
            if (containerId == InventoryID.INV
                && regionId == PYRAMID_PLUNDER_REGION
                && isPyramidPlunderArtefact(lowerName))
            {
                writeProfileFlag(COMPLETION_PYRAMID_PLUNDER_KEY);
            }
        }

        maybeShowItemActivity(mageArenaCapeBefore, isMageArenaCapeComplete(),
            isMageArenaCapeAvailable(currentRelease), "activity:mage_arena_cape",
            "Obtain a Mage Arena god cape");

        maybeShowItemActivity(castleWarsBefore, isCastleWarsComplete(),
            isCastleWarsAvailable(currentRelease), "activity:castle_wars",
            "Buy a Castle Wars reward");
        maybeShowItemActivity(agilityPyramidBefore, isAgilityPyramidComplete(),
            isAgilityPyramidAvailable(currentRelease), "activity:agility_pyramid",
            "Retrieve a pyramid top");
        maybeShowItemActivity(templeTrekkingBefore, isTempleTrekkingComplete(),
            isTempleTrekkingAvailable(currentRelease), "activity:temple_trekking",
            "Complete a Temple Trek");
        maybeShowItemActivity(troubleBrewingBefore, isTroubleBrewingComplete(),
            isTroubleBrewingAvailable(currentRelease), "activity:trouble_brewing",
            "Earn Pieces of Eight");
        maybeShowItemActivity(strongholdBefore, isStrongholdSecurityComplete(),
            isStrongholdSecurityAvailable(currentRelease), "activity:stronghold_security",
            "Claim Stronghold boots");
        maybeShowItemActivity(pyramidPlunderBefore, isPyramidPlunderComplete(),
            isPyramidPlunderAvailable(currentRelease), "activity:pyramid_plunder",
            "Loot a Pyramid Plunder artefact");

        if (completionSnapshotReady && currentRelease != null)
        {
            if (!torsoBefore && isFighterTorsoAvailable(currentRelease)
                && isFighterTorsoComplete()
                && completionSnapshot.add("activity:fighter_torso"))
            {
                showCompletionPopup("Fighter torso");
            }

            if (!runeDefenderBefore && isRuneDefenderAvailable(currentRelease)
                && isRuneDefenderComplete()
                && completionSnapshot.add("activity:rune_defender"))
            {
                showCompletionPopup("Rune defender");
            }

            if (!voidBefore && isVoidSetAvailable(currentRelease)
                && isVoidSetComplete()
                && completionSnapshot.add("activity:void_set"))
            {
                showCompletionPopup("Void Knight set");
            }
        }

        if (panel != null && ((!torsoBefore && isFighterTorsoComplete())
            || (!runeDefenderBefore && isRuneDefenderComplete())
            || (!voidBefore && isVoidSetComplete())
            || (!mageArenaCapeBefore && isMageArenaCapeComplete())
            || (!castleWarsBefore && isCastleWarsComplete())
            || (!agilityPyramidBefore && isAgilityPyramidComplete())
            || (!templeTrekkingBefore && isTempleTrekkingComplete())
            || (!troubleBrewingBefore && isTroubleBrewingComplete())
            || (!strongholdBefore && isStrongholdSecurityComplete())
            || (!pyramidPlunderBefore && isPyramidPlunderComplete())))
        {
            panel.refresh();
        }
    }

    private boolean hasAnyItem(ItemContainer container)
    {
        for (Item item : container.getItems())
        {
            if (item != null && item.getQuantity() > 0)
            {
                return true;
            }
        }
        return false;
    }

    private int currentRegionId()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return -1;
        }
        WorldPoint point = localPlayer.getWorldLocation();
        return point == null ? -1 : point.getRegionID();
    }

    private static boolean isPyramidPlunderArtefact(String name)
    {
        return name.equals("ivory comb")
            || name.equals("pottery scarab")
            || name.equals("pottery statuette")
            || name.equals("stone seal")
            || name.equals("stone scarab")
            || name.equals("stone statuette")
            || name.equals("gold seal")
            || name.equals("golden scarab")
            || name.equals("golden statuette")
            || name.equals("pharaoh's sceptre");
    }

    private void maybeShowItemActivity(boolean before, boolean after, boolean available,
        String snapshotKey, String title)
    {
        if (!before && after && completionSnapshotReady && currentRelease != null
            && available && completionSnapshot.add(snapshotKey))
        {
            showCompletionPopup(title);
        }
    }

    private void recordActivity(String profileKey, String snapshotKey, String title, boolean available)
    {
        boolean before = readProfileFlag(profileKey);
        writeProfileFlag(profileKey);
        if (!before && completionSnapshotReady && currentRelease != null
            && available && completionSnapshot.add(snapshotKey))
        {
            showCompletionPopup(title);
        }
        if (!before && panel != null)
        {
            panel.refresh();
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() != client.getLocalPlayer()
            || !(event.getTarget() instanceof NPC))
        {
            return;
        }

        NPC target = (NPC) event.getTarget();
        WorldPoint point = target.getWorldLocation();
        if (point != null && SLAYER_TOWER_REGIONS.contains(point.getRegionID()))
        {
            slayerTowerTarget = target;
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        Actor actor = event.getActor();
        if (!(actor instanceof NPC)) return;

        NPC npc = (NPC) actor;

        if (npc == slayerTowerTarget)
        {
            WorldPoint point = npc.getWorldLocation();
            if (point != null
                && SLAYER_TOWER_REGIONS.contains(point.getRegionID())
                && isSlayerTowerAvailable(currentRelease))
            {
                recordActivity(
                    COMPLETION_SLAYER_TOWER_KEY,
                    "activity:slayer_tower",
                    "Kill a monster in the Slayer Tower",
                    true);
            }
            slayerTowerTarget = null;
        }

        if (!"TzTok-Jad".equalsIgnoreCase(npc.getName())) return;

        configManager.setRSProfileConfiguration(
            CONFIG_GROUP_KEY, COMPLETION_FIGHT_CAVES_KEY, true);
        configManager.setConfiguration(
            CONFIG_GROUP_KEY, COMPLETION_FIGHT_CAVES_KEY, true);

        if (completionSnapshotReady && currentRelease != null
            && isFightCavesAvailable(currentRelease))
        {
            String key = "activity:fight_caves";
            if (completionSnapshot.add(key))
            {
                showCompletionPopup("TzHaar Fight Cave");
            }
        }

        if (panel != null)
        {
            panel.refresh();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (completionSeedPending)
        {
            completionSeedPending = false;
            seedCompletionSnapshot();
            if (panel != null) panel.refresh();
            return;
        }

        if (isShadesMorttonAvailable(currentRelease)
            && !isShadesMorttonComplete()
            && currentRegionId() == MORTTON_REGION
            && client.getVarpValue(VarPlayerID.TEMPLE_SANCTITY_P) > 0)
        {
            recordActivity(
                COMPLETION_SHADES_MORTTON_KEY,
                "activity:shades_mortton",
                "Contribute to rebuilding the Flamtaer temple",
                true);
        }

        checkQuestCompletions();
        checkActivityCompletions();
    }

    private void checkActivityCompletions() {
        if (!completionSnapshotReady || currentRelease == null
            || client.getGameState() != GameState.LOGGED_IN) return;

        if (isBarrowsAvailable(currentRelease))
        {
            String key = "activity:barrows_chest";
            if (!completionSnapshot.contains(key) && isBarrowsComplete()) {
                completionSnapshot.add(key);
                showCompletionPopup("Barrows reward chest");
            }
        }

        if (isFightCavesAvailable(currentRelease) && isFightCavesComplete())
        {
            completionSnapshot.add("activity:fight_caves");
        }

        if (isBonesToPeachesAvailable(currentRelease) && isBonesToPeachesComplete()
            && completionSnapshot.add("activity:bones_to_peaches"))
        {
            showCompletionPopup("Bones to Peaches");
        }

        if (isRuneDefenderAvailable(currentRelease) && isRuneDefenderComplete())
        {
            completionSnapshot.add("activity:rune_defender");
        }
        if (isFishingTrawlerAvailable(currentRelease) && isFishingTrawlerComplete())
            completionSnapshot.add("activity:fishing_trawler");
        if (isMageArenaCapeAvailable(currentRelease) && isMageArenaCapeComplete())
            completionSnapshot.add("activity:mage_arena_cape");

        if (isShadesMorttonAvailable(currentRelease) && isShadesMorttonComplete())
            completionSnapshot.add("activity:shades_mortton");
        if (isSlayerTowerAvailable(currentRelease) && isSlayerTowerComplete())
            completionSnapshot.add("activity:slayer_tower");
        if (isCastleWarsAvailable(currentRelease) && isCastleWarsComplete())
            completionSnapshot.add("activity:castle_wars");
        if (isBlastFurnaceAvailable(currentRelease) && isBlastFurnaceComplete())
            completionSnapshot.add("activity:blast_furnace");
        if (isAgilityPyramidAvailable(currentRelease) && isAgilityPyramidComplete())
            completionSnapshot.add("activity:agility_pyramid");
        if (isTempleTrekkingAvailable(currentRelease) && isTempleTrekkingComplete())
            completionSnapshot.add("activity:temple_trekking");
        if (isTroubleBrewingAvailable(currentRelease) && isTroubleBrewingComplete())
            completionSnapshot.add("activity:trouble_brewing");
        if (isStrongholdSecurityAvailable(currentRelease) && isStrongholdSecurityComplete())
            completionSnapshot.add("activity:stronghold_security");
        if (isPyramidPlunderAvailable(currentRelease) && isPyramidPlunderComplete())
            completionSnapshot.add("activity:pyramid_plunder");
    }

    private void checkQuestCompletions() {
        if (!completionSnapshotReady || currentRelease == null
            || client.getGameState() != GameState.LOGGED_IN) return;
        for (Quest quest : Release.getQuests(currentRelease)) {
            String key = "quest:" + quest.name();
            if (!completionSnapshot.contains(key) && isQuestComplete(quest)) {
                completionSnapshot.add(key);
                showCompletionPopup(quest.getName());
            }
        }
    }

    private void seedCompletionSnapshot() {
        if (client.getGameState() != GameState.LOGGED_IN || currentRelease == null) return;
        completionSnapshot.clear();
        for (Quest quest : Release.getQuests(currentRelease)) {
            if (isQuestComplete(quest)) completionSnapshot.add("quest:" + quest.name());
        }
        for (Map.Entry<Skill, Integer> entry : getHistoricalSkillTargets().entrySet()) {
            if (client.getRealSkillLevel(entry.getKey()) >= entry.getValue()) {
                completionSnapshot.add("skill:" + entry.getKey().name() + ":" + entry.getValue());
            }
        }
        if (isBarrowsAvailable(currentRelease) && isBarrowsComplete()) {
            completionSnapshot.add("activity:barrows_chest");
        }
        if (isFightCavesAvailable(currentRelease) && isFightCavesComplete()) {
            completionSnapshot.add("activity:fight_caves");
        }
        if (isBonesToPeachesAvailable(currentRelease) && isBonesToPeachesComplete()) {
            completionSnapshot.add("activity:bones_to_peaches");
        }
        if (isVoidSetAvailable(currentRelease) && isVoidSetComplete()) {
            completionSnapshot.add("activity:void_set");
        }
        if (isRuneDefenderAvailable(currentRelease) && isRuneDefenderComplete()) {
            completionSnapshot.add("activity:rune_defender");
        }
        if (isFighterTorsoAvailable(currentRelease) && isFighterTorsoComplete()) {
            completionSnapshot.add("activity:fighter_torso");
        }
        if (isFishingTrawlerAvailable(currentRelease) && isFishingTrawlerComplete())
            completionSnapshot.add("activity:fishing_trawler");
        if (isMageArenaCapeAvailable(currentRelease) && isMageArenaCapeComplete())
            completionSnapshot.add("activity:mage_arena_cape");

        if (isShadesMorttonAvailable(currentRelease) && isShadesMorttonComplete())
            completionSnapshot.add("activity:shades_mortton");
        if (isSlayerTowerAvailable(currentRelease) && isSlayerTowerComplete())
            completionSnapshot.add("activity:slayer_tower");
        if (isCastleWarsAvailable(currentRelease) && isCastleWarsComplete())
            completionSnapshot.add("activity:castle_wars");
        if (isBlastFurnaceAvailable(currentRelease) && isBlastFurnaceComplete())
            completionSnapshot.add("activity:blast_furnace");
        if (isAgilityPyramidAvailable(currentRelease) && isAgilityPyramidComplete())
            completionSnapshot.add("activity:agility_pyramid");
        if (isTempleTrekkingAvailable(currentRelease) && isTempleTrekkingComplete())
            completionSnapshot.add("activity:temple_trekking");
        if (isTroubleBrewingAvailable(currentRelease) && isTroubleBrewingComplete())
            completionSnapshot.add("activity:trouble_brewing");
        if (isStrongholdSecurityAvailable(currentRelease) && isStrongholdSecurityComplete())
            completionSnapshot.add("activity:stronghold_security");
        if (isPyramidPlunderAvailable(currentRelease) && isPyramidPlunderComplete())
            completionSnapshot.add("activity:pyramid_plunder");
        completionSnapshotReady = true;
    }

    private void showCompletionPopup(String objective) {
        CompletionProgress progress = getCompletionProgress();
        completionOverlay.show(objective, currentRelease.getDate().getName(),
            progress.getCompleted(), progress.getAvailable());
        if (panel != null) panel.refresh();
    }

    boolean isClientStateReadable() {
        return client.getGameState() == GameState.LOGGED_IN && client.isClientThread();
    }

    CompletionProgress getCompletionProgress() {
        if (currentRelease == null || client.getGameState() != GameState.LOGGED_IN) {
            return new CompletionProgress(0, 0, 0, 0, 0, 0);
        }

        List<Quest> availableQuests = getAvailableCompletionQuests();
        int completedQuests = 0;
        for (Quest quest : availableQuests) {
            try {
                if (quest.getState(client) == QuestState.FINISHED) {
                    completedQuests++;
                }
            } catch (RuntimeException ex) {
                log.debug("Unable to read quest state for {}", quest.getName(), ex);
            }
        }

        int completedMilestones = 0;
        int availableMilestones = 0;
        final Map<Skill, Integer> targets = getHistoricalSkillTargets();
        for (Map.Entry<Skill, Integer> entry : targets.entrySet()) {
            availableMilestones++;
            if (client.getRealSkillLevel(entry.getKey()) >= entry.getValue()) {
                completedMilestones++;
            }
        }

        int availableActivities = getAvailableActivityCount(currentRelease);
        int completedActivities = getCompletedActivityCount(currentRelease);

        return new CompletionProgress(
            completedQuests, availableQuests.size(),
            completedMilestones, availableMilestones,
            completedActivities, availableActivities);
    }

    /**
     * Completion skill goals follow the content available in the selected timeline.
     * Known quest requirements establish a floor; Rewind then adds a small mastery
     * buffer and rounds up to a clean five-level target. Skills with no quest-driven
     * requirement yet receive a modest target based on how long they have existed.
     */
    Map<Skill, Integer> getHistoricalSkillTargets() {
        return getHistoricalSkillTargets(currentRelease);
    }

    private Map<Skill, Integer> getHistoricalSkillTargets(Release release) {
        Map<Skill, Integer> targets = new LinkedHashMap<>();
        if (release == null)
        {
            return targets;
        }

        Map<Skill, Integer> requirements = new EnumMap<>(Skill.class);
        for (Quest quest : Release.getQuests(release)) {
            applyQuestRequirements(requirements, quest);
        }

        LocalDate selected = release.getDate().getLocalDate();
        for (Skill skill : Release.getSkills(release)) {
            if (HistoricalPermanentExclusions.isSkillPermanentlyLocked(skill)) continue;
            int requirement = requirements.getOrDefault(skill, 0);
            int target;
            if (requirement > 0) {
                target = roundUpFive(requirement + 5);
            } else {
                long ageMonths = java.time.temporal.ChronoUnit.MONTHS.between(
                    skillIntroductionDate(skill).withDayOfMonth(1), selected.withDayOfMonth(1));
                target = ageMonths < 6 ? 20 : ageMonths < 18 ? 30 : 40;
            }
            targets.put(skill, Math.min(target, historicalSkillCap(selected.getYear())));
        }
        return targets;
    }

    CompletionProgress getEraProgress(int year) {
        if (currentRelease == null || client.getGameState() != GameState.LOGGED_IN)
        {
            return new CompletionProgress(0, 0, 0, 0, 0, 0);
        }

        // Future eras relative to the player's selected timeline are intentionally
        // unavailable. The final 10 August 2007 endpoint exposes every era.
        if (year > currentRelease.getDate().getLocalDate().getYear())
        {
            return new CompletionProgress(0, 0, 0, 0, 0, 0);
        }

        Release eraRelease = null;
        LocalDate selectedDate = currentRelease.getDate().getLocalDate();
        for (Release release : Release.getRELEASES())
        {
            LocalDate releaseDate = release.getDate().getLocalDate();
            int releaseYear = releaseDate.getYear();

            if (releaseYear > year)
            {
                break;
            }

            // For the currently selected year, never count releases that are still
            // in the future relative to the chosen historical date.
            if (year == selectedDate.getYear() && releaseDate.isAfter(selectedDate))
            {
                break;
            }

            eraRelease = release;
        }

        if (eraRelease == null)
        {
            return new CompletionProgress(0, 0, 0, 0, 0, 0);
        }

        List<Quest> quests = Release.getQuests(eraRelease);
        int completedQuests = 0;
        for (Quest quest : quests)
        {
            if (isQuestComplete(quest))
            {
                completedQuests++;
            }
        }

        Map<Skill, Integer> targets = getHistoricalSkillTargets(eraRelease);
        int completedSkills = 0;
        for (Map.Entry<Skill, Integer> entry : targets.entrySet())
        {
            if (client.getRealSkillLevel(entry.getKey()) >= entry.getValue())
            {
                completedSkills++;
            }
        }

        int availableActivities = getAvailableActivityCount(eraRelease);
        int completedActivities = getCompletedActivityCount(eraRelease);

        return new CompletionProgress(
            completedQuests, quests.size(),
            completedSkills, targets.size(),
            completedActivities, availableActivities);
    }

    boolean isBarrowsAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._09_MAY_2005.getLocalDate());
    }

    boolean isBarrowsComplete() {
        // RuneLite's total Barrows reward-chest counter. Keep the numeric varp id
        // here so this remains compatible even when generated gameval constants move.
        return client.getGameState() == GameState.LOGGED_IN
            && client.getVarpValue(1502) > 0;
    }

    boolean isFightCavesAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._04_OCTOBER_2005.getLocalDate());
    }

    boolean isFightCavesComplete() {
        String value = configManager.getRSProfileConfiguration(
            CONFIG_GROUP_KEY, COMPLETION_FIGHT_CAVES_KEY);
        if (value == null)
        {
            value = configManager.getConfiguration(
                CONFIG_GROUP_KEY, COMPLETION_FIGHT_CAVES_KEY);
        }
        return Boolean.parseBoolean(value);
    }

    boolean isMageArenaCapeAvailable(Release release) {
        return availableFrom(release, ReleaseDate._22_SEPTEMBER_2003);
    }

    boolean isMageArenaCapeComplete() { return readProfileFlag(COMPLETION_MAGE_ARENA_CAPE_KEY); }

    boolean isShadesMorttonAvailable(Release release) {
        return availableFrom(release, ReleaseDate._18_OCTOBER_2004);
    }

    boolean isSlayerTowerAvailable(Release release) {
        return availableFrom(release, ReleaseDate._26_JANUARY_2005);
    }

    boolean isShadesMorttonComplete() { return readProfileFlag(COMPLETION_SHADES_MORTTON_KEY); }
    boolean isSlayerTowerComplete() { return readProfileFlag(COMPLETION_SLAYER_TOWER_KEY); }

    boolean isFishingTrawlerAvailable(Release release) {
        return availableFrom(release, ReleaseDate._28_JULY_2003);
    }

    boolean isCastleWarsAvailable(Release release) {
        return availableFrom(release, ReleaseDate._13_DECEMBER_2004);
    }

    boolean isBlastFurnaceAvailable(Release release) {
        return availableFrom(release, ReleaseDate._23_AUGUST_2005);
    }

    boolean isAgilityPyramidAvailable(Release release) {
        return availableFrom(release, ReleaseDate._16_JANUARY_2006);
    }

    boolean isTempleTrekkingAvailable(Release release) {
        return availableFrom(release, ReleaseDate._28_MARCH_2006);
    }

    boolean isTroubleBrewingAvailable(Release release) {
        return availableFrom(release, ReleaseDate._04_JULY_2006);
    }

    boolean isStrongholdSecurityAvailable(Release release) {
        return availableFrom(release, ReleaseDate._04_JULY_2006);
    }

    boolean isPyramidPlunderAvailable(Release release) {
        return availableFrom(release, ReleaseDate._17_JULY_2006);
    }

    boolean isFishingTrawlerComplete() { return readProfileFlag(COMPLETION_FISHING_TRAWLER_KEY); }
    boolean isCastleWarsComplete() { return readProfileFlag(COMPLETION_CASTLE_WARS_KEY); }
    boolean isBlastFurnaceComplete() { return readProfileFlag(COMPLETION_BLAST_FURNACE_KEY); }
    boolean isAgilityPyramidComplete() { return readProfileFlag(COMPLETION_AGILITY_PYRAMID_KEY); }
    boolean isTempleTrekkingComplete() { return readProfileFlag(COMPLETION_TEMPLE_TREKKING_KEY); }
    boolean isTroubleBrewingComplete() { return readProfileFlag(COMPLETION_TROUBLE_BREWING_KEY); }
    boolean isStrongholdSecurityComplete() { return readProfileFlag(COMPLETION_STRONGHOLD_SECURITY_KEY); }
    boolean isPyramidPlunderComplete() { return readProfileFlag(COMPLETION_PYRAMID_PLUNDER_KEY); }

    private static boolean availableFrom(Release release, ReleaseDate date) {
        return release != null && !release.getDate().getLocalDate().isBefore(date.getLocalDate());
    }

    boolean isFighterTorsoAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._04_JANUARY_2007.getLocalDate());
    }

    boolean isVoidSetAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._06_JUNE_2006.getLocalDate());
    }

    boolean isRuneDefenderAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._13_JUNE_2006.getLocalDate());
    }

    boolean isBonesToPeachesAvailable(Release release) {
        return release != null
            && !release.getDate().getLocalDate().isBefore(
                ReleaseDate._04_JANUARY_2006.getLocalDate());
    }

    boolean isFighterTorsoComplete() {
        return readProfileFlag(COMPLETION_FIGHTER_TORSO_KEY);
    }

    boolean isVoidSetComplete() {
        return readProfileFlag(COMPLETION_VOID_TOP_KEY)
            && readProfileFlag(COMPLETION_VOID_ROBE_KEY)
            && readProfileFlag(COMPLETION_VOID_GLOVES_KEY)
            && readProfileFlag(COMPLETION_VOID_HELM_KEY);
    }

    boolean isRuneDefenderComplete() {
        return readProfileFlag(COMPLETION_RUNE_DEFENDER_KEY);
    }

    boolean isBonesToPeachesComplete() {
        // MAGICTRAINING_BONESPEACHES is varbit 1505 in RuneLite's gameval data.
        // Unlike inferring from inventory or spell clicks, this is the game's
        // persistent account state for whether the MTA reward spell is unlocked.
        return client.getGameState() == GameState.LOGGED_IN
            && client.getVarbitValue(1505) > 0;
    }

    private boolean readProfileFlag(String key) {
        String value = configManager.getRSProfileConfiguration(CONFIG_GROUP_KEY, key);
        if (value == null)
        {
            value = configManager.getConfiguration(CONFIG_GROUP_KEY, key);
        }
        return Boolean.parseBoolean(value);
    }

    private void writeProfileFlag(String key) {
        configManager.setRSProfileConfiguration(CONFIG_GROUP_KEY, key, true);
        configManager.setConfiguration(CONFIG_GROUP_KEY, key, true);
    }

    private int getAvailableActivityCount(Release release) {
        int total = 0;
        if (isFishingTrawlerAvailable(release)) total++;
        if (isMageArenaCapeAvailable(release)) total++;
        if (isShadesMorttonAvailable(release)) total++;
        if (isCastleWarsAvailable(release)) total++;
        if (isSlayerTowerAvailable(release)) total++;
        if (isBarrowsAvailable(release)) total++;
        if (isBlastFurnaceAvailable(release)) total++;
        if (isFightCavesAvailable(release)) total++;
        if (isBonesToPeachesAvailable(release)) total++;
        if (isAgilityPyramidAvailable(release)) total++;
        if (isTempleTrekkingAvailable(release)) total++;
        if (isTroubleBrewingAvailable(release)) total++;
        if (isStrongholdSecurityAvailable(release)) total++;
        if (isPyramidPlunderAvailable(release)) total++;
        if (isVoidSetAvailable(release)) total++;
        if (isRuneDefenderAvailable(release)) total++;
        if (isFighterTorsoAvailable(release)) total++;
        return total;
    }

    private int getCompletedActivityCount(Release release) {
        int total = 0;
        if (isFishingTrawlerAvailable(release) && isFishingTrawlerComplete()) total++;
        if (isMageArenaCapeAvailable(release) && isMageArenaCapeComplete()) total++;
        if (isShadesMorttonAvailable(release) && isShadesMorttonComplete()) total++;
        if (isCastleWarsAvailable(release) && isCastleWarsComplete()) total++;
        if (isSlayerTowerAvailable(release) && isSlayerTowerComplete()) total++;
        if (isBarrowsAvailable(release) && isBarrowsComplete()) total++;
        if (isBlastFurnaceAvailable(release) && isBlastFurnaceComplete()) total++;
        if (isFightCavesAvailable(release) && isFightCavesComplete()) total++;
        if (isBonesToPeachesAvailable(release) && isBonesToPeachesComplete()) total++;
        if (isAgilityPyramidAvailable(release) && isAgilityPyramidComplete()) total++;
        if (isTempleTrekkingAvailable(release) && isTempleTrekkingComplete()) total++;
        if (isTroubleBrewingAvailable(release) && isTroubleBrewingComplete()) total++;
        if (isStrongholdSecurityAvailable(release) && isStrongholdSecurityComplete()) total++;
        if (isPyramidPlunderAvailable(release) && isPyramidPlunderComplete()) total++;
        if (isVoidSetAvailable(release) && isVoidSetComplete()) total++;
        if (isRuneDefenderAvailable(release) && isRuneDefenderComplete()) total++;
        if (isFighterTorsoAvailable(release) && isFighterTorsoComplete()) total++;
        return total;
    }

    private static int historicalSkillCap(int year) {
        switch (year) {
            case 2001: return 40;
            case 2002: return 50;
            case 2003: return 60;
            case 2004: return 70;
            case 2005: return 75;
            case 2006: return 80;
            default: return 85;
        }
    }

    private static int roundUpFive(int level) {
        return Math.min(99, ((level + 4) / 5) * 5);
    }

    private static void require(Map<Skill, Integer> requirements, Skill skill, int level) {
        requirements.merge(skill, level, Math::max);
    }

    /**
     * Curated hard skill requirements for quests represented in Rewind. Entries are
     * intentionally added only when the requirement is reliable; unknown quests do
     * not invent a requirement and instead fall back to the skill-age target.
     */
    private static void applyQuestRequirements(Map<Skill, Integer> r, Quest q) {
        switch (q) {
            case THE_KNIGHTS_SWORD:
                require(r, Skill.MINING, 10); break;
            case SCORPION_CATCHER:
                require(r, Skill.PRAYER, 31); break;
            case TRIBAL_TOTEM:
                require(r, Skill.THIEVING, 21); break;
            case FISHING_CONTEST:
                require(r, Skill.FISHING, 10); break;
            case TEMPLE_OF_IKOV:
                require(r, Skill.RANGED, 40); require(r, Skill.THIEVING, 42); break;
            case HOLY_GRAIL:
                require(r, Skill.ATTACK, 20); break;
            case SEA_SLUG:
                require(r, Skill.FIREMAKING, 30); break;
            case JUNGLE_POTION:
                require(r, Skill.HERBLORE, 3); break;
            case THE_GRAND_TREE:
                require(r, Skill.AGILITY, 25); break;
            case SHILO_VILLAGE:
                require(r, Skill.AGILITY, 32); require(r, Skill.CRAFTING, 20); break;
            case UNDERGROUND_PASS:
                require(r, Skill.RANGED, 25); break;
            case THE_TOURIST_TRAP:
                require(r, Skill.FLETCHING, 10); require(r, Skill.SMITHING, 20); break;
            case WATCHTOWER:
                require(r, Skill.AGILITY, 25); require(r, Skill.HERBLORE, 14);
                require(r, Skill.MAGIC, 15); require(r, Skill.MINING, 40);
                require(r, Skill.THIEVING, 15); break;
            case THE_DIG_SITE:
                require(r, Skill.AGILITY, 10); require(r, Skill.HERBLORE, 10);
                require(r, Skill.THIEVING, 25); break;
            case BIG_CHOMPY_BIRD_HUNTING:
                require(r, Skill.COOKING, 30); require(r, Skill.FLETCHING, 5);
                require(r, Skill.RANGED, 30); break;
            case ELEMENTAL_WORKSHOP_I:
                require(r, Skill.CRAFTING, 20); require(r, Skill.MINING, 20);
                require(r, Skill.SMITHING, 20); break;
            case TROLL_STRONGHOLD:
                require(r, Skill.AGILITY, 15); break;
            case TAI_BWO_WANNAI_TRIO:
                require(r, Skill.AGILITY, 15); require(r, Skill.COOKING, 30);
                require(r, Skill.FIREMAKING, 30); require(r, Skill.FISHING, 5); break;
            case REGICIDE:
                require(r, Skill.AGILITY, 56); require(r, Skill.CRAFTING, 10); break;
            case EADGARS_RUSE:
                require(r, Skill.HERBLORE, 31); break;
            case SHADES_OF_MORTTON:
                require(r, Skill.CRAFTING, 20); require(r, Skill.FIREMAKING, 5);
                require(r, Skill.HERBLORE, 15); break;
            case HORROR_FROM_THE_DEEP:
                require(r, Skill.AGILITY, 35); break;
            case HAUNTED_MINE:
                require(r, Skill.CRAFTING, 35); break;
            case TROLL_ROMANCE:
                require(r, Skill.AGILITY, 28); break;
            case IN_SEARCH_OF_THE_MYREQUE:
                require(r, Skill.AGILITY, 25); break;
            case CREATURE_OF_FENKENSTRAIN:
                require(r, Skill.CRAFTING, 20); require(r, Skill.THIEVING, 25); break;
            case ROVING_ELVES:
                require(r, Skill.AGILITY, 56); break;
            case GHOSTS_AHOY:
                require(r, Skill.AGILITY, 25); require(r, Skill.COOKING, 20); break;
            case MOUNTAIN_DAUGHTER:
                require(r, Skill.AGILITY, 20); break;
            case THE_FEUD:
                require(r, Skill.THIEVING, 30); break;
            case ZOGRE_FLESH_EATERS:
                require(r, Skill.HERBLORE, 8); require(r, Skill.RANGED, 30);
                require(r, Skill.SMITHING, 4); break;
            case THE_GOLEM:
                require(r, Skill.CRAFTING, 20); require(r, Skill.THIEVING, 25); break;
            case TEARS_OF_GUTHIX:
                require(r, Skill.CRAFTING, 20); require(r, Skill.FIREMAKING, 49);
                require(r, Skill.MINING, 20); break;
            case THE_GIANT_DWARF:
                require(r, Skill.CRAFTING, 12); require(r, Skill.FIREMAKING, 16);
                require(r, Skill.MAGIC, 33); require(r, Skill.THIEVING, 14); break;
            case THE_LOST_TRIBE:
                require(r, Skill.AGILITY, 13); require(r, Skill.MINING, 17);
                require(r, Skill.THIEVING, 13); break;
            case ONE_SMALL_FAVOUR:
                require(r, Skill.AGILITY, 36); require(r, Skill.CRAFTING, 25);
                require(r, Skill.HERBLORE, 18); require(r, Skill.SMITHING, 30); break;
            case BETWEEN_A_ROCK:
                require(r, Skill.DEFENCE, 30); require(r, Skill.MINING, 40);
                require(r, Skill.SMITHING, 50); break;
            case SPIRITS_OF_THE_ELID:
                require(r, Skill.MAGIC, 33); require(r, Skill.MINING, 37);
                require(r, Skill.RANGED, 37); require(r, Skill.THIEVING, 37); break;
            case RUM_DEAL:
                require(r, Skill.CRAFTING, 42); require(r, Skill.FARMING, 40);
                require(r, Skill.FISHING, 50); require(r, Skill.PRAYER, 47);
                require(r, Skill.SLAYER, 42); break;
            case CABIN_FEVER:
                require(r, Skill.AGILITY, 42); require(r, Skill.CRAFTING, 45);
                require(r, Skill.RANGED, 40); require(r, Skill.SMITHING, 50); break;
            case THE_HAND_IN_THE_SAND:
                require(r, Skill.CRAFTING, 49); require(r, Skill.THIEVING, 17); break;
            case ENAKHRAS_LAMENT:
                require(r, Skill.CRAFTING, 50); require(r, Skill.FIREMAKING, 45);
                require(r, Skill.MAGIC, 39); require(r, Skill.PRAYER, 43); break;
            case DARKNESS_OF_HALLOWVALE:
                require(r, Skill.AGILITY, 26); require(r, Skill.CONSTRUCTION, 5);
                require(r, Skill.CRAFTING, 32); require(r, Skill.MAGIC, 33);
                require(r, Skill.MINING, 20); require(r, Skill.STRENGTH, 40);
                require(r, Skill.THIEVING, 22); break;
            case THE_SLUG_MENACE:
                require(r, Skill.CRAFTING, 30); require(r, Skill.RUNECRAFT, 30);
                require(r, Skill.SLAYER, 30); require(r, Skill.THIEVING, 30); break;
            case ELEMENTAL_WORKSHOP_II:
                require(r, Skill.MAGIC, 20); require(r, Skill.SMITHING, 30); break;
            case ENLIGHTENED_JOURNEY:
                require(r, Skill.CRAFTING, 36); require(r, Skill.FARMING, 30);
                require(r, Skill.FIREMAKING, 20); break;
            case ANIMAL_MAGNETISM:
                require(r, Skill.CRAFTING, 19); require(r, Skill.RANGED, 30);
                require(r, Skill.SLAYER, 18); require(r, Skill.WOODCUTTING, 35); break;
            case COLD_WAR:
                require(r, Skill.AGILITY, 30); require(r, Skill.CONSTRUCTION, 34);
                require(r, Skill.CRAFTING, 30); require(r, Skill.HUNTER, 10);
                require(r, Skill.THIEVING, 15); break;
            case THE_GREAT_BRAIN_ROBBERY:
                require(r, Skill.CRAFTING, 16); require(r, Skill.CONSTRUCTION, 30);
                require(r, Skill.PRAYER, 50); break;
            case GRIM_TALES:
                require(r, Skill.AGILITY, 59); require(r, Skill.FARMING, 45);
                require(r, Skill.HERBLORE, 52); require(r, Skill.THIEVING, 58);
                require(r, Skill.WOODCUTTING, 71); break;
            case HEROES_QUEST:
                require(r, Skill.COOKING, 53); require(r, Skill.FISHING, 53);
                require(r, Skill.HERBLORE, 25); require(r, Skill.MINING, 50); break;
            case LOST_CITY:
                require(r, Skill.CRAFTING, 31); require(r, Skill.WOODCUTTING, 36); break;
            case FAMILY_CREST:
                require(r, Skill.CRAFTING, 40); require(r, Skill.MAGIC, 59);
                require(r, Skill.MINING, 40); require(r, Skill.SMITHING, 40); break;
            case LEGENDS_QUEST:
                require(r, Skill.AGILITY, 50); require(r, Skill.CRAFTING, 50);
                require(r, Skill.HERBLORE, 45); require(r, Skill.MAGIC, 56);
                require(r, Skill.MINING, 52); require(r, Skill.PRAYER, 42);
                require(r, Skill.SMITHING, 50); require(r, Skill.STRENGTH, 50);
                require(r, Skill.THIEVING, 50); require(r, Skill.WOODCUTTING, 50); break;
            case DESERT_TREASURE_I:
                require(r, Skill.FIREMAKING, 50); require(r, Skill.MAGIC, 50);
                require(r, Skill.SLAYER, 10); require(r, Skill.THIEVING, 53); break;
            case MOURNINGS_END_PART_I:
                require(r, Skill.RANGED, 60); require(r, Skill.THIEVING, 50); break;
            case MOURNINGS_END_PART_II:
                require(r, Skill.AGILITY, 56); break;
            case SWAN_SONG:
                require(r, Skill.CRAFTING, 40); require(r, Skill.COOKING, 62);
                require(r, Skill.FIREMAKING, 42); require(r, Skill.FISHING, 62);
                require(r, Skill.MAGIC, 66); require(r, Skill.SMITHING, 45); break;
            case LUNAR_DIPLOMACY:
                require(r, Skill.CRAFTING, 61); require(r, Skill.DEFENCE, 40);
                require(r, Skill.FIREMAKING, 49); require(r, Skill.HERBLORE, 5);
                require(r, Skill.MAGIC, 65); require(r, Skill.MINING, 60);
                require(r, Skill.WOODCUTTING, 55); break;
            case DREAM_MENTOR:
                // 85 Combat is composite, so do not invent per-skill requirements.
                break;
            case KINGS_RANSOM:
                require(r, Skill.DEFENCE, 65); require(r, Skill.MAGIC, 45); break;
            case FORGETTABLE_TALE:
                require(r, Skill.COOKING, 22); require(r, Skill.FARMING, 17); break;
            case GARDEN_OF_TRANQUILLITY:
                require(r, Skill.FARMING, 25); break;
            case SKIPPY_AND_THE_MOGRES:
                require(r, Skill.COOKING, 20); break;
            case SHADOW_OF_THE_STORM:
                require(r, Skill.CRAFTING, 30); break;
            case DEVIOUS_MINDS:
                require(r, Skill.FLETCHING, 50); require(r, Skill.RUNECRAFT, 50);
                require(r, Skill.SMITHING, 65); break;
            case IN_AID_OF_THE_MYREQUE:
                require(r, Skill.CRAFTING, 25); require(r, Skill.MAGIC, 7);
                require(r, Skill.MINING, 15); break;
            case ROYAL_TROUBLE:
                require(r, Skill.AGILITY, 40); require(r, Skill.SLAYER, 40); break;
            case DEATH_TO_THE_DORGESHUUN:
                require(r, Skill.AGILITY, 23); require(r, Skill.THIEVING, 23); break;
            case FAIRYTALE_II__CURE_A_QUEEN:
                require(r, Skill.FARMING, 49); require(r, Skill.HERBLORE, 57);
                require(r, Skill.THIEVING, 40); break;
            case THE_EYES_OF_GLOUPHRIE:
                require(r, Skill.CONSTRUCTION, 5); require(r, Skill.MAGIC, 46); break;
            case MY_ARMS_BIG_ADVENTURE:
                require(r, Skill.FARMING, 29); require(r, Skill.WOODCUTTING, 10); break;
            case EAGLES_PEAK:
                require(r, Skill.HUNTER, 27); break;
            case LAIR_OF_TARN_RAZORLOR:
                require(r, Skill.SLAYER, 40); break;
            case THE_FREMENNIK_ISLES:
                require(r, Skill.AGILITY, 40); require(r, Skill.CONSTRUCTION, 20);
                require(r, Skill.CRAFTING, 46); require(r, Skill.WOODCUTTING, 56); break;
            case TOWER_OF_LIFE:
                require(r, Skill.CONSTRUCTION, 10); break;
            case WHAT_LIES_BELOW:
                require(r, Skill.MINING, 42); require(r, Skill.RUNECRAFT, 35); break;
            case OLAFS_QUEST:
                require(r, Skill.FIREMAKING, 40); require(r, Skill.WOODCUTTING, 50); break;
            case ANOTHER_SLICE_OF_HAM:
                require(r, Skill.ATTACK, 15); require(r, Skill.PRAYER, 25); break;
            case BARBARIAN_TRAINING:
                require(r, Skill.AGILITY, 15); require(r, Skill.CRAFTING, 11);
                require(r, Skill.FARMING, 15); require(r, Skill.FIREMAKING, 35);
                require(r, Skill.FISHING, 55); require(r, Skill.SMITHING, 5);
                require(r, Skill.STRENGTH, 15); break;
            case MAGE_ARENA_I:
                require(r, Skill.MAGIC, 60); break;
            case RECIPE_FOR_DISASTER__ANOTHER_COOKS_QUEST:
                require(r, Skill.COOKING, 10); break;
            case RECIPE_FOR_DISASTER__EVIL_DAVE:
                require(r, Skill.COOKING, 25); break;
            case RECIPE_FOR_DISASTER__PIRATE_PETE:
                require(r, Skill.COOKING, 31); require(r, Skill.CRAFTING, 42); break;
            case RECIPE_FOR_DISASTER__LUMBRIDGE_GUIDE:
                require(r, Skill.COOKING, 40); break;
            case RECIPE_FOR_DISASTER__SKRACH_UGLOGWEE:
                require(r, Skill.COOKING, 41); require(r, Skill.FIREMAKING, 20); break;
            case RECIPE_FOR_DISASTER__KING_AWOWOGEI:
                require(r, Skill.AGILITY, 48); require(r, Skill.COOKING, 70); break;
            // Quests not listed here have no direct mandatory skill requirement.
            // Prerequisite quest requirements are already represented cumulatively.
            default:
                break;
        }
    }

    private LocalDate skillIntroductionDate(Skill skill) {
        for (Release release : Release.getRELEASES()) {
            if (release.getSkills() != null && release.getSkills().contains(skill)) {
                return release.getDate().getLocalDate();
            }
        }
        return ReleaseDate._04_JANUARY_2001.getLocalDate();
    }

    List<Quest> getAvailableCompletionQuests() {
        if (currentRelease == null)
        {
            return Collections.emptyList();
        }

        // The Completion Log is a milestone checklist for the selected historical
        // release, not a cumulative quest list. Earlier quests remain playable but
        // are hidden here once the player advances to a later timeline milestone.
        // The final 10 August 2007 endpoint is the exception: it is the full
        // historical completion view and therefore shows every supported quest.
        if (currentRelease.getDate() == ReleaseDate._10_AUGUST_2007)
        {
            return new ArrayList<>(Release.getQuests(currentRelease));
        }

        return currentRelease.getQuests() == null
            ? Collections.emptyList()
            : new ArrayList<>(currentRelease.getQuests());
    }

    boolean isQuestComplete(Quest quest) {
        if (quest == null || client.getGameState() != GameState.LOGGED_IN) return false;
        try {
            return quest.getState(client) == QuestState.FINISHED;
        } catch (RuntimeException ex) {
            log.debug("Unable to read quest state for {}", quest.getName(), ex);
            return false;
        }
    }

    Map<Skill, Integer> getAvailableCompletionSkills() {
        Map<Skill, Integer> levels = new LinkedHashMap<>();
        if (currentRelease == null || client.getGameState() != GameState.LOGGED_IN) return levels;
        for (Skill skill : Release.getSkills(currentRelease)) {
            if (!HistoricalPermanentExclusions.isSkillPermanentlyLocked(skill)) {
                levels.put(skill, client.getRealSkillLevel(skill));
            }
        }
        return levels;
    }

    private void updateAdditionalRegions() {
        HistoricalRegionState.setAdditionallyUnlocked(config.unlockGrandExchange()
            ? Collections.singleton(GRAND_EXCHANGE_REGION) : Collections.emptySet());
    }

    private boolean isNpcUnlocked(NPC npc) throws ParseException {
        WorldPoint location = npc.getWorldLocation();
        WorldView worldView = npc.getWorldView();
        LocalPoint localPoint = npc.getLocalLocation();
        if (worldView != null && localPoint != null) {
            location = WorldPoint.fromLocalInstance(worldView.getScene(), localPoint, worldView.getPlane());
        }
        if (config.unlockGrandExchange() && location.getRegionID() == GRAND_EXCHANGE_REGION) {
            return true;
        }
        if (!HistoricalRegionState.isTileUnlocked(location)) {
            return false;
        }
        return EntityDefinition.isMonsterUnlocked(npc.getId(), npc.getName(), config.release().getDate());
    }

    @VisibleForTesting
    static boolean isSceneLocationAction(net.runelite.api.MenuAction action) {
        if (action == null) return false;
        String name = action.name();
        return name.equals("WALK")
            || name.contains("GAME_OBJECT")
            || name.contains("GROUND_ITEM");
    }

    private WorldPoint getMenuWorldPoint(MenuOptionClicked event) {
        Player player = client.getLocalPlayer();
        if (player == null) return null;
        WorldView worldView = player.getWorldView();

        // WALK menu parameters are not a reliable way to identify the clicked tile
        // across every RuneLite input path. RuneLite itself exposes the selected scene
        // tile for ordinary scene walking, so prefer that exact tile.
        if (event.getMenuAction() == net.runelite.api.MenuAction.WALK) {
            Tile selectedTile = client.getSelectedSceneTile();
            if (selectedTile != null) {
                return WorldPoint.fromLocalInstance(
                    worldView.getScene(), selectedTile.getLocalLocation(), selectedTile.getPlane());
            }
        }

        LocalPoint localPoint = LocalPoint.fromScene(event.getParam0(), event.getParam1(), worldView);
        if (localPoint == null) return null;
        return WorldPoint.fromLocalInstance(worldView.getScene(), localPoint, worldView.getPlane());
    }

    @VisibleForTesting
    static boolean isGrandExchangeGroup(int group) {
        return group == InterfaceID.GE_OFFERS || group == InterfaceID.GE_OFFERS_SIDE
            || group == InterfaceID.GE_PRICELIST;
    }

    @VisibleForTesting
    static boolean isPrayerActivation(String option) {
        String value = option == null ? "" : option.toLowerCase(Locale.ROOT);
        return value.contains("activate") && !value.contains("deactivate");
    }

    private boolean isPrayerWidgetUnlocked(Widget widget, String target) {
        List<Prayer> unlocked = Release.getPrayers(currentRelease);
        int widgetId = widget == null ? -1 : widget.getId();
        for (RewindPrayer prayer : RewindPrayer.values()) {
            if (prayer.getPackedID() == widgetId || prayer.getName().equalsIgnoreCase(target)) {
                return unlocked.contains(prayer.getPrayer());
            }
        }
        // Any prayer absent from the historical catalogue is post-backup content.
        return false;
    }

	public static int getCenterX(Widget window, int width) {
		return (window.getWidth() / 2) - (width / 2);
	}

	public static int getCenterY(Widget window, int height) {
		return (window.getHeight() / 2) - (height / 2);
	}
}
