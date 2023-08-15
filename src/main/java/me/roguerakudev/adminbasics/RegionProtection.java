package me.roguerakudev.adminbasics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = AdminBasics.MOD_ID)
public class RegionProtection {
    private static final StringTextComponent INTERACTION_DISALLOWED_MSG = new StringTextComponent(
            "§cInteraction disallowed because the target block or entity is in a protected region."
    );
    private static final StringTextComponent INCOMING_DAMAGE_CANCELED_MSG = new StringTextComponent(
            "§aIncoming damage blocked because you are in a protected region."
    );
    private static final StringTextComponent OUTGOING_DAMAGE_CANCELED_MSG = new StringTextComponent(
            "§cYou cannot damage that entity because it is in a protected region."
    );
    private static final StringTextComponent SPAWNING_DISALLOWED_MSG = new StringTextComponent(
            "§cSpawning prevented because the entity would be in a protected region."
    );
    private static final Logger LOGGER = LogManager.getLogger();

    private static Map<UUID, LocalDateTime> timeOfLastMessageTo = new HashMap<>();
    private static void sendMessageWithSpamLimiting(PlayerEntity player, ITextComponent message) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSent = timeOfLastMessageTo.get(player.getUniqueID());
        if (lastSent == null || now.isAfter(lastSent.plusSeconds(5))) {
            timeOfLastMessageTo.put(player.getUniqueID(), now);
            player.sendMessage(message);
        }
    }
    @SubscribeEvent
    public void onPlayerLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        timeOfLastMessageTo.remove(event.getPlayer().getUniqueID());
    }

    public RegionProtection() {

    }

    /* --------- EVENT HOOKS SECTION --------- */
    /* Prevent players from interacting with blocks/entities inside protected regions */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerInteract(final PlayerInteractEvent event) {
        RegionProtectionData protData = RegionProtectionData.getForWorld(event.getWorld());
        if (protData.isPosInProtectedRegion(event.getPos()) && !ABUtils.isOp(event.getPlayer())) {
            event.setCanceled(true);
            sendMessageWithSpamLimiting(event.getPlayer(), INTERACTION_DISALLOWED_MSG);
        }
    }

    /* Block attempts at damaging entities inside protected regions
     * Yes, this does not block damage *by* entities inside the protected region, but non-player entities are barred from
     *   protected regions and players can only PvP by contract, so it's not really worth coding
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageAttempt(LivingHurtEvent event) {
        RegionProtectionData protData = RegionProtectionData.getForWorld(event.getEntity().getEntityWorld());
        if (protData.isPosInProtectedRegion(event.getEntity().getPosition())) {
            event.setCanceled(true);

            if (event.getEntity() instanceof PlayerEntity) {
                sendMessageWithSpamLimiting((PlayerEntity) event.getEntity(), INCOMING_DAMAGE_CANCELED_MSG);
            }
            if (event.getSource().getTrueSource() instanceof PlayerEntity) {
                sendMessageWithSpamLimiting((PlayerEntity) event.getSource().getTrueSource(), OUTGOING_DAMAGE_CANCELED_MSG);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityModifyBlock(BlockEvent event) {
        RegionProtectionData protData = RegionProtectionData.getForWorld(event.getWorld().getWorld());
        if (protData.isPosInProtectedRegion(event.getPos())) {
            Entity source = null;
            if (event instanceof BlockEvent.HarvestDropsEvent) {
                source = ((BlockEvent.HarvestDropsEvent) event).getHarvester();
            }
            else if (event instanceof BlockEvent.BreakEvent) {
                source = ((BlockEvent.BreakEvent) event).getPlayer();
            }
            else if (event instanceof BlockEvent.EntityPlaceEvent) {
                source = ((BlockEvent.EntityPlaceEvent) event).getEntity();
            }
            else if (event instanceof BlockEvent.FarmlandTrampleEvent) {
                source = ((BlockEvent.FarmlandTrampleEvent) event).getEntity();
            }

            if (source != null) { // if source is null, it's probably a command, so let it through
                if (source instanceof PlayerEntity) {
                    PlayerEntity playerSource = (PlayerEntity) source;
                    if (!ABUtils.isOp(playerSource)) {
                        sendMessageWithSpamLimiting(playerSource, INTERACTION_DISALLOWED_MSG);
                        event.setCanceled(true);
                    }
                }
                else {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntitySpawn(LivingSpawnEvent.SpecialSpawn event) {
        // Allow spawning via command and spawning of non-mob entities
        if (!event.getSpawnReason().equals(SpawnReason.COMMAND) && event.getEntity() instanceof MobEntity) {
            RegionProtectionData protData = RegionProtectionData.getForWorld(event.getWorld().getWorld());
            if (protData.isPosInProtectedRegion(event.getEntity().getPosition())) {
                event.setCanceled(true);
                if (event.getSpawner().getSpawnerEntity() instanceof PlayerEntity) {
                    sendMessageWithSpamLimiting((PlayerEntity) event.getSpawner().getSpawnerEntity(), SPAWNING_DISALLOWED_MSG);
                }
            }
        }
    }

    private static byte tickCounter = 0;
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (++tickCounter >= 60) { // 20 tps, so every 3 seconds
            tickCounter = 0;
            ServerLifecycleHooks.getCurrentServer().getWorlds().forEach(world -> {
                RegionProtectionData protData = RegionProtectionData.getForWorld(world);
                for (ProtectedRegion region : protData.getAllRegions()) {
                    world.getLoadedEntitiesWithinAABB(MonsterEntity.class, region.toAABB())
                            .forEach(Entity::remove);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        RegionProtectionData.saveForWorld(event.getWorld().getWorld());
    }

    /* --------- COMMANDS SECTION --------- */
    public static LiteralArgumentBuilder<CommandSource> getCommands() {
        return Commands.literal("pr").requires(sender -> sender.hasPermissionLevel(4))
                .then(Commands.literal("-la")
                        .executes(ctx -> command_sendList(ctx.getSource())))
                .then(Commands.literal("-lcw")
                        .executes(ctx -> command_sendListForCurrentWorld(ctx.getSource())))
                .then(Commands.argument("region-name", StringArgumentType.word())
                        .then(Commands.literal("create")
                                .then(Commands.argument("x1", IntegerArgumentType.integer())
                                .then(Commands.argument("z1", IntegerArgumentType.integer())
                                .then(Commands.argument("x2", IntegerArgumentType.integer())
                                .then(Commands.argument("z2", IntegerArgumentType.integer())
                        .executes(ctx -> command_create(ctx.getSource(),
                                StringArgumentType.getString(ctx, "region-name"),
                                IntegerArgumentType.getInteger(ctx, "x1"),
                                IntegerArgumentType.getInteger(ctx, "z1"),
                                IntegerArgumentType.getInteger(ctx, "x2"),
                                IntegerArgumentType.getInteger(ctx, "z2"))
                        ))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("region-name", StringArgumentType.word())
                        .executes(ctx -> command_delete(ctx.getSource(),
                                StringArgumentType.getString(ctx, "region-name"))
                        )))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("new-name", StringArgumentType.word())
                        .executes(ctx -> command_rename(ctx.getSource(),
                                StringArgumentType.getString(ctx, "region-name"),
                                StringArgumentType.getString(ctx, "new-name"))
                        )))
                        .then(Commands.literal("aer")
                                .then(Commands.argument("edge", StringArgumentType.word())
                                .then(Commands.argument("delta", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            String edgeStr = StringArgumentType.getString(ctx, "edge");
                            Direction edge = getDirectionFromString(edgeStr);
                            if (edge == null) {
                                ctx.getSource().sendErrorMessage(new StringTextComponent("Invalid edge '" + edgeStr + "'. Use north, east, south, west, or auto."));
                                return -4;
                            }
                            return command_alterRelative(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "region-name"),
                                    edge,
                                    IntegerArgumentType.getInteger(ctx, "delta"));
                        }))))
                        .then(Commands.literal("aea")
                                .then(Commands.argument("edge", StringArgumentType.word())
                                .then(Commands.argument("new-value", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            String edgeStr = StringArgumentType.getString(ctx, "edge");
                            Direction edge = getDirectionFromString(edgeStr);
                            if (edge == null) {
                                ctx.getSource().sendErrorMessage(new StringTextComponent("Invalid edge '" + edgeStr + "'. Use north, east, south, west, or auto."));
                                return -4;
                            }
                            return command_alterAbsolute(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "region-name"),
                                    edge,
                                    IntegerArgumentType.getInteger(ctx, "new-value"));
                        }))))
                );
    }

    private static Direction getDirectionFromString(String edgeStr) {
        return
              edgeStr.equals("north") ? Direction.NORTH
            : edgeStr.equals("east")  ? Direction.EAST
            : edgeStr.equals("south") ? Direction.SOUTH
            : edgeStr.equals("west")  ? Direction.WEST
            : edgeStr.equals("auto")  ? Direction.DOWN
            :                           null;
    }
    
    private static int command_sendList(CommandSource source) {
        ServerLifecycleHooks.getCurrentServer().getWorlds().forEach(world ->
                listRegionsForWorld(source, world)
        );

        return 0;
    }

    private static int command_sendListForCurrentWorld(CommandSource source) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }

        listRegionsForWorld(source, source.getWorld());

        return 0;
    }

    private static void listRegionsForWorld(CommandSource sendTo, World world) {
        List<ProtectedRegion> regions = RegionProtectionData.getForWorld(world).getAllRegions();
        sendTo.sendFeedback(new StringTextComponent(regions.size() + " protected region(s) exist(s) in dimension " + world.getDimension().getType().getId()), true);
        for (ProtectedRegion region : regions) {
            sendTo.sendFeedback(new StringTextComponent(region.toString()), true);
        }
    }

    private static int command_create(CommandSource source, String regionName, int x1, int z1, int x2, int z2) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }
        
        ProtectedRegion regionPreNormalization = new ProtectedRegion(regionName, x1, z1, x2, z2);
        ProtectedRegion region = regionPreNormalization.butNormalized();
        if (region != regionPreNormalization) {
            source.sendFeedback(new StringTextComponent("Coordinates corrected to X(" + region.x1 + " -> " + region.x2 + "), Z(" + region.z1 + " -> " + region.z2 + ")"), true);
        }

        RegionProtectionData protData = RegionProtectionData.getForWorld(source.getWorld());
        if (protData.addRegion(region)) {
            source.sendFeedback(new StringTextComponent("Successfully created protected region " + region), true);
        }
        else {
            source.sendErrorMessage(new StringTextComponent("A region named '" + regionName + "' already exists in this dimension"));
            return -2;
        }

        return 0;
    }

    private static int command_delete(CommandSource source, String regionName) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }

        RegionProtectionData protData = RegionProtectionData.getForWorld(source.getWorld());
        if (protData.deleteRegion(regionName)) {
            source.sendFeedback(new StringTextComponent("Successfully deleted protected region " + regionName), true);
        }
        else {
            source.sendErrorMessage(new StringTextComponent("No such region '" + regionName + "' exists in this dimension (" + source.getWorld().getDimension().getType().getId() + ")"));
            return -2;
        }

        return 0;
    }

    private static int command_rename(CommandSource source, String oldName, String newName) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }

        RegionProtectionData protData = RegionProtectionData.getForWorld(source.getWorld());

        ProtectedRegion regionOld = protData.getRegion(oldName);
        if (regionOld == null) {
            source.sendErrorMessage(new StringTextComponent("No such region '" + oldName + "' exists in this dimension (" + source.getWorld().getDimension().getType().getId() + ")"));
            return -2;
        }

        ProtectedRegion regionNew = regionOld.butWithName(newName);
        protData.deleteRegion(oldName);
        protData.addRegion(regionNew);
        source.sendFeedback(new StringTextComponent("Rename complete: " + oldName + " -> " + regionNew), true);

        return 0;
    }
    
    private static int command_alterRelative(CommandSource source, String regionName, Direction edge, int delta) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }

        RegionProtectionData protData = RegionProtectionData.getForWorld(source.getWorld());
        ProtectedRegion regionOld = protData.getRegion(regionName);
        if (regionOld == null) {
            source.sendErrorMessage(new StringTextComponent("No such region '" + regionName + "' exists in this dimension (" + source.getWorld().getDimension().getType().getId() + ")"));
            return -2;
        }

        // Using DOWN here as a signal value for "auto"
        if (edge == Direction.DOWN) {
            PlayerEntity commander = (PlayerEntity) source.getEntity();
            edge = commander.getHorizontalFacing();
        }

        ProtectedRegion regionNewPreNormalization;
        int newValue;
        switch (edge) {
            case NORTH: // -Z
                newValue = regionOld.z1 - delta; // "expand by delta" for the lower bound means subtract, not add, delta
                regionNewPreNormalization = regionOld.butWithZ1(newValue);
                break;
            case EAST: // +X
                newValue = regionOld.x2 + delta;
                regionNewPreNormalization = regionOld.butWithX2(newValue);
                break;
            case SOUTH: // +Z
                newValue = regionOld.z2 + delta;
                regionNewPreNormalization = regionOld.butWithZ2(newValue);
                break;
            case WEST: // -X
                newValue = regionOld.x1 - delta;
                regionNewPreNormalization = regionOld.butWithX1(newValue);
                break;
            default:
                source.sendErrorMessage(new StringTextComponent("Something went very wrong. edge = " + edge.getName()));
                return -3;
        }

        ProtectedRegion regionNew = regionNewPreNormalization.butNormalized();
        if (regionNew != regionNewPreNormalization) {
            source.sendFeedback(new StringTextComponent("Coordinates corrected to X(" + regionNew.x1 + " -> " + regionNew.x2 + "), Z(" + regionNew.z1 + " -> " + regionNew.z2 + ")"), true);
        }

        protData.updateRegion(regionNew);
        source.sendFeedback(new StringTextComponent("Successfully set " + (regionNew == regionNewPreNormalization ? edge.getName() : edge.getOpposite().getName()) + "ern border at "
                + edge.getAxis().getName() + " = " + newValue), true);

        return 0;
    }

    private static int command_alterAbsolute(CommandSource source, String regionName, Direction edge, int newValue) {
        if (source.getEntity() == null) {
            source.sendErrorMessage(new StringTextComponent("This command can only be executed as a player"));
            return -1;
        }

        RegionProtectionData protData = RegionProtectionData.getForWorld(source.getWorld());
        ProtectedRegion regionOld = protData.getRegion(regionName);
        if (regionOld == null) {
            source.sendErrorMessage(new StringTextComponent("No such region '" + regionName + "' exists in this dimension (" + source.getWorld().getDimension().getType().getId() + ")"));
            return -2;
        }

        // Using DOWN here as a signal value for "auto"
        if (edge == Direction.DOWN) {
            PlayerEntity commander = (PlayerEntity) source.getEntity();
            edge = commander.getHorizontalFacing();
        }
        
        ProtectedRegion regionNewPreNormalization;
        switch (edge) {
            case NORTH: // -Z
                regionNewPreNormalization = regionOld.butWithZ1(newValue);
                break;
            case EAST: // +X
                regionNewPreNormalization = regionOld.butWithX2(newValue);
                break;
            case SOUTH: // +Z
                regionNewPreNormalization = regionOld.butWithZ2(newValue);
                break;
            case WEST: // -X
                regionNewPreNormalization = regionOld.butWithX1(newValue);
                break;
            default:
                source.sendErrorMessage(new StringTextComponent("Something went very wrong. edge = " + edge.getName()));
                return -3;
        }
        
        ProtectedRegion regionNew = regionNewPreNormalization.butNormalized();
        if (regionNew != regionNewPreNormalization) {
            source.sendFeedback(new StringTextComponent("Coordinates corrected to X(" + regionNew.x1 + " -> " + regionNew.x2 + "), Z(" + regionNew.z1 + " -> " + regionNew.z2 + ")"), true);
        }
        
        protData.updateRegion(regionNew);
        source.sendFeedback(new StringTextComponent("Successfully set " + (regionNew == regionNewPreNormalization ? edge.getName() : edge.getOpposite().getName()) + "ern border at " 
                + edge.getAxis().getName() + " = " + newValue), true);

        return 0;
    }
}
