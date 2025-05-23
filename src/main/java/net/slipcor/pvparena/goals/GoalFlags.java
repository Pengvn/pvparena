package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PADeathInfo;
import net.slipcor.pvparena.core.ColorUtils;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.events.goal.PAGoalFlagBringEvent;
import net.slipcor.pvparena.events.goal.PAGoalFlagSaveEvent;
import net.slipcor.pvparena.events.goal.PAGoalFlagTakeEvent;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.managers.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Flags"
 * </pre>
 * <p/>
 * Well, should be clear. Capture flags, bring them home, get points, win.
 *
 * @author slipcor
 */

public class GoalFlags extends AbstractFlagGoal {
    public GoalFlags() {
        super("Flags");
    }

    @Override
    protected CFG getFlagEffectCfg() {
        return CFG.GOAL_FLAGS_FLAGEFFECT;
    }

    @Override
    protected CFG getFlagLivesCfg() {
        return CFG.GOAL_FLAGS_LIVES;
    }

    @Override
    protected boolean doesAutoColorBlocks() {
        return this.arena.getConfig().getBoolean(CFG.GOAL_FLAGS_AUTOCOLOR);
    }

    @Override
    protected boolean hasWoolHead() {
        return this.arena.getConfig().getBoolean(CFG.GOAL_FLAGS_WOOLFLAGHEAD);
    }

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    /**
     * hook into an interacting player
     *
     * @param player the interacting player
     * @param event  the interact event
     * @return true if event has been handled
     */
    @Override
    public boolean checkInteract(final Player player, final PlayerInteractEvent event) {
        Block block = event.getClickedBlock();

        if (block == null) {
            return false;
        }
        debug(this.arena, player, "checking interact");
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[CTF] already ending!!");
            return false;
        }

        final ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(player);

        if (!this.isSameTypeThanFlags(block.getType())) {
            debug(this.arena, player, "block, but not flag");
            return false;
        }
        debug(this.arena, player, "flag click!");

        Vector vLoc;
        Vector vFlag = null;

