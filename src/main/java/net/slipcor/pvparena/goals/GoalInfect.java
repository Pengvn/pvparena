package net.slipcor.pvparena.goals;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.*;
import net.slipcor.pvparena.classes.PADeathInfo;
import net.slipcor.pvparena.classes.PASpawn;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;

import net.slipcor.pvparena.events.PATeamChangeEvent;
import net.slipcor.pvparena.events.goal.PAGoalEndEvent;
import net.slipcor.pvparena.events.goal.PAGoalPlayerDeathEvent;
import net.slipcor.pvparena.exceptions.GameplayException;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModule;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.InventoryManager;
import net.slipcor.pvparena.managers.TeleportManager;
import net.slipcor.pvparena.managers.WorkflowManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static net.slipcor.pvparena.config.Debugger.debug;

/**
 * <pre>
 * Arena Goal class "Infect"
 * </pre>
 * <p/>
 * Infected players kill ppl to enhance their team. Configurable lives
 *
 * @author slipcor
 */

public class GoalInfect extends ArenaGoal {

    private static final String INFECTED = "infected";
    private static final String GETPROTECT = "getprotect";
    private static final String SETPROTECT = "setprotect";

    private ArenaTeam infectedTeam;

    public GoalInfect() {
        super("Infect");
    }

    private EndRunnable endRunner;

    @Override
    public String version() {
        return PVPArena.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean isFreeForAll() {
        return true;
    }

    @Override
    public boolean checkEnd() {
        final int count = this.getActivePlayerLifeMap().size();

        return count <= 1 || this.anyTeamEmpty(); // yep. only one player left. go!
    }

    private boolean anyTeamEmpty() {
        return this.arena.getTeams().stream()
                .filter(team -> team.getTeamMembers()
                        .stream()
                        .noneMatch(player -> asList(PlayerStatus.FIGHT, PlayerStatus.DEAD).contains(player.getStatus()))
                )
                .peek(team -> debug(this.arena, "team empty: {}", team.getName()))
                .findAny()
                .isPresent();
    }

    @Override
    public Set<PASpawn> checkForMissingSpawns(Set<PASpawn> spawns) {

        Set<PASpawn> missing = SpawnManager.getMissingFFACustom(spawns, INFECTED);
        missing.addAll(SpawnManager.getMissingFFASpawn(this.arena, spawns));

        return missing;
    }

    @Override
    public boolean checkCommand(final String string) {
        return GETPROTECT.equalsIgnoreCase(string) || SETPROTECT.equalsIgnoreCase(string);
    }

    @Override
    public void checkBreak(BlockBreakEvent event) throws GameplayException {
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(event.getPlayer());
        if (this.arena.equals(arenaPlayer.getArena()) && arenaPlayer.getStatus() == PlayerStatus.FIGHT
                && this.infectedTeam.equals(arenaPlayer.getArenaTeam())) {
            if (PlayerPrevention.has(
                    this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.BREAK
            )) {
                event.setCancelled(true);
                this.arena.msg(event.getPlayer(), MSG.PLAYER_PREVENTED_BREAK);
                throw new GameplayException("BREAK not allowed");
            } else if (event.getBlock().getType() == Material.TNT &&
                    PlayerPrevention.has(
                            this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.TNTBREAK
                    )) {
                event.setCancelled(true);
                this.arena.msg(event.getPlayer(), MSG.PLAYER_PREVENTED_TNTBREAK);
                throw new GameplayException("TNTBREAK not allowed");
            }
        }
    }

    @Override
    public void checkCraft(CraftItemEvent event) throws GameplayException {
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(((Player) event.getInventory().getHolder()).getName());
        if (this.arena.equals(arenaPlayer.getArena())
                && arenaPlayer.getStatus() == PlayerStatus.FIGHT
                && this.infectedTeam.equals(arenaPlayer.getArenaTeam())
                && PlayerPrevention.has(this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.CRAFT)
        ) {
            event.setCancelled(true);
            this.arena.msg(event.getWhoClicked(), MSG.PLAYER_PREVENTED_CRAFT);
            throw new GameplayException("CRAFT not allowed");
        }
    }

    @Override
    public void checkDrop(PlayerDropItemEvent event) throws GameplayException {
        ArenaPlayer arenaPlayer = ArenaPlayer.fromPlayer(event.getPlayer().getName());
        if (this.arena.equals(arenaPlayer.getArena())
                && arenaPlayer.getStatus() == PlayerStatus.FIGHT
                && this.infectedTeam.equals(arenaPlayer.getArenaTeam())
                && PlayerPrevention.has(this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.DROP)
        ) {
            event.setCancelled(true);
            this.arena.msg(event.getPlayer(), MSG.PLAYER_PREVENTED_DROP);
            throw new GameplayException("DROP not allowed");
        }
    }

    @Override
    public void checkInventory(InventoryClickEvent event) throws GameplayException {
        ArenaPlayer ap = ArenaPlayer.fromPlayer(event.getWhoClicked().getName());
        if (this.arena.equals(ap.getArena())
                && ap.getStatus() == PlayerStatus.FIGHT
                && INFECTED.equals(ap.getArenaTeam().getName())
                && PlayerPrevention.has(this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.INVENTORY)
        ) {
            event.setCancelled(true);
            event.getWhoClicked().closeInventory();
            this.arena.msg(event.getWhoClicked(), MSG.PLAYER_PREVENTED_INVENTORY);
            throw new GameplayException("INVENTORY not allowed");
        }
    }

    @Override
    public void checkPickup(EntityPickupItemEvent event) throws GameplayException {
        ArenaPlayer ap = ArenaPlayer.fromPlayer(event.getEntity().getName());
        if (this.arena.equals(ap.getArena())
                && ap.getStatus() == PlayerStatus.FIGHT
                && INFECTED.equals(ap.getArenaTeam().getName())
                && PlayerPrevention.has(this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.PICKUP)
        ) {
            event.setCancelled(true);
            throw new GameplayException("PICKUP not allowed");
        }
    }

    @Override
    public void checkPlace(BlockPlaceEvent event) throws GameplayException {
        ArenaPlayer ap = ArenaPlayer.fromPlayer(event.getPlayer().getName());
        if (this.arena.equals(ap.getArena())
                && ap.getStatus() == PlayerStatus.FIGHT
                && INFECTED.equals(ap.getArenaTeam().getName())) {
            if (PlayerPrevention.has(
                    this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.PLACE
            )) {
                event.setCancelled(true);
                this.arena.msg(event.getPlayer(), MSG.PLAYER_PREVENTED_PLACE);
                throw new GameplayException("PLACE not allowed");
            } else if (event.getBlock().getType() == Material.TNT &&
                    PlayerPrevention.has(
                            this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS), PlayerPrevention.TNT
                    )) {
                event.setCancelled(true);
                this.arena.msg(event.getPlayer(), MSG.PLAYER_PREVENTED_TNT);
                throw new GameplayException("TNT not allowed");
            }
        }
    }

