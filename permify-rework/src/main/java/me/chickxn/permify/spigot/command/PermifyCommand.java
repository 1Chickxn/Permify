package me.chickxn.permify.spigot.command;

import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.spigot.PermifyBase;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PermifyCommand implements CommandExecutor, TabCompleter {

    private final PermifyBase plugin;

    // Color codes
    private static final String PREFIX = "§8▶▷ §bPermify §8| §7";
    private static final String PRIMARY = "§b";
    private static final String SECONDARY = "§7";
    private static final String SUCCESS = "§a";
    private static final String ERROR = "§c";
    private static final String WARNING = "§e";
    private static final String INFO = "§f";

    // Common permissions
    private static final String PERM_ADMIN = "permify.admin";
    private static final String PERM_PLAYER = "permify.player";
    private static final String PERM_GROUP = "permify.group";
    private static final String PERM_INFO = "permify.info";
    private static final String PERM_RELOAD = "permify.reload";

    public PermifyCommand(@NotNull PermifyBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        // Check if plugin is fully loaded
        if (!plugin.isFullyLoaded()) {
            sendMessage(sender, ERROR + "Permify is still loading, please wait...");
            return true;
        }

        // Check storage availability
        if (StorageHandler.getActiveStorage() == null) {
            sendMessage(sender, ERROR + "Storage system not available!");
            return true;
        }

        // Handle commands
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "user":
            case "player":
                return handleUserCommand(sender, args);
            case "group":
                return handleGroupCommand(sender, args);
            case "info":
                return handleInfoCommand(sender, args);
            case "list":
                return handleListCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "stats":
                return handleStatsCommand(sender);
            case "help":
                sendUsage(sender);
                return true;
            default:
                sendMessage(sender, ERROR + "Unknown subcommand. Use /permify help");
                return true;
        }
    }

    // ===================================================================================================
    // USER COMMANDS
    // ===================================================================================================

    private boolean handleUserCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERM_PLAYER) && !sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        if (args.length < 3) {
            sendUserUsage(sender);
            return true;
        }

        String playerName = args[1];
        String action = args[2].toLowerCase();

        // Get player (online or offline)
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(playerName);
            }

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendMessage(sender, ERROR + "Player '" + playerName + "' has never played on this server!");
                return;
            }

            PlayerInterface playerData = StorageHandler.getActiveStorage().loadPlayer(target.getUniqueId());
            if (playerData == null) {
                sendMessage(sender, ERROR + "Could not load player data!");
                return;
            }

            handleUserAction(sender, playerData, action, Arrays.copyOfRange(args, 3, args.length), target.getName());
        });

        return true;
    }

    private void handleUserAction(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                  @NotNull String action, @NotNull String[] actionArgs, @NotNull String playerName) {
        switch (action) {
            case "addperm":
            case "addpermission":
                handleUserAddPermission(sender, player, actionArgs, playerName);
                break;
            case "removeperm":
            case "removepermission":
                handleUserRemovePermission(sender, player, actionArgs, playerName);
                break;
            case "addgroup":
                handleUserAddGroup(sender, player, actionArgs, playerName);
                break;
            case "removegroup":
                handleUserRemoveGroup(sender, player, actionArgs, playerName);
                break;
            case "setgroup":
                handleUserSetGroup(sender, player, actionArgs, playerName);
                break;
            case "setprefix":
                handleUserSetPrefix(sender, player, actionArgs, playerName);
                break;
            case "setsuffix":
                handleUserSetSuffix(sender, player, actionArgs, playerName);
                break;
            case "setcolor":
                handleUserSetColor(sender, player, actionArgs, playerName);
                break;
            case "addtemp":
                handleUserAddTempPermission(sender, player, actionArgs, playerName);
                break;
            case "clear":
                handleUserClear(sender, player, actionArgs, playerName);
                break;
            default:
                sendUserUsage(sender);
        }
    }

    private void handleUserAddPermission(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                         @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " addperm <permission>");
            return;
        }

        String permission = args[0];

        if (player.getDirectPermissions().contains(permission)) {
            sendMessage(sender, WARNING + "Player already has permission: " + PRIMARY + permission);
            return;
        }

        if (StorageHandler.getActiveStorage().addPlayerPermission(player, permission)) {
            sendMessage(sender, SUCCESS + "Added permission " + PRIMARY + permission + SUCCESS + " to " + PRIMARY + playerName);
            updatePlayerPermissions(playerName);
        } else {
            sendMessage(sender, ERROR + "Failed to add permission!");
        }
    }

    private void handleUserRemovePermission(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                            @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " removeperm <permission>");
            return;
        }

        String permission = args[0];

        if (!player.getDirectPermissions().contains(permission)) {
            sendMessage(sender, WARNING + "Player doesn't have permission: " + PRIMARY + permission);
            return;
        }

        if (StorageHandler.getActiveStorage().removePlayerPermission(player, permission)) {
            sendMessage(sender, SUCCESS + "Removed permission " + PRIMARY + permission + SUCCESS + " from " + PRIMARY + playerName);
            updatePlayerPermissions(playerName);
        } else {
            sendMessage(sender, ERROR + "Failed to remove permission!");
        }
    }

    private void handleUserAddGroup(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                    @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " addgroup <group>");
            return;
        }

        String groupName = args[0];
        GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);

        if (group == null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
            return;
        }

        if (player.hasGroup(group)) {
            sendMessage(sender, WARNING + "Player is already in group: " + PRIMARY + groupName);
            return;
        }

        if (StorageHandler.getActiveStorage().addPlayerToGroup(player, group)) {
            sendMessage(sender, SUCCESS + "Added " + PRIMARY + playerName + SUCCESS + " to group " + PRIMARY + groupName);
            updatePlayerPermissions(playerName);
        } else {
            sendMessage(sender, ERROR + "Failed to add player to group!");
        }
    }

    private void handleUserRemoveGroup(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                       @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " removegroup <group>");
            return;
        }

        String groupName = args[0];
        GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);

        if (group == null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
            return;
        }

        if (!player.hasGroup(group)) {
            sendMessage(sender, WARNING + "Player is not in group: " + PRIMARY + groupName);
            return;
        }

        if (StorageHandler.getActiveStorage().removePlayerFromGroup(player, group)) {
            sendMessage(sender, SUCCESS + "Removed " + PRIMARY + playerName + SUCCESS + " from group " + PRIMARY + groupName);
            updatePlayerPermissions(playerName);
        } else {
            sendMessage(sender, ERROR + "Failed to remove player from group!");
        }
    }

    private void handleUserSetGroup(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                    @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " setgroup <group>");
            return;
        }

        String groupName = args[0];
        GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);

        if (group == null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
            return;
        }

        if (StorageHandler.getActiveStorage().setPlayerPrimaryGroup(player, group)) {
            sendMessage(sender, SUCCESS + "Set " + PRIMARY + playerName + SUCCESS + "'s primary group to " + PRIMARY + groupName);
            updatePlayerPermissions(playerName);
        } else {
            sendMessage(sender, ERROR + "Failed to set primary group!");
        }
    }

    private void handleUserSetPrefix(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                     @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " setprefix <prefix|none>");
            return;
        }

        String prefix = String.join(" ", args);
        if ("none".equalsIgnoreCase(prefix)) {
            prefix = null;
        } else {
            prefix = prefix.replace("&", "§");
        }

        StorageHandler.getActiveStorage().updatePlayerDisplayProperties(player, prefix, null, null);

        if (prefix == null) {
            sendMessage(sender, SUCCESS + "Removed custom prefix for " + PRIMARY + playerName);
        } else {
            sendMessage(sender, SUCCESS + "Set prefix for " + PRIMARY + playerName + SUCCESS + " to: " + prefix);
        }
        updatePlayerPermissions(playerName);
    }

    private void handleUserSetSuffix(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                     @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " setsuffix <suffix|none>");
            return;
        }

        String suffix = String.join(" ", args);
        if ("none".equalsIgnoreCase(suffix)) {
            suffix = null;
        } else {
            suffix = suffix.replace("&", "§");
        }

        StorageHandler.getActiveStorage().updatePlayerDisplayProperties(player, null, suffix, null);

        if (suffix == null) {
            sendMessage(sender, SUCCESS + "Removed custom suffix for " + PRIMARY + playerName);
        } else {
            sendMessage(sender, SUCCESS + "Set suffix for " + PRIMARY + playerName + SUCCESS + " to: " + suffix);
        }
        updatePlayerPermissions(playerName);
    }

    private void handleUserSetColor(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                    @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " setcolor <color|none>");
            return;
        }

        String colorName = args[0].toUpperCase();
        NamedTextColor color = null;

        if (!"NONE".equals(colorName)) {
            try {
                color = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (color == null) {
                    sendMessage(sender, ERROR + "Invalid color! Available colors: " +
                            NamedTextColor.NAMES.keys().stream()
                                    .collect(Collectors.joining(", ")));
                    return;
                }
            } catch (Exception e) {
                sendMessage(sender, ERROR + "Invalid color name!");
                return;
            }
        }

        StorageHandler.getActiveStorage().updatePlayerDisplayProperties(player, null, null, color);

        if (color == null) {
            sendMessage(sender, SUCCESS + "Removed custom color for " + PRIMARY + playerName);
        } else {
            sendMessage(sender, SUCCESS + "Set color for " + PRIMARY + playerName + SUCCESS + " to: " + PRIMARY + colorName);
        }
        updatePlayerPermissions(playerName);
    }

    private void handleUserAddTempPermission(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                             @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 2) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " addtemp <permission> <duration>");
            return;
        }

        String permission = args[0];
        String durationStr = args[1];

        try {
            long duration = parseDuration(durationStr);
            LocalDateTime expiry = LocalDateTime.now().plusSeconds(duration);

            if (StorageHandler.getActiveStorage().addTemporaryPlayerPermission(player, permission, expiry)) {
                sendMessage(sender, SUCCESS + "Added temporary permission " + PRIMARY + permission +
                        SUCCESS + " to " + PRIMARY + playerName + SUCCESS + " for " + PRIMARY + durationStr);
                updatePlayerPermissions(playerName);
            } else {
                sendMessage(sender, ERROR + "Failed to add temporary permission!");
            }
        } catch (IllegalArgumentException e) {
            sendMessage(sender, ERROR + "Invalid duration format! Use: 1d, 2h, 30m, 45s");
        }
    }

    private void handleUserClear(@NotNull CommandSender sender, @NotNull PlayerInterface player,
                                 @NotNull String[] args, @NotNull String playerName) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " clear <permissions|groups|all>");
            return;
        }

        String type = args[0].toLowerCase();
        switch (type) {
            case "permissions":
            case "perms":
                for (String perm : new HashSet<>(player.getDirectPermissions())) {
                    StorageHandler.getActiveStorage().removePlayerPermission(player, perm);
                }
                sendMessage(sender, SUCCESS + "Cleared all permissions for " + PRIMARY + playerName);
                break;
            case "groups":
                for (GroupInterface group : new ArrayList<>(player.getGroups())) {
                    StorageHandler.getActiveStorage().removePlayerFromGroup(player, group);
                }
                sendMessage(sender, SUCCESS + "Cleared all groups for " + PRIMARY + playerName);
                break;
            case "all":
                player.reset();
                sendMessage(sender, SUCCESS + "Cleared all data for " + PRIMARY + playerName);
                break;
            default:
                sendMessage(sender, ERROR + "Usage: /permify user " + playerName + " clear <permissions|groups|all>");
                return;
        }
        updatePlayerPermissions(playerName);
    }

    // ===================================================================================================
    // GROUP COMMANDS
    // ===================================================================================================

    private boolean handleGroupCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERM_GROUP) && !sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        if (args.length < 2) {
            sendGroupUsage(sender);
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create":
                return handleGroupCreate(sender, Arrays.copyOfRange(args, 2, args.length));
            case "delete":
                return handleGroupDelete(sender, Arrays.copyOfRange(args, 2, args.length));
            case "list":
                return handleGroupList(sender);
            default:
                // Assume it's a group name
                String groupName = args[1];
                GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);
                if (group == null) {
                    sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
                    return true;
                }

                if (args.length < 3) {
                    sendGroupManageUsage(sender, groupName);
                    return true;
                }

                String groupAction = args[2].toLowerCase();
                handleGroupManage(sender, group, groupAction, Arrays.copyOfRange(args, 3, args.length));
                return true;
        }
    }

    private boolean handleGroupCreate(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group create <name> [displayName]");
            return true;
        }

        String groupName = args[0];
        String displayName = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;

        if (StorageHandler.getActiveStorage().loadGroup(groupName) != null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' already exists!");
            return true;
        }

        GroupInterface group = StorageHandler.getActiveStorage().createGroup(groupName);
        if (displayName != null) {
            group.setDisplayName(displayName.replace("&", "§"));
            StorageHandler.getActiveStorage().saveGroup(group);
        }

        sendMessage(sender, SUCCESS + "Created group " + PRIMARY + groupName +
                (displayName != null ? SUCCESS + " with display name " + PRIMARY + displayName : ""));
        return true;
    }

    private boolean handleGroupDelete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group delete <name>");
            return true;
        }

        String groupName = args[0];
        GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);

        if (group == null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
            return true;
        }

        if (group.isDefault()) {
            sendMessage(sender, ERROR + "Cannot delete default group!");
            return true;
        }

        if (StorageHandler.getActiveStorage().deleteGroup(group)) {
            sendMessage(sender, SUCCESS + "Deleted group " + PRIMARY + groupName);
            // Update all online players
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPermissionHandler().updateAllPlayerPermissions();
            });
        } else {
            sendMessage(sender, ERROR + "Failed to delete group!");
        }
        return true;
    }

    private boolean handleGroupList(@NotNull CommandSender sender) {
        List<GroupInterface> groups = StorageHandler.getActiveStorage().getAllGroups();
        groups.sort(Comparator.comparingInt(GroupInterface::getPriority).reversed());

        sendMessage(sender, INFO + "Groups (" + groups.size() + "):");
        for (GroupInterface group : groups) {
            String prefix = group.getPrefix() != null ? group.getPrefix() : "";
            String suffix = group.getSuffix() != null ? group.getSuffix() : "";
            String defaultMarker = group.isDefault() ? " " + SUCCESS + "(default)" : "";

            sendMessage(sender, SECONDARY + "▸ " + PRIMARY + group.getName() +
                    SECONDARY + " [Priority: " + group.getPriority() + "]" + defaultMarker);
            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                sendMessage(sender, SECONDARY + "  Display: " + prefix + group.getDisplayName() + suffix);
            }
        }
        return true;
    }

    private void handleGroupManage(@NotNull CommandSender sender, @NotNull GroupInterface group,
                                   @NotNull String action, @NotNull String[] args) {
        switch (action) {
            case "addperm":
            case "addpermission":
                handleGroupAddPermission(sender, group, args);
                break;
            case "removeperm":
            case "removepermission":
                handleGroupRemovePermission(sender, group, args);
                break;
            case "addparent":
                handleGroupAddParent(sender, group, args);
                break;
            case "removeparent":
                handleGroupRemoveParent(sender, group, args);
                break;
            case "setprefix":
                handleGroupSetPrefix(sender, group, args);
                break;
            case "setsuffix":
                handleGroupSetSuffix(sender, group, args);
                break;
            case "setcolor":
                handleGroupSetColor(sender, group, args);
                break;
            case "setpriority":
                handleGroupSetPriority(sender, group, args);
                break;
            case "setdefault":
                handleGroupSetDefault(sender, group, args);
                break;
            default:
                sendGroupManageUsage(sender, group.getName());
        }
    }

    private void handleGroupAddPermission(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " addperm <permission>");
            return;
        }

        String permission = args[0];

        if (group.getPermissions().contains(permission)) {
            sendMessage(sender, WARNING + "Group already has permission: " + PRIMARY + permission);
            return;
        }

        if (StorageHandler.getActiveStorage().addGroupPermission(group, permission)) {
            sendMessage(sender, SUCCESS + "Added permission " + PRIMARY + permission +
                    SUCCESS + " to group " + PRIMARY + group.getName());
            updateAllPlayers();
        } else {
            sendMessage(sender, ERROR + "Failed to add permission!");
        }
    }

    private void handleGroupRemovePermission(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " removeperm <permission>");
            return;
        }

        String permission = args[0];

        if (!group.getPermissions().contains(permission)) {
            sendMessage(sender, WARNING + "Group doesn't have permission: " + PRIMARY + permission);
            return;
        }

        if (StorageHandler.getActiveStorage().removeGroupPermission(group, permission)) {
            sendMessage(sender, SUCCESS + "Removed permission " + PRIMARY + permission +
                    SUCCESS + " from group " + PRIMARY + group.getName());
            updateAllPlayers();
        } else {
            sendMessage(sender, ERROR + "Failed to remove permission!");
        }
    }

    private void handleGroupAddParent(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " addparent <parent_group>");
            return;
        }

        String parentName = args[0];
        GroupInterface parentGroup = StorageHandler.getActiveStorage().loadGroup(parentName);

        if (parentGroup == null) {
            sendMessage(sender, ERROR + "Parent group '" + parentName + "' does not exist!");
            return;
        }

        if (group.inheritsFrom(parentGroup)) {
            sendMessage(sender, WARNING + "Group already inherits from: " + PRIMARY + parentName);
            return;
        }

        if (StorageHandler.getActiveStorage().addGroupParent(group, parentGroup)) {
            sendMessage(sender, SUCCESS + "Added parent " + PRIMARY + parentName +
                    SUCCESS + " to group " + PRIMARY + group.getName());
            updateAllPlayers();
        } else {
            sendMessage(sender, ERROR + "Failed to add parent (would create cycle?)!");
        }
    }

    private void handleGroupRemoveParent(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " removeparent <parent_group>");
            return;
        }

        String parentName = args[0];
        GroupInterface parentGroup = StorageHandler.getActiveStorage().loadGroup(parentName);

        if (parentGroup == null) {
            sendMessage(sender, ERROR + "Parent group '" + parentName + "' does not exist!");
            return;
        }

        if (StorageHandler.getActiveStorage().removeGroupParent(group, parentGroup)) {
            sendMessage(sender, SUCCESS + "Removed parent " + PRIMARY + parentName +
                    SUCCESS + " from group " + PRIMARY + group.getName());
            updateAllPlayers();
        } else {
            sendMessage(sender, ERROR + "Failed to remove parent!");
        }
    }

    private void handleGroupSetPrefix(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " setprefix <prefix|none>");
            return;
        }

        String prefix = String.join(" ", args);
        if ("none".equalsIgnoreCase(prefix)) {
            prefix = null;
        } else {
            prefix = prefix.replace("&", "§");
        }

        StorageHandler.getActiveStorage().updateGroupDisplayProperties(group, null, prefix, null, null, 0);

        if (prefix == null) {
            sendMessage(sender, SUCCESS + "Removed prefix for group " + PRIMARY + group.getName());
        } else {
            sendMessage(sender, SUCCESS + "Set prefix for group " + PRIMARY + group.getName() + SUCCESS + " to: " + prefix);
        }
        updateAllPlayers();
    }

    private void handleGroupSetSuffix(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " setsuffix <suffix|none>");
            return;
        }

        String suffix = String.join(" ", args);
        if ("none".equalsIgnoreCase(suffix)) {
            suffix = null;
        } else {
            suffix = suffix.replace("&", "§");
        }

        StorageHandler.getActiveStorage().updateGroupDisplayProperties(group, null, null, suffix, null, 0);

        if (suffix == null) {
            sendMessage(sender, SUCCESS + "Removed suffix for group " + PRIMARY + group.getName());
        } else {
            sendMessage(sender, SUCCESS + "Set suffix for group " + PRIMARY + group.getName() + SUCCESS + " to: " + suffix);
        }
        updateAllPlayers();
    }

    private void handleGroupSetColor(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " setcolor <color|none>");
            return;
        }

        String colorName = args[0].toUpperCase();
        NamedTextColor color = null;

        if (!"NONE".equals(colorName)) {
            try {
                color = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (color == null) {
                    sendMessage(sender, ERROR + "Invalid color! Available: " +
                            NamedTextColor.NAMES.keys().stream().collect(Collectors.joining(", ")));
                    return;
                }
            } catch (Exception e) {
                sendMessage(sender, ERROR + "Invalid color name!");
                return;
            }
        }

        StorageHandler.getActiveStorage().updateGroupDisplayProperties(group, null, null, null, color, 0);

        if (color == null) {
            sendMessage(sender, SUCCESS + "Removed color for group " + PRIMARY + group.getName());
        } else {
            sendMessage(sender, SUCCESS + "Set color for group " + PRIMARY + group.getName() + SUCCESS + " to: " + PRIMARY + colorName);
        }
        updateAllPlayers();
    }

    private void handleGroupSetPriority(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " setpriority <number>");
            return;
        }

        try {
            int priority = Integer.parseInt(args[0]);
            StorageHandler.getActiveStorage().updateGroupDisplayProperties(group, null, null, null, null, priority);
            sendMessage(sender, SUCCESS + "Set priority for group " + PRIMARY + group.getName() + SUCCESS + " to: " + PRIMARY + priority);
            updateAllPlayers();
        } catch (NumberFormatException e) {
            sendMessage(sender, ERROR + "Invalid number: " + args[0]);
        }
    }

    private void handleGroupSetDefault(@NotNull CommandSender sender, @NotNull GroupInterface group, @NotNull String[] args) {
        if (args.length < 1) {
            sendMessage(sender, ERROR + "Usage: /permify group " + group.getName() + " setdefault <true|false>");
            return;
        }

        boolean isDefault = Boolean.parseBoolean(args[0]);
        group.setDefault(isDefault);
        StorageHandler.getActiveStorage().saveGroup(group);

        sendMessage(sender, SUCCESS + "Set default status for group " + PRIMARY + group.getName() +
                SUCCESS + " to: " + PRIMARY + isDefault);
    }

    // ===================================================================================================
    // INFO COMMANDS
    // ===================================================================================================

    private boolean handleInfoCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERM_INFO) && !sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        if (args.length < 3) {
            sendMessage(sender, ERROR + "Usage: /permify info <user|group> <name>");
            return true;
        }

        String type = args[1].toLowerCase();
        String name = args[2];

        switch (type) {
            case "user":
            case "player":
                showUserInfo(sender, name);
                break;
            case "group":
                showGroupInfo(sender, name);
                break;
            default:
                sendMessage(sender, ERROR + "Usage: /permify info <user|group> <name>");
        }
        return true;
    }

    private void showUserInfo(@NotNull CommandSender sender, @NotNull String playerName) {
        CompletableFuture.runAsync(() -> {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
            if (target == null) {
                target = Bukkit.getOfflinePlayer(playerName);
            }

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sendMessage(sender, ERROR + "Player '" + playerName + "' has never played on this server!");
                return;
            }

            PlayerInterface player = StorageHandler.getActiveStorage().loadPlayer(target.getUniqueId());
            if (player == null) {
                sendMessage(sender, ERROR + "Could not load player data!");
                return;
            }

            // Basic info
            sendMessage(sender, INFO + "=== Player Info: " + PRIMARY + player.getName() + INFO + " ===");
            sendMessage(sender, SECONDARY + "UUID: " + INFO + player.getUuid());
            sendMessage(sender, SECONDARY + "Online: " + (player.isOnline() ? SUCCESS + "Yes" : ERROR + "No"));

            // Display properties
            String prefix = player.getPrefix();
            String suffix = player.getSuffix();
            NamedTextColor color = player.getColor();

            if (prefix != null) sendMessage(sender, SECONDARY + "Prefix: " + prefix);
            if (suffix != null) sendMessage(sender, SECONDARY + "Suffix: " + suffix);
            if (color != null) sendMessage(sender, SECONDARY + "Color: " + PRIMARY + color.toString());

            // Groups
            List<GroupInterface> groups = player.getGroups();
            sendMessage(sender, SECONDARY + "Groups (" + groups.size() + "):");
            for (GroupInterface group : groups) {
                String marker = group.equals(player.getPrimaryGroup().orElse(null)) ? SUCCESS + " (primary)" : "";
                sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + group.getName() + marker);
            }

            // Direct permissions
            Set<String> directPerms = player.getDirectPermissions();
            sendMessage(sender, SECONDARY + "Direct Permissions (" + directPerms.size() + "):");
            directPerms.stream().sorted().forEach(perm ->
                    sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + perm));

            // Temporary permissions
            Map<String, LocalDateTime> tempPerms = player.getTemporaryPermissions();
            if (!tempPerms.isEmpty()) {
                sendMessage(sender, SECONDARY + "Temporary Permissions (" + tempPerms.size() + "):");
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                tempPerms.forEach((perm, expiry) -> {
                    String timeLeft = expiry.isAfter(LocalDateTime.now()) ?
                            SUCCESS + expiry.format(formatter) : ERROR + "EXPIRED";
                    sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + perm + SECONDARY + " (expires: " + timeLeft + SECONDARY + ")");
                });
            }

            // Metadata
            if (player.getFirstJoin() != null) {
                sendMessage(sender, SECONDARY + "First Join: " + INFO + player.getFirstJoin().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            if (player.getLastSeen() != null) {
                sendMessage(sender, SECONDARY + "Last Seen: " + INFO + player.getLastSeen().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            sendMessage(sender, SECONDARY + "Playtime: " + INFO + formatPlaytime(player.getPlaytimeSeconds()));
        });
    }

    private void showGroupInfo(@NotNull CommandSender sender, @NotNull String groupName) {
        GroupInterface group = StorageHandler.getActiveStorage().loadGroup(groupName);
        if (group == null) {
            sendMessage(sender, ERROR + "Group '" + groupName + "' does not exist!");
            return;
        }

        sendMessage(sender, INFO + "=== Group Info: " + PRIMARY + group.getName() + INFO + " ===");
        sendMessage(sender, SECONDARY + "Display Name: " + INFO + group.getDisplayName());
        sendMessage(sender, SECONDARY + "Priority: " + INFO + group.getPriority());
        sendMessage(sender, SECONDARY + "Default: " + (group.isDefault() ? SUCCESS + "Yes" : ERROR + "No"));

        if (group.getPrefix() != null) {
            sendMessage(sender, SECONDARY + "Prefix: " + group.getPrefix());
        }
        if (group.getSuffix() != null) {
            sendMessage(sender, SECONDARY + "Suffix: " + group.getSuffix());
        }
        if (group.getColor() != null) {
            sendMessage(sender, SECONDARY + "Color: " + PRIMARY + group.getColor().toString());
        }
        if (group.getDescription() != null) {
            sendMessage(sender, SECONDARY + "Description: " + INFO + group.getDescription());
        }

        // Parent groups
        List<GroupInterface> parents = group.getParentGroups();
        if (!parents.isEmpty()) {
            sendMessage(sender, SECONDARY + "Parent Groups (" + parents.size() + "):");
            parents.forEach(parent -> sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + parent.getName()));
        }

        // Child groups
        List<GroupInterface> children = group.getChildGroups();
        if (!children.isEmpty()) {
            sendMessage(sender, SECONDARY + "Child Groups (" + children.size() + "):");
            children.forEach(child -> sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + child.getName()));
        }

        // Direct permissions
        Set<String> directPerms = group.getPermissions();
        sendMessage(sender, SECONDARY + "Direct Permissions (" + directPerms.size() + "):");
        directPerms.stream().sorted().forEach(perm ->
                sendMessage(sender, SECONDARY + "  ▸ " + PRIMARY + perm));

        // All permissions (including inherited)
        Set<String> allPerms = group.getAllPermissions();
        if (allPerms.size() > directPerms.size()) {
            sendMessage(sender, SECONDARY + "Total Permissions (including inherited): " + INFO + allPerms.size());
        }

        // Metadata
        if (group.getCreatedAt() != null) {
            sendMessage(sender, SECONDARY + "Created: " + INFO + group.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (group.getLastModified() != null) {
            sendMessage(sender, SECONDARY + "Last Modified: " + INFO + group.getLastModified().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    // ===================================================================================================
    // OTHER COMMANDS
    // ===================================================================================================

    private boolean handleListCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(PERM_INFO) && !sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        if (args.length < 2) {
            sendMessage(sender, ERROR + "Usage: /permify list <groups|users>");
            return true;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "groups":
                return handleGroupList(sender);
            case "users":
            case "players":
                showOnlineUsers(sender);
                return true;
            default:
                sendMessage(sender, ERROR + "Usage: /permify list <groups|users>");
                return true;
        }
    }

    private void showOnlineUsers(@NotNull CommandSender sender) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        sendMessage(sender, INFO + "Online Players (" + players.size() + "):");

        for (Player player : players) {
            PlayerInterface playerData = StorageHandler.getActiveStorage().loadPlayer(player.getUniqueId());
            if (playerData != null) {
                Optional<GroupInterface> primaryGroup = playerData.getPrimaryGroup();
                String groupName = primaryGroup.map(GroupInterface::getName).orElse("none");
                sendMessage(sender, SECONDARY + "▸ " + PRIMARY + player.getName() +
                        SECONDARY + " [" + groupName + "]");
            } else {
                sendMessage(sender, SECONDARY + "▸ " + PRIMARY + player.getName() + ERROR + " [no data]");
            }
        }
    }

    private boolean handleReloadCommand(@NotNull CommandSender sender) {
        if (!sender.hasPermission(PERM_RELOAD) && !sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        sendMessage(sender, INFO + "Reloading Permify...");

        plugin.reloadPlugin().thenAccept(success -> {
            if (success) {
                sendMessage(sender, SUCCESS + "Permify reloaded successfully!");
            } else {
                sendMessage(sender, ERROR + "Failed to reload Permify!");
            }
        });

        return true;
    }

    private boolean handleStatsCommand(@NotNull CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, ERROR + "No permission!");
            return true;
        }

        sendMessage(sender, INFO + "=== Permify Statistics ===");
        sendMessage(sender, plugin.getPluginStats());
        return true;
    }

    // ===================================================================================================
    // TAB COMPLETION
    // ===================================================================================================

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (!plugin.isFullyLoaded() || StorageHandler.getActiveStorage() == null) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        try {
            if (args.length == 1) {
                // Main subcommands
                if (sender.hasPermission(PERM_PLAYER) || sender.hasPermission(PERM_ADMIN)) {
                    completions.addAll(Arrays.asList("user", "player"));
                }
                if (sender.hasPermission(PERM_GROUP) || sender.hasPermission(PERM_ADMIN)) {
                    completions.add("group");
                }
                if (sender.hasPermission(PERM_INFO) || sender.hasPermission(PERM_ADMIN)) {
                    completions.addAll(Arrays.asList("info", "list"));
                }
                if (sender.hasPermission(PERM_RELOAD) || sender.hasPermission(PERM_ADMIN)) {
                    completions.add("reload");
                }
                if (sender.hasPermission(PERM_ADMIN)) {
                    completions.add("stats");
                }
                completions.add("help");

            } else if (args.length == 2) {
                String subCmd = args[0].toLowerCase();

                switch (subCmd) {
                    case "user":
                    case "player":
                        if (sender.hasPermission(PERM_PLAYER) || sender.hasPermission(PERM_ADMIN)) {
                            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                            // Also add offline players that have data
                            // Note: This could be expensive for large databases
                        }
                        break;
                    case "group":
                        if (sender.hasPermission(PERM_GROUP) || sender.hasPermission(PERM_ADMIN)) {
                            completions.addAll(Arrays.asList("create", "delete", "list"));
                            StorageHandler.getActiveStorage().getAllGroups()
                                    .forEach(g -> completions.add(g.getName()));
                        }
                        break;
                    case "info":
                        if (sender.hasPermission(PERM_INFO) || sender.hasPermission(PERM_ADMIN)) {
                            completions.addAll(Arrays.asList("user", "player", "group"));
                        }
                        break;
                    case "list":
                        if (sender.hasPermission(PERM_INFO) || sender.hasPermission(PERM_ADMIN)) {
                            completions.addAll(Arrays.asList("groups", "users", "players"));
                        }
                        break;
                }

            } else if (args.length == 3) {
                String subCmd = args[0].toLowerCase();
                String target = args[1];

                switch (subCmd) {
                    case "user":
                    case "player":
                        if (sender.hasPermission(PERM_PLAYER) || sender.hasPermission(PERM_ADMIN)) {
                            completions.addAll(Arrays.asList(
                                    "addperm", "removeperm", "addgroup", "removegroup", "setgroup",
                                    "setprefix", "setsuffix", "setcolor", "addtemp", "clear"
                            ));
                        }
                        break;
                    case "group":
                        if (sender.hasPermission(PERM_GROUP) || sender.hasPermission(PERM_ADMIN)) {
                            if ("create".equals(target) || "delete".equals(target)) {
                                // Group name for create/delete
                                if ("delete".equals(target)) {
                                    StorageHandler.getActiveStorage().getAllGroups().stream()
                                            .filter(g -> !g.isDefault())
                                            .forEach(g -> completions.add(g.getName()));
                                }
                            } else {
                                // Group management actions
                                completions.addAll(Arrays.asList(
                                        "addperm", "removeperm", "addparent", "removeparent",
                                        "setprefix", "setsuffix", "setcolor", "setpriority", "setdefault"
                                ));
                            }
                        }
                        break;
                    case "info":
                        if (sender.hasPermission(PERM_INFO) || sender.hasPermission(PERM_ADMIN)) {
                            if ("user".equals(target) || "player".equals(target)) {
                                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                            } else if ("group".equals(target)) {
                                StorageHandler.getActiveStorage().getAllGroups()
                                        .forEach(g -> completions.add(g.getName()));
                            }
                        }
                        break;
                }

            } else if (args.length == 4) {
                String subCmd = args[0].toLowerCase();
                String action = args[2].toLowerCase();

                if (("user".equals(subCmd) || "player".equals(subCmd)) &&
                        (sender.hasPermission(PERM_PLAYER) || sender.hasPermission(PERM_ADMIN))) {

                    switch (action) {
                        case "setgroup":
                        case "addgroup":
                        case "removegroup":
                            StorageHandler.getActiveStorage().getAllGroups()
                                    .forEach(g -> completions.add(g.getName()));
                            break;
                        case "setcolor":
                            NamedTextColor.NAMES.keys().forEach(completions::add);
                            completions.add("none");
                            break;
                        case "setdefault":
                            completions.addAll(Arrays.asList("true", "false"));
                            break;
                        case "clear":
                            completions.addAll(Arrays.asList("permissions", "groups", "all"));
                            break;
                    }

                } else if ("group".equals(subCmd) &&
                        (sender.hasPermission(PERM_GROUP) || sender.hasPermission(PERM_ADMIN))) {

                    switch (action) {
                        case "addparent":
                        case "removeparent":
                            StorageHandler.getActiveStorage().getAllGroups()
                                    .forEach(g -> completions.add(g.getName()));
                            break;
                        case "setcolor":
                            NamedTextColor.NAMES.keys().forEach(completions::add);
                            completions.add("none");
                            break;
                        case "setdefault":
                            completions.addAll(Arrays.asList("true", "false"));
                            break;
                    }
                }
            }

        } catch (Exception e) {
            // Ignore tab completion errors
        }

        // Filter and sort completions
        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .sorted()
                .collect(Collectors.toList());
    }

    // ===================================================================================================
    // UTILITY METHODS
    // ===================================================================================================

    private void updatePlayerPermissions(@NotNull String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getPermissionHandler().updatePlayerPermissions(player);
            });
        }
    }

    private void updateAllPlayers() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getPermissionHandler().updateAllPlayerPermissions();
        });
    }

    private void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        sender.sendMessage(PREFIX + message);
    }

    private long parseDuration(@NotNull String duration) throws IllegalArgumentException {
        if (duration.isEmpty()) throw new IllegalArgumentException("Empty duration");

        char unit = duration.charAt(duration.length() - 1);
        String numberPart = duration.substring(0, duration.length() - 1);

        try {
            long value = Long.parseLong(numberPart);
            switch (unit) {
                case 's': return value;
                case 'm': return value * 60;
                case 'h': return value * 3600;
                case 'd': return value * 86400;
                default: throw new IllegalArgumentException("Invalid time unit: " + unit);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + numberPart);
        }
    }

    private String formatPlaytime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m " + (seconds % 60) + "s";
        }
    }

    // ===================================================================================================
    // USAGE MESSAGES
    // ===================================================================================================

    private void sendUsage(@NotNull CommandSender sender) {
        sendMessage(sender, INFO + "=== Permify Commands ===");

        if (sender.hasPermission(PERM_PLAYER) || sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, PRIMARY + "User Management:");
            sendMessage(sender, SECONDARY + "▸ /permify user <player> <action> [args...]");
        }

        if (sender.hasPermission(PERM_GROUP) || sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, PRIMARY + "Group Management:");
            sendMessage(sender, SECONDARY + "▸ /permify group <create|delete|list>");
            sendMessage(sender, SECONDARY + "▸ /permify group <name> <action> [args...]");
        }

        if (sender.hasPermission(PERM_INFO) || sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, PRIMARY + "Information:");
            sendMessage(sender, SECONDARY + "▸ /permify info <user|group> <name>");
            sendMessage(sender, SECONDARY + "▸ /permify list <groups|users>");
        }

        if (sender.hasPermission(PERM_RELOAD) || sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, PRIMARY + "System:");
            sendMessage(sender, SECONDARY + "▸ /permify reload");
        }

        if (sender.hasPermission(PERM_ADMIN)) {
            sendMessage(sender, SECONDARY + "▸ /permify stats");
        }

        sendMessage(sender, SECONDARY + "Use /permify help for detailed usage");
    }

    private void sendUserUsage(@NotNull CommandSender sender) {
        sendMessage(sender, INFO + "User Management Commands:");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> addperm <permission>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> removeperm <permission>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> addgroup <group>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> removegroup <group>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> setgroup <group>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> setprefix <prefix|none>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> setsuffix <suffix|none>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> setcolor <color|none>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> addtemp <permission> <duration>");
        sendMessage(sender, SECONDARY + "▸ /permify user <player> clear <permissions|groups|all>");
    }

    private void sendGroupUsage(@NotNull CommandSender sender) {
        sendMessage(sender, INFO + "Group Management Commands:");
        sendMessage(sender, SECONDARY + "▸ /permify group create <name> [displayName]");
        sendMessage(sender, SECONDARY + "▸ /permify group delete <name>");
        sendMessage(sender, SECONDARY + "▸ /permify group list");
        sendMessage(sender, SECONDARY + "▸ /permify group <name> <action> [args...]");
    }

    private void sendGroupManageUsage(@NotNull CommandSender sender, @NotNull String groupName) {
        sendMessage(sender, INFO + "Group '" + groupName + "' Management:");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " addperm <permission>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " removeperm <permission>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " addparent <group>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " removeparent <group>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " setprefix <prefix|none>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " setsuffix <suffix|none>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " setcolor <color|none>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " setpriority <number>");
        sendMessage(sender, SECONDARY + "▸ /permify group " + groupName + " setdefault <true|false>");
    }
}