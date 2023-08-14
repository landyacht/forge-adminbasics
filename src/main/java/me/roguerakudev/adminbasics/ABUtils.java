package me.roguerakudev.adminbasics;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

class ABUtils {
    static boolean isOp(PlayerEntity player) {
        PlayerList playerList = ServerLifecycleHooks.getCurrentServer().getPlayerList();
        return playerList.canSendCommands(player.getGameProfile())
                && !playerList.commandsAllowedForAll(); // if only there were a public method just for is-op...
    }
}
