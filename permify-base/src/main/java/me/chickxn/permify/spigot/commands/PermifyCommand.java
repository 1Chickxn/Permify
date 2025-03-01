package me.chickxn.permify.spigot.commands;

import me.chickxn.permify.data.storage.StorageHandler;
import me.chickxn.permify.data.interfaces.StorageInterface;
import me.chickxn.permify.data.interfaces.PlayerInterface;
import me.chickxn.permify.data.interfaces.GroupInterface;
import me.chickxn.permify.data.models.GroupData;
import me.chickxn.permify.spigot.PermifyBase;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;
import java.util.stream.Collectors;

public class PermifyCommand implements CommandExecutor, TabCompleter {

    private final String PREFIX = "§8▶▷ §bPermify §8| §7";
    private final String PRIMARY = "§b";
    private final String SECONDARY = "§7";
    private final String SUCCESS = "§a";
    private final String ERROR = "§c";
    private final String DEFAULT_GROUP = "default";
    private final String PERMISSION = "permify.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(formatError("Du hast keine Berechtigung, diesen Befehl zu nutzen!"));
            return true;
        }

        StorageInterface storage = StorageHandler.getActiveStorage();
        if (storage == null) {
            sender.sendMessage(formatError("Datenbank nicht verfügbar!"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "player":
                handlePlayerCommand(sender, storage, args);
                break;
            case "group":
                handleGroupCommand(sender, storage, args);
                break;
            case "groups":
                listAllGroups(sender, storage);
                break;
            case "info":
                handleInfoCommand(sender, storage, args);
                break;
            default:
                sendUsage(sender);
        }

        Bukkit.getOnlinePlayers().forEach(onlinePlayers -> {
            PermifyBase.getInstance().getPermissionHandler().updatePlayerPermissions(onlinePlayers);
        });

        return true;
    }

    private void handlePlayerCommand(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 5) {
            sendUsage(player);
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sendError(player, "Spieler nicht gefunden!");
            return;
        }

        PlayerInterface playerData = storage.getPlayerData(target.getUniqueId());
        if (playerData == null) {
            sendError(player, "Daten nicht geladen!");
            return;
        }

        String action = args[2].toLowerCase();
        String dataType = args[3].toLowerCase();
        String value = args[4];

        switch (action) {
            case "add":
                handleAddAction(player, storage, playerData, dataType, value, targetName);
                break;
            case "remove":
                handleRemoveAction(player, storage, playerData, dataType, value, targetName);
                break;
            case "set":
                handleSetAction(player, storage, playerData, dataType, value, targetName);
                break;
            default:
                sendUsage(player);
        }
    }

    private void handleGroupCommand(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 2) {
            sendUsage(player);
            return;
        }

        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "create":
                createGroup(player, storage, args);
                break;
            case "delete":
                deleteGroup(player, storage, args);
                break;
            default:
                if (storage.getGroupByName(args[1]) != null) {
                    handleGroupPermissions(player, storage, args);
                } else {
                    sendError(player, "Ungültiger Befehl!");
                }
        }
    }

    private void handleAddAction(CommandSender player, StorageInterface storage, PlayerInterface playerData,
                                 String dataType, String value, String targetName) {
        if ("permission".equalsIgnoreCase(dataType)) {
            if (playerData.permissions().contains(value)) {
                sendError(player, "Berechtigung " + PRIMARY + value + SECONDARY + " existiert bereits!");
                return;
            }
            storage.addPlayerPermissions(playerData, value);
            sendSuccess(player, PRIMARY + value + SECONDARY + " zu " + PRIMARY + targetName + " hinzugefügt");
        } else {
            sendError(player, "Ungültiger Datentyp!");
        }
    }

    private void handleRemoveAction(CommandSender player, StorageInterface storage, PlayerInterface playerData,
                                    String dataType, String value, String targetName) {
        if ("permission".equalsIgnoreCase(dataType)) {
            if (!playerData.permissions().contains(value)) {
                sendError(player, "Berechtigung nicht vorhanden!");
                return;
            }
            storage.removePlayerPermissions(playerData, value);
            sendSuccess(player, PRIMARY + value + SECONDARY + " von " + PRIMARY + targetName + " entfernt");
        } else {
            sendError(player, "Ungültiger Datentyp!");
        }
    }

    private void handleSetAction(CommandSender player, StorageInterface storage, PlayerInterface playerData,
                                 String dataType, String value, String targetName) {
        if ("group".equalsIgnoreCase(dataType)) {
            GroupInterface group = storage.getGroupByName(value);
            if (group == null) {
                sendError(player, "Gruppe existiert nicht!");
                return;
            }
            storage.setPlayerToGroup(playerData, group);
            sendSuccess(player, PRIMARY + targetName + SECONDARY + " ist jetzt in Gruppe " + PRIMARY + value);
        } else {
            sendError(player, "Ungültiger Datentyp!");
        }
    }

    private void createGroup(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 3) {
            sendMessage(player, "Nutze: " + PRIMARY + "/permify group create <Name>");
            return;
        }

        String groupName = args[2];
        if (storage.getGroupByName(groupName) != null) {
            sendError(player, "Gruppe existiert bereits!");
            return;
        }

        GroupInterface group = new GroupData(
                groupName,
                SECONDARY + "[" + groupName + "]",
                "",
                NamedTextColor.AQUA,
                storage.getAllGroups().size() + 1
        );
        storage.createGroup(group);
        sendSuccess(player, "Gruppe " + PRIMARY + groupName + SECONDARY + " erstellt!");
    }

    private void deleteGroup(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 3) {
            sendMessage(player, "Nutze: " + PRIMARY + "/permify group delete <Name>");
            return;
        }

        String groupName = args[2];
        if (DEFAULT_GROUP.equalsIgnoreCase(groupName)) {
            sendError(player, "Standardgruppe kann nicht gelöscht werden!");
            return;
        }

        GroupInterface group = storage.getGroupByName(groupName);
        if (group == null) {
            sendError(player, "Gruppe nicht gefunden!");
            return;
        }

        storage.removeGroup(group);
        sendSuccess(player, "Gruppe " + PRIMARY + groupName + SECONDARY + " gelöscht!");
    }

    private void handleGroupPermissions(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 5) {
            sendUsage(player);
            return;
        }

        String groupName = args[1];
        GroupInterface group = storage.getGroupByName(groupName);
        if (group == null) {
            sendError(player, "Gruppe nicht gefunden!");
            return;
        }

        String action = args[2].toLowerCase();
        String permission = args[4];

        switch (action) {
            case "add":
                if (group.permissions().contains(permission)) {
                    sendError(player, "Berechtigung " + PRIMARY + permission + SECONDARY + " existiert bereits!");
                    return;
                }
                storage.addGroupPermissions(group, permission);
                sendSuccess(player, "Berechtigung " + PRIMARY + permission + SECONDARY + " hinzugefügt");
                break;
            case "remove":
                if (!group.permissions().contains(permission)) {
                    sendError(player, "Berechtigung nicht vorhanden!");
                    return;
                }
                storage.removeGroupPermissions(group, permission);
                sendSuccess(player, "Berechtigung " + PRIMARY + permission + SECONDARY + " entfernt");
                break;
            default:
                sendUsage(player);
        }
    }

    private void listAllGroups(CommandSender player, StorageInterface storage) {
        List<GroupInterface> groups = storage.getAllGroups();
        player.sendMessage(PREFIX + "Existierende Gruppen (" + groups.size() + ")");
        groups.forEach(g -> player.sendMessage(SECONDARY + "▸ " + PRIMARY + g.groupName()));
    }

    private void handleInfoCommand(CommandSender player, StorageInterface storage, String[] args) {
        if (args.length < 3) {
            sendUsage(player);
            return;
        }

        String type = args[1].toLowerCase();
        String name = args[2];

        if ("player".equals(type)) {
            showPlayerInfo(player, storage, name);
        } else if ("group".equals(type)) {
            showGroupInfo(player, storage, name);
        } else {
            sendUsage(player);
        }
    }

    private void showPlayerInfo(CommandSender player, StorageInterface storage, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sendError(player, "Spieler offline!");
            return;
        }

        PlayerInterface playerData = storage.getPlayerData(target.getUniqueId());
        if (playerData == null) {
            sendError(player, "Daten nicht gefunden!");
            return;
        }

        player.sendMessage(PREFIX + "Spielerinfo: " + PRIMARY + targetName);
        player.sendMessage(PRIMARY + "Berechtigungen:");
        playerData.permissions().forEach(p -> player.sendMessage(SECONDARY + "▸ " + PRIMARY + p));

        GroupInterface group = storage.getPlayerGroup(playerData);
        player.sendMessage(PRIMARY + "\nGruppe: " + (group != null ? PRIMARY + group.groupName() : SECONDARY + "Keine"));
    }

    private void showGroupInfo(CommandSender player, StorageInterface storage, String groupName) {
        GroupInterface group = storage.getGroupByName(groupName);
        if (group == null) {
            sendError(player, "Gruppe nicht gefunden!");
            return;
        }

        player.sendMessage(PREFIX + "Gruppeninfo: " + PRIMARY + groupName);
        player.sendMessage(PRIMARY + "Berechtigungen:");
        group.permissions().forEach(p -> player.sendMessage(SECONDARY + "▸ " + PRIMARY + p));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        StorageInterface storage = StorageHandler.getActiveStorage();

        if (args.length == 1) {
            if (sender.hasPermission("permify.player")) completions.add("player");
            if (sender.hasPermission("permify.group")) completions.add("group");
            if (sender.hasPermission("permify.info")) completions.addAll(Arrays.asList("groups", "info"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "player":
                    if (sender.hasPermission("permify.player")) {
                        Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    }
                    break;
                case "group":
                    if (sender.hasPermission("permify.group")) {
                        completions.addAll(getAllGroupNames(storage));
                        completions.addAll(Arrays.asList("create", "delete"));
                    }
                    break;
                case "info":
                    if (sender.hasPermission("permify.info")) {
                        completions.addAll(Arrays.asList("player", "group"));
                    }
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "player":
                    if (sender.hasPermission("permify.player")) {
                        completions.addAll(Arrays.asList("add", "remove", "set"));
                    }
                    break;
                case "group":
                    if (sender.hasPermission("permify.group")) {
                        if ("delete".equalsIgnoreCase(args[1])) {
                            completions.addAll(getDeletableGroups(storage));
                        } else if (isExistingGroup(storage, args[1])) {
                            completions.addAll(Arrays.asList("add", "remove"));
                        }
                    }
                    break;
                case "info":
                    if (sender.hasPermission("permify.info")) {
                        if ("group".equalsIgnoreCase(args[1])) {
                            completions.addAll(getAllGroupNames(storage));
                        } else if ("player".equalsIgnoreCase(args[1])) {
                            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                        }
                    }
                    break;
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("player") && sender.hasPermission("permify.player")) {
                String action = args[2].toLowerCase();
                if (action.equals("add") || action.equals("remove")) {
                    completions.add("permission");
                } else if (action.equals("set")) {
                    completions.add("group");
                }
            } else if (args[0].equalsIgnoreCase("group") && sender.hasPermission("permify.group") && isGroupAction(args[1])) {
                completions.add("permission");
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("player") && args[3].equalsIgnoreCase("group") && sender.hasPermission("permify.player")) {
                completions.addAll(getAllGroupNames(storage));
            }
        }

        return filterCompletions(completions, args.length > 0 ? args[args.length - 1] : "");
    }

    private List<String> getAllGroupNames(StorageInterface storage) {
        return storage != null ?
                storage.getAllGroups().stream()
                        .map(GroupInterface::groupName)
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private List<String> getDeletableGroups(StorageInterface storage) {
        return storage != null ?
                storage.getAllGroups().stream()
                        .map(GroupInterface::groupName)
                        .filter(name -> !DEFAULT_GROUP.equalsIgnoreCase(name))
                        .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private boolean isGroupAction(String arg) {
        return !"create".equalsIgnoreCase(arg) && !"delete".equalsIgnoreCase(arg);
    }

    private boolean isExistingGroup(StorageInterface storage, String groupName) {
        return storage != null && storage.getGroupByName(groupName) != null;
    }

    private List<String> filterCompletions(List<String> completions, String input) {
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private void sendUsage(CommandSender player) {
        player.sendMessage(PREFIX + "Befehlsübersicht");
        player.sendMessage(PREFIX + PRIMARY + "Spielerverwaltung:");
        sendCmdHelp(player, "player <Spieler> add permission <Recht>", "Fügt Recht hinzu");
        sendCmdHelp(player, "player <Spieler> remove permission <Recht>", "Entfernt Recht");
        sendCmdHelp(player, "player <Spieler> set group <Gruppe>", "Setzt Gruppe");
        player.sendMessage(PREFIX + PRIMARY + "\nGruppenverwaltung:");
        sendCmdHelp(player, "group create <Name>", "Neue Gruppe erstellen");
        sendCmdHelp(player, "group delete <Name>", "Gruppe löschen");
        sendCmdHelp(player, "group <Gruppe> add <Recht>", "Recht hinzufügen");
        sendCmdHelp(player, "group <Gruppe> remove <Recht>", "Recht entfernen");
        player.sendMessage(PREFIX + PRIMARY + "\nInformationen:");
        sendCmdHelp(player, "groups", "Listet alle Gruppen");
        sendCmdHelp(player, "info player <Spieler>", "Zeigt Spielerinfo");
        sendCmdHelp(player, "info group <Gruppe>", "Zeigt Gruppeninfo");
    }

    private void sendCmdHelp(CommandSender player, String cmd, String desc) {
        player.sendMessage(PREFIX + SECONDARY + "▸ " + PRIMARY + "/permify " + cmd);
        player.sendMessage(PREFIX + SECONDARY + "  ┗ " + desc);
    }

    private void sendSuccess(CommandSender player, String text) {
        player.sendMessage(PREFIX + SECONDARY + "[§a✓§7] " + SUCCESS + text);
    }

    private String formatError(String text) {
        return PREFIX + SECONDARY + "[§c!§7] " + ERROR + text;
    }

    private void sendError(CommandSender player, String text) {
        player.sendMessage(formatError(text));
    }

    private void sendMessage(CommandSender player, String text) {
        player.sendMessage(PREFIX + text);
    }
}