    @Override
    public Boolean shouldRespawnPlayer(final ArenaPlayer arenaPlayer, PADeathInfo deathInfo) {
        if (this.getPlayerLifeMap().containsKey(arenaPlayer)) {
            final int iLives = this.getPlayerLifeMap().get(arenaPlayer);
            debug(arenaPlayer, "lives before death: " + iLives);
            return iLives > 1 || !INFECTED.equals(arenaPlayer.getArenaTeam().getName());
        }
        return true;
    }

    @Override
    public boolean overridesStart() {
        return true;
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {

        int value = this.arena.getConfig().getInt(CFG.GOAL_INFECTED_PPROTECTS);

        if (GETPROTECT.equalsIgnoreCase(args[0])) {
            List<String> values = new ArrayList<>();


            for (PlayerPrevention pp : PlayerPrevention.values()) {
                if (pp == null) {
                    continue;
                }
                values.add((PlayerPrevention.has(value, pp) ?
                        ChatColor.GREEN.toString() : ChatColor.RED.toString()) + pp.name());
            }
            this.arena.msg(sender, MSG.GOAL_INFECTED_IPROTECT, StringParser.joinList(values, (ChatColor.WHITE + ", ")));

        } else if (SETPROTECT.equalsIgnoreCase(args[0])) {
            // setprotect [value] {true|false}
            if (args.length < 2) {
                this.arena.msg(sender, MSG.ERROR_INVALID_ARGUMENT_COUNT, String.valueOf(args.length), "2|3");
                return;
            }

            try {
                final PlayerPrevention pp = PlayerPrevention.valueOf(args[1].toUpperCase());
                final boolean has = PlayerPrevention.has(value, pp);

                debug(this.arena, "plain value: " + value);
                debug(this.arena, "checked: " + pp.name());
                debug(this.arena, "has: " + has);

                boolean future = !has;

                if (args.length > 2) {
                    if (StringParser.isNegativeValue(args[2])) {
                        future = false;
                    } else if (StringParser.isPositiveValue(args[2])) {
                        future = true;
                    }
                }

                if (future) {
                    value = value | (int) Math.pow(2, pp.ordinal());
                    this.arena.msg(
                            sender,
                            Language.parse(MSG.GOAL_INFECTED_IPROTECT_SET,
                                    pp.name(), ChatColor.GREEN + "true") + ChatColor.YELLOW);
                } else {
                    value = value ^ (int) Math.pow(2, pp.ordinal());
                    this.arena.msg(
                            sender,
                            Language.parse(MSG.GOAL_INFECTED_IPROTECT_SET,
                                    pp.name(), ChatColor.RED + "false") + ChatColor.YELLOW);
                }
                this.arena.getConfig().set(CFG.GOAL_INFECTED_PPROTECTS, value);
            } catch (final Exception e) {
                List<String> values = new ArrayList<>();


                for (PlayerPrevention pp : PlayerPrevention.values()) {
                    values.add(pp.name());
                }
                this.arena.msg(sender, MSG.ERROR_ARGUMENT, args[1], StringParser.joinList(values, ", "));
                return;
            }
            this.arena.getConfig().save();

        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (this.endRunner != null) {
            return;
        }
        if (this.arena.realEndRunner != null) {
            debug(this.arena, "[INFECT] already ending");
            return;
        }
        final PAGoalEndEvent gEvent = new PAGoalEndEvent(this.arena, this);
        Bukkit.getPluginManager().callEvent(gEvent);

        for (ArenaTeam team : this.arena.getTeams()) {
            for (ArenaPlayer arenaPlayer : team.getTeamMembers()) {
                if (arenaPlayer.getStatus() != PlayerStatus.FIGHT) {
                    continue;
                }
                if (INFECTED.equals(arenaPlayer.getArenaTeam().getName())) {
                    ArenaModuleManager.announce(this.arena,
                            Language.parse(MSG.GOAL_INFECTED_WON), "END");

                    ArenaModuleManager.announce(this.arena,
                            Language.parse(MSG.GOAL_INFECTED_WON), "WINNER");

                    this.arena.broadcast(Language.parse(MSG.GOAL_INFECTED_WON));
                    Set<String> winnerNameList = arenaPlayer.getArenaTeam().getTeamMembers().stream()
                            .map(ArenaPlayer::getName)
                            .collect(Collectors.toSet());
                    this.arena.setWinners(winnerNameList);
                    break;
                } else {

                    ArenaModuleManager.announce(this.arena,
                            Language.parse(MSG.GOAL_INFECTED_LOST), "END");
                    ArenaModuleManager.announce(this.arena,
                            Language.parse(MSG.GOAL_INFECTED_LOST), "LOSER");

                    this.arena.broadcast(Language.parse(MSG.GOAL_INFECTED_LOST));
                    Set<String> winnerNameList = this.arena.getTeams().stream()
                            .filter(arenaTeam -> !INFECTED.equals(arenaTeam.getName()))
                            .flatMap(arenaTeam -> arenaTeam.getTeamMembers().stream())
                            .map(ArenaPlayer::getName)
                            .collect(Collectors.toSet());
                    this.arena.setWinners(winnerNameList);
                    break;
                }
            }

            if (ArenaModuleManager.commitEnd(this.arena, team, null)) {
                return;
            }
        }

        this.endRunner = new EndRunnable(this.arena, this.arena.getConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitPlayerDeath(final ArenaPlayer aPlayer, final boolean doesRespawn, PADeathInfo deathInfo) {
        if (!this.getPlayerLifeMap().containsKey(aPlayer)) {
            return;
        }
        Player player = aPlayer.getPlayer();
        int iLives = this.getPlayerLifeMap().get(aPlayer);
        debug(aPlayer, "lives before death: " + iLives);
        if (iLives <= 1 || INFECTED.equals(aPlayer.getArenaTeam().getName())) {
            if (iLives <= 1 && INFECTED.equals(aPlayer.getArenaTeam().getName())) {

                final PAGoalPlayerDeathEvent gEvent = new PAGoalPlayerDeathEvent(this.arena, this, aPlayer, deathInfo, false);
                Bukkit.getPluginManager().callEvent(gEvent);
                // kill, remove!
                this.getPlayerLifeMap().remove(aPlayer);

                debug(aPlayer, "no remaining lives -> LOST");
                aPlayer.handleDeathAndLose(deathInfo);

                return;
            }
            if (iLives <= 1) {
                PAGoalPlayerDeathEvent gEvent = new PAGoalPlayerDeathEvent(this.arena, this, aPlayer, deathInfo, false);
                Bukkit.getPluginManager().callEvent(gEvent);
                // dying player -> infected
                this.getPlayerLifeMap().put(aPlayer, this.arena.getConfig().getInt(CFG.GOAL_INFECTED_ILIVES));
                this.arena.msg(player, MSG.GOAL_INFECTED_YOU);
                this.arena.broadcast(Language.parse(MSG.GOAL_INFECTED_PLAYER, aPlayer.getName()));

                final ArenaTeam oldTeam = aPlayer.getArenaTeam();
                final ArenaTeam respawnTeam = this.arena.getTeam(INFECTED);

                PATeamChangeEvent tcEvent = new PATeamChangeEvent(this.arena, player, oldTeam, respawnTeam);
                Bukkit.getPluginManager().callEvent(tcEvent);
                this.arena.getScoreboard().switchPlayerTeam(player, oldTeam, respawnTeam);

                oldTeam.remove(aPlayer);

                respawnTeam.add(aPlayer);

                final ArenaClass infectedClass = this.arena.getArenaClass("%infected%");
                if (infectedClass != null) {
                    aPlayer.setArenaClass(infectedClass);
                }

                if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                    this.broadcastSimpleDeathMessage(aPlayer, deathInfo);
                }

                aPlayer.setMayDropInventory(true);
                aPlayer.setMayRespawn(true);

                if (this.anyTeamEmpty()) {
                    WorkflowManager.handleEnd(this.arena, false);
                }
                return;
            }
            // dying infected player, has lives remaining
            PAGoalPlayerDeathEvent gEvent = new PAGoalPlayerDeathEvent(this.arena, this,  aPlayer, deathInfo, true);
            Bukkit.getPluginManager().callEvent(gEvent);
            iLives--;
            this.getPlayerLifeMap().put(aPlayer, iLives);

            if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING, aPlayer, deathInfo, iLives);
            }

            aPlayer.setMayDropInventory(true);
            aPlayer.setMayRespawn(true);


            // player died => commit death!
            WorkflowManager.handleEnd(this.arena, false);
        } else {
            iLives--;
            this.getPlayerLifeMap().put(aPlayer, iLives);

            if (this.arena.getConfig().getBoolean(CFG.USES_DEATHMESSAGES)) {
                this.broadcastDeathMessage(MSG.FIGHT_KILLED_BY_REMAINING, aPlayer, deathInfo, iLives);
            }

            aPlayer.setMayDropInventory(true);
            aPlayer.setMayRespawn(true);
        }
    }

    @Override
    public void commitStart() {
        this.parseStart(); // hack the team in before spawning, derp!
        for (ArenaTeam team : this.arena.getTeams()) {
            SpawnManager.distributeTeams(this.arena, team);
        }
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("normal lives: "
                + this.arena.getConfig().getInt(CFG.GOAL_INFECTED_NLIVES) + " || " +
                "infected lives: "
                + this.arena.getConfig().getInt(CFG.GOAL_INFECTED_ILIVES));
    }

    @Override
    public List<String> getGoalCommands() {
        return asList(GETPROTECT, SETPROTECT);
    }

    @Override
    public boolean hasSpawn(final String spawnName, final String spawnTeamName) {
        boolean hasSpawn = super.hasSpawn(spawnName, spawnTeamName);

        return hasSpawn || spawnName.startsWith(INFECTED);
    }

    @Override
    public void initiate(final ArenaPlayer arenaPlayer) {
        this.updateLives(arenaPlayer, this.arena.getConfig().getInt(CFG.GOAL_INFECTED_NLIVES));
    }

    @Override
    public void parseLeave(final ArenaPlayer arenaPlayer) {
        if (arenaPlayer == null) {
            PVPArena.getInstance().getLogger().warning(
                    this.getName() + ": player NULL");
            return;
        }
        this.getPlayerLifeMap().remove(arenaPlayer);
    }

    @Override
    public void parseStart() {
        // we already build the infected team
        if (this.arena.getTeam(INFECTED) != null) {
            this.infectedTeam = this.arena.getTeam(INFECTED);
            return;
        }
        // create the team infected
        this.infectedTeam = new ArenaTeam(INFECTED, DyeColor.PINK.name());
        this.arena.getTeams().add(this.infectedTeam);

        // select starting infected players
        ArenaPlayer infected = null;
        final Random random = new Random();
        for (ArenaTeam team : this.arena.getNotEmptyTeams()) {
            int pos = random.nextInt(team.getTeamMembers().size());
            debug(this.arena, "team " + team.getName() + " random " + pos);
            for (ArenaPlayer arenaPlayer : team.getTeamMembers()) {
                debug(this.arena, arenaPlayer.getPlayer(), "#" + pos + ": " + arenaPlayer);
                this.getPlayerLifeMap().put(arenaPlayer, this.arena.getConfig().getInt(CFG.GOAL_INFECTED_NLIVES));
                if (pos-- == 0) {
                    infected = arenaPlayer;
                    this.getPlayerLifeMap().put(arenaPlayer, this.arena.getConfig().getInt(CFG.GOAL_INFECTED_ILIVES));
                }
            }
        }

        assert infected != null;
        for (ArenaTeam arenaTeam : this.arena.getNotEmptyTeams()) {
            if (arenaTeam.getTeamMembers().contains(infected)) {
                final PATeamChangeEvent tcEvent = new PATeamChangeEvent(this.arena, infected.getPlayer(), arenaTeam, this.infectedTeam);
                Bukkit.getPluginManager().callEvent(tcEvent);
                this.arena.getScoreboard().switchPlayerTeam(infected.getPlayer(), arenaTeam, this.infectedTeam);
                arenaTeam.remove(infected);
            }
        }
        this.infectedTeam.add(infected);

        final ArenaClass infectedClass = this.arena.getArenaClass("%infected%");
        if (infectedClass != null) {
            infected.setArenaClass(infectedClass);
            InventoryManager.clearInventory(infected.getPlayer());
            infectedClass.equip(infected.getPlayer());
            for (ArenaModule mod : this.arena.getMods()) {
                mod.parseRespawn(infected.getPlayer(), this.infectedTeam, DamageCause.CUSTOM,
                        infected.getPlayer());
            }
        }

        this.arena.msg(infected.getPlayer(), MSG.GOAL_INFECTED_YOU, infected.getName());
        this.arena.broadcast(Language.parse(MSG.GOAL_INFECTED_PLAYER, infected.getName()));

        final Set<PASpawn> spawns = new HashSet<>(SpawnManager.getPASpawnsStartingWith(this.arena, INFECTED));

        TeleportManager.teleportPlayerToRandomSpawn(this.arena, infected, spawns);
    }

    @Override
    public void reset(final boolean force) {
        this.endRunner = null;
        this.getPlayerLifeMap().clear();
        this.arena.getTeams().remove(this.arena.getTeam(INFECTED));
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (ArenaPlayer arenaPlayer : this.arena.getFighters()) {
            double score = this.getPlayerLifeMap().getOrDefault(arenaPlayer, 0);
            if (arenaPlayer.getArenaTeam() != null && INFECTED.equals(arenaPlayer.getArenaTeam().getName())) {
                score *= this.arena.getFighters().size();
            }
            if (scores.containsKey(arenaPlayer.getName())) {
                scores.put(arenaPlayer.getName(), scores.get(arenaPlayer.getName()) + score);
            } else {
                scores.put(arenaPlayer.getName(), score);
            }
        }

        return scores;
    }
}