        if (this.getFlagMap().containsValue(player.getName())) {
            debug(this.arena, player, "player " + player.getName() + " has got a flag");
            vLoc = block.getLocation().toVector();
            final ArenaTeam arenaTeam = arenaPlayer.getArenaTeam();
            debug(this.arena, player, "block: " + vLoc);
            if (!SpawnManager.getBlocksStartingWith(this.arena,  "flag", arenaTeam.getName()).isEmpty()) {
                vFlag = SpawnManager
                        .getBlockNearest(
                                SpawnManager.getBlocksStartingWith(this.arena, "flag", arenaTeam.getName()),
                                new PABlockLocation(player.getLocation()))
                        .toLocation().toVector();
            } else {
                debug(this.arena, player, arenaTeam + "flag = null");
            }

            debug(this.arena, player, "player is in the team " + arenaTeam);
            if (vFlag != null && vLoc.distance(vFlag) < 2) {

                debug(this.arena, player, "player is at his flag");

                if (this.getFlagMap().containsKey(arenaTeam) || this.getFlagMap().containsKey(this.touchdownTeam)) {
                    debug(this.arena, player, "the flag of the own team is taken!");

                    if (this.arena.getConfig().getBoolean(
                            CFG.GOAL_FLAGS_MUSTBESAFE)
                            && !this.getFlagMap().containsKey(this.touchdownTeam)) {
                        debug(this.arena, player, "cancelling");

                        this.arena.msg(player, MSG.GOAL_FLAGS_NOTSAFE);
                        return false;
                    }
                }

                ArenaTeam flagTeam = this.getHeldFlagTeam(arenaPlayer);

                debug(this.arena, player, "the flag belongs to team " + flagTeam);

                try {
                    if (this.touchdownTeam.equals(flagTeam)) {
                        this.arena.broadcast(Language.parse(MSG.GOAL_FLAGS_TOUCHHOME,
                                arenaTeam.colorizePlayer(arenaPlayer) + ChatColor.YELLOW));
                    } else {
                        this.arena.broadcast(Language.parse(
                                MSG.GOAL_FLAGS_BROUGHTHOME, arenaTeam.colorizePlayer(arenaPlayer)
                                        + ChatColor.YELLOW,
                                flagTeam.getColoredName()
                                        + ChatColor.YELLOW, String
                                        .valueOf(this.getTeamLifeMap().get(flagTeam) - 1)));
                    }
                    this.getFlagMap().remove(flagTeam);
                } catch (final Exception e) {
                    Bukkit.getLogger().severe(
                            "[PVP Arena] team unknown/no lives: " + flagTeam);
                    e.printStackTrace();
                }
                if (this.touchdownTeam.equals(flagTeam)) {
                    this.releaseFlag(this.touchdownTeam);
                } else {
                    this.releaseFlag(flagTeam);
                }
                this.removeEffects(player);
                if (this.hasWoolHead()) {
                    if (this.getHeadGearMap().get(ArenaPlayer.fromPlayer(player)) == null) {
                        player.getInventory().setHelmet(
                                new ItemStack(Material.AIR, 1));
                    } else {
                        player.getInventory().setHelmet(
                                this.getHeadGearMap().get(ArenaPlayer.fromPlayer(player)).clone());
                        this.getHeadGearMap().remove(ArenaPlayer.fromPlayer(player));
                    }
                }

                if (this.touchdownTeam.equals(flagTeam)) {
                    this.checkAndCommitTouchdown(this.arena, arenaPlayer.getArenaTeam());
                } else {
                    this.reduceLivesCheckEndAndCommit(this.arena, flagTeam);
                }
                final PAGoalFlagBringEvent gEvent = new PAGoalFlagBringEvent(this.arena, this, arenaPlayer, arenaTeam);
                Bukkit.getPluginManager().callEvent(gEvent);
                return true;
            }
        } else {
            final ArenaTeam pTeam = arenaPlayer.getArenaTeam();
            if (pTeam == null) {
                return false;
            }

            final Set<ArenaTeam> setTeam = new HashSet<>(this.arena.getTeams());

            setTeam.add(new ArenaTeam(TOUCHDOWN, "BLACK"));
            for (ArenaTeam arenaTeam : setTeam) {

                if (arenaTeam.equals(pTeam)) {
                    debug(this.arena, player, "equals!OUT! ");
                    continue;
                }

                if (arenaTeam.isEmpty() && !TOUCHDOWN.equals(arenaTeam.getName())) {
                    debug(this.arena, player, "size!OUT! ");
                    continue; // dont check for inactive teams
                }

                if (this.getFlagMap().containsKey(arenaTeam)) {
                    debug(this.arena, player, "taken!OUT! ");
                    continue; // already taken
                }

                debug(this.arena, player, "checking for flag of team " + arenaTeam);
                vLoc = block.getLocation().toVector();
                debug(this.arena, player, "block: " + vLoc);

                if (!SpawnManager.getBlocksStartingWith(this.arena, FLAG, arenaTeam.getName()).isEmpty()) {
                    vFlag = SpawnManager
                            .getBlockNearest(
                                    SpawnManager.getBlocksStartingWith(this.arena, FLAG, arenaTeam.getName()),
                                    new PABlockLocation(player.getLocation()))
                            .toLocation().toVector();
                }

                if (vFlag != null && vLoc.distance(vFlag) < 2) {
                    debug(this.arena, player, "flag found!");
                    debug(this.arena, player, "vFlag: " + vFlag);

                    if (TOUCHDOWN.equals(arenaTeam.getName())) {

                        this.arena.broadcast(Language.parse(
                                MSG.GOAL_FLAGS_GRABBEDTOUCH,
                                pTeam.colorizePlayer(arenaPlayer) + ChatColor.YELLOW));
                    } else {

                        this.arena.broadcast(Language
                                .parse(MSG.GOAL_FLAGS_GRABBED,
                                        pTeam.colorizePlayer(arenaPlayer)
                                                + ChatColor.YELLOW,
                                        arenaTeam.getColoredName()
                                                + ChatColor.YELLOW));
                    }
                    try {
                        this.getHeadGearMap().put(arenaPlayer, player.getInventory().getHelmet().clone());
                    } catch (final Exception ignored) {
                    }

                    if (this.hasWoolHead()) {
                        final ItemStack itemStack = new ItemStack(this.getFlagOverrideTeamMaterial(this.arena, arenaTeam));
                        player.getInventory().setHelmet(itemStack);
                    }
                    this.applyEffects(player);

                    this.takeFlag(new PABlockLocation(vFlag.toLocation(block.getWorld())));
                    this.getFlagMap().put(arenaTeam, player.getName());

                    final PAGoalFlagTakeEvent gEvent = new PAGoalFlagTakeEvent(this.arena, this, arenaPlayer, arenaTeam);
                    Bukkit.getPluginManager().callEvent(gEvent);

                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void disconnect(final ArenaPlayer arenaPlayer) {
        if (this.getFlagMap().isEmpty()) {
            return;
        }
        final ArenaTeam flagTeam = this.getHeldFlagTeam(arenaPlayer);

        if(flagTeam == null) {
            return;
        }

        if (flagTeam.equals(this.touchdownTeam)) {
            this.arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPEDTOUCH, arenaPlayer
                    .getArenaTeam().getColorCodeString()
                    + arenaPlayer.getName()
                    + ChatColor.YELLOW));
        } else {
            this.arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPED, arenaPlayer
                    .getArenaTeam().getColorCodeString()
                    + arenaPlayer.getName()
                    + ChatColor.YELLOW, flagTeam.getName() + ChatColor.YELLOW));
        }

        this.getFlagMap().remove(flagTeam);
        if (this.getHeadGearMap().get(arenaPlayer) != null) {
            arenaPlayer.getPlayer().getInventory().setHelmet(this.getHeadGearMap().get(arenaPlayer).clone());
            this.getHeadGearMap().remove(arenaPlayer);
        }

        this.releaseFlag(flagTeam);
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        Config cfg = this.arena.getConfig();
        sender.sendMessage("flag effect: " + cfg.getString(CFG.GOAL_FLAGS_FLAGEFFECT));
        sender.sendMessage("lives: " + cfg.getInt(CFG.GOAL_FLAGS_LIVES));
        sender.sendMessage("auto color: " + StringParser.colorVar(cfg.getBoolean(CFG.GOAL_FLAGS_AUTOCOLOR)));
        sender.sendMessage(StringParser.colorVar("mustbesafe", cfg.getBoolean(CFG.GOAL_FLAGS_MUSTBESAFE))
                + " | " + StringParser.colorVar("flaghead", this.hasWoolHead())
                + " | " + StringParser.colorVar("alterOnCatch", cfg.getBoolean(CFG.GOAL_FLAGS_ALTERONCATCH)));
    }

    @Override
    public void parsePlayerDeath(final ArenaPlayer arenaPlayer,
                                 final PADeathInfo deathInfo) {

        if (this.getFlagMap().isEmpty()) {
            debug(arenaPlayer, "no flags set!!");
            return;
        }
        final ArenaTeam flagTeam = this.getHeldFlagTeam(arenaPlayer);

        if(flagTeam == null) {
            return;
        }

        if (this.touchdownTeam.equals(flagTeam)) {
            this.arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPEDTOUCH, arenaPlayer
                    .getArenaTeam().getColorCodeString()
                    + arenaPlayer.getName()
                    + ChatColor.YELLOW));
        } else {
            this.arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPED, arenaPlayer
                            .getArenaTeam().colorizePlayer(arenaPlayer) + ChatColor.YELLOW,
                    flagTeam.getColoredName() + ChatColor.YELLOW));
        }

        this.getFlagMap().remove(flagTeam);
        if (this.getHeadGearMap().get(arenaPlayer) != null) {
            arenaPlayer.getPlayer().getInventory().setHelmet(this.getHeadGearMap().get(arenaPlayer).clone());
            this.getHeadGearMap().remove(arenaPlayer);
        }

        final PAGoalFlagSaveEvent gEvent = new PAGoalFlagSaveEvent(this.arena, this,
                Optional.ofNullable(deathInfo.getKiller()).map(ArenaPlayer::fromPlayer).orElse(null), flagTeam);
        Bukkit.getPluginManager().callEvent(gEvent);

        this.releaseFlag(flagTeam);
    }

    @Override
    public void checkInventory(InventoryClickEvent event) throws GameplayException {
        if (this.isIrrelevantInventoryClickEvent(event)) {
            return;
        }

        final Player player = (Player) event.getWhoClicked();

        if (this.hasWoolHead() && event.getSlotType() == InventoryType.SlotType.ARMOR &&
                this.getFlagMap().containsValue(player.getName())) {
            event.setCancelled(true);
            throw new GameplayException("INVENTORY not allowed");
        }
    }

    /**
     * take an arena flag
     *
     * @param paBlockLocation the location to take/reset
     */
    private void takeFlag(final PABlockLocation paBlockLocation) {
        if (paBlockLocation == null) {
            return;
        }

        if (this.arena.getConfig().getBoolean(CFG.GOAL_FLAGS_ALTERONCATCH)) {
            Block flagBlock = paBlockLocation.toLocation().getBlock();

            if (ColorUtils.isColorableMaterial(flagBlock.getType())) {
                ColorUtils.setNewFlagColor(flagBlock, ChatColor.WHITE);
            } else {
                flagBlock.setType(Material.BEDROCK);
            }
        }
    }
}
