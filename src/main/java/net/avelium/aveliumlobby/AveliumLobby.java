package net.avelium.aveliumlobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class AveliumLobby extends JavaPlugin implements Listener {

    private File dataFile;
    private org.bukkit.configuration.file.FileConfiguration playerData;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AveliumLobby запущен!");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "players.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать players.yml");
            }
        }
        playerData = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            playerData.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить players.yml");
        }
    }

    private String msg(String path) {
        String prefix = getConfig().getString("messages.prefix", "");
        String m = getConfig().getString("messages." + path, path);
        return ChatColor.translateAlternateColorCodes('&', prefix + m);
    }

    private String plain(String path) {
        String m = getConfig().getString("messages." + path, path);
        return ChatColor.translateAlternateColorCodes('&', m);
    }

    private Component legacyToComponent(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(
                ChatColor.translateAlternateColorCodes('&', s));
    }

    // ============ ПРОВЕРКА ЛОББИ ============

    public boolean isInLobby(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        String worldName = getConfig().getString("world", "world");
        if (!loc.getWorld().getName().equals(worldName)) return false;

        int minX = getConfig().getInt("region.min-x");
        int minY = getConfig().getInt("region.min-y");
        int minZ = getConfig().getInt("region.min-z");
        int maxX = getConfig().getInt("region.max-x");
        int maxY = getConfig().getInt("region.max-y");
        int maxZ = getConfig().getInt("region.max-z");

        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean isInLobby(Player p) {
        return isInLobby(p.getLocation());
    }

    public Location getLobbySpawn() {
        String worldName = getConfig().getString("world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                getConfig().getDouble("spawn.x"),
                getConfig().getDouble("spawn.y"),
                getConfig().getDouble("spawn.z"),
                (float) getConfig().getDouble("spawn.yaw"),
                (float) getConfig().getDouble("spawn.pitch"));
    }

    public Location getBedwarsLobby() {
        String worldName = getConfig().getString("bedwars-lobby.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w,
                getConfig().getDouble("bedwars-lobby.x"),
                getConfig().getDouble("bedwars-lobby.y"),
                getConfig().getDouble("bedwars-lobby.z"),
                (float) getConfig().getDouble("bedwars-lobby.yaw"),
                (float) getConfig().getDouble("bedwars-lobby.pitch"));
    }

    // ============ СОХРАНЕНИЕ ПОЗИЦИЙ ============

    public void saveSurvivalLocation(Player p) {
        Location l = p.getLocation();
        if (isInLobby(l)) return; // Не сохраняем позицию если в лобби
        String path = "survival." + p.getUniqueId();
        playerData.set(path + ".world", l.getWorld().getName());
        playerData.set(path + ".x", l.getX());
        playerData.set(path + ".y", l.getY());
        playerData.set(path + ".z", l.getZ());
        playerData.set(path + ".yaw", l.getYaw());
        playerData.set(path + ".pitch", l.getPitch());
        saveData();
    }

    public Location getSurvivalLocation(Player p) {
        String path = "survival." + p.getUniqueId();
        if (!playerData.contains(path)) return null;
        String worldName = playerData.getString(path + ".world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w,
                playerData.getDouble(path + ".x"),
                playerData.getDouble(path + ".y"),
                playerData.getDouble(path + ".z"),
                (float) playerData.getDouble(path + ".yaw"),
                (float) playerData.getDouble(path + ".pitch"));
    }

    // ============ ПОДГОТОВКА ИГРОКА В ЛОББИ ============

    public void preparePlayerForLobby(Player p) {
        if (p.isOp()) return; // ОПы играют как хотят

        if (getConfig().getBoolean("settings.clear-inventory", true)) {
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
        }

        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20);
        p.setFireTicks(0);
        p.setGameMode(GameMode.ADVENTURE);

        if (getConfig().getBoolean("settings.give-menu-item", true)) {
            giveMenuItem(p);
        }
    }

    public void giveMenuItem(Player p) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        String name = plain("menu-item-name");
        meta.setDisplayName(name);
        compass.setItemMeta(meta);

        int slot = getConfig().getInt("settings.menu-slot", 4);
        p.getInventory().setItem(slot, compass);
    }

    // ============ EVENTS ============

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Телепортируем в лобби только если игрок не в мире выживания сохранён
        // Точка спавна = лобби по умолчанию
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            // Если игрок не в лобби, оставим его где есть (это авторизация)
            // AveliumGuard сам разберётся
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // Сохраняем последнее место в выживании (если не в лобби)
        if (!isInLobby(p)) {
            saveSurvivalLocation(p);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (isInLobby(p)) {
            preparePlayerForLobby(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        // Если игрок только что зашёл в зону лобби — подготовить его
        boolean wasIn = isInLobby(e.getFrom());
        boolean nowIn = isInLobby(e.getTo());
        if (!wasIn && nowIn) {
            preparePlayerForLobby(p);
        }
        // Автосохранение позиции в выживании (если игрок покидает лобби)
        if (wasIn && !nowIn && !p.isOp()) {
            // Игрок пытается выйти из лобби пешком — вернём обратно
            e.setTo(e.getFrom());
        }
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        if (isInLobby(e.getLocation())) {
            // Разрешаем только не-мобов (например, стойки для брони, лодки)
            if (e.getEntity() instanceof org.bukkit.entity.Monster
                || e.getEntity() instanceof org.bukkit.entity.Animals
                || e.getEntity() instanceof org.bukkit.entity.Ambient
                || e.getEntity() instanceof org.bukkit.entity.WaterMob) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getPlayer().isOp()) return;
        if (isInLobby(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(msg("build-blocked"));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer().isOp()) return;
        if (isInLobby(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(msg("build-blocked"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.isOp()) return;
        if (isInLobby(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (p.isOp()) return;
        if (isInLobby(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (e.getPlayer().isOp()) return;
        if (isInLobby(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        if (e.getPlayer().isOp()) return;
        if (isInLobby(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Клик по меню Avelium
        String menuTitle = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("menu.title", "Меню"));
        if (e.getView().getTitle().equals(menuTitle)) {
            e.setCancelled(true);
            int slot = e.getRawSlot();
            if (slot == getConfig().getInt("menu.bedwars.slot")) {
                p.closeInventory();
                p.sendMessage(msg("bedwars-coming-soon"));
                // Пока не телепортируем в BedWars (режим не готов)
            } else if (slot == getConfig().getInt("menu.survival.slot")) {
                p.closeInventory();
                teleportToSurvival(p);
            }
            return;
        }
        // Запрет менять инвентарь в лобби (кроме ОПов)
        if (p.isOp()) return;
        if (isInLobby(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.COMPASS) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String menuName = ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("messages.menu-item-name", ""));
                if (meta.getDisplayName().equals(menuName)) {
                    e.setCancelled(true);
                    openMenu(p);
                    return;
                }
            }
        }
        // Запрет взаимодействия в лобби
        if (p.isOp()) return;
        if (isInLobby(p)) {
            // Разрешаем только компас
            if (item != null && item.getType() == Material.COMPASS) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.isOp()) return;
        if (!isInLobby(p)) return;

        String cmd = e.getMessage().toLowerCase().split(" ")[0].substring(1);
        // Разрешённые команды в лобби
        List<String> allowed = Arrays.asList("menu", "lobby", "hub", "m",
                "register", "reg", "login", "l");
        if (!allowed.contains(cmd)) {
            e.setCancelled(true);
            p.sendMessage(msg("command-blocked"));
        }
    }

    // ============ МЕНЮ ============

    public void openMenu(Player p) {
        String title = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("menu.title", "Меню"));
        int size = getConfig().getInt("menu.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // BedWars
        inv.setItem(getConfig().getInt("menu.bedwars.slot"),
                createMenuItem("menu.bedwars"));
        // Survival
        inv.setItem(getConfig().getInt("menu.survival.slot"),
                createMenuItem("menu.survival"));
        // Info
        inv.setItem(getConfig().getInt("menu.info.slot"),
                createMenuItem("menu.info"));

        // Заполнить пустые слоты серым стеклом
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        p.openInventory(inv);
    }

    private ItemStack createMenuItem(String path) {
        String matName = getConfig().getString(path + ".material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                getConfig().getString(path + ".name", "")));
        List<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList(path + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ============ ТЕЛЕПОРТ В ВЫЖИВАНИЕ ============

    public void teleportToSurvival(Player p) {
        Location saved = getSurvivalLocation(p);
        if (saved != null) {
            p.sendMessage(msg("teleport-survival"));
            p.teleport(saved);
            p.setGameMode(GameMode.SURVIVAL);
        } else {
            // Первый раз в выживании — телепорт на спавн мира выживания
            String worldName = getConfig().getString("survival-world", "world");
            World w = Bukkit.getWorld(worldName);
            if (w == null) w = Bukkit.getWorlds().get(0);
            p.sendMessage(msg("no-last-location"));
            p.teleport(w.getSpawnLocation());
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    // ============ КОМАНДЫ ============

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Только для игроков!");
                return true;
            }
            openMenu(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("setlobby")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Только для игроков!");
                return true;
            }
            if (!p.hasPermission("aveliumlobby.admin")) {
                p.sendMessage(msg("no-permission"));
                return true;
            }
            Location l = p.getLocation();
            getConfig().set("spawn.x", l.getX());
            getConfig().set("spawn.y", l.getY());
            getConfig().set("spawn.z", l.getZ());
            getConfig().set("spawn.yaw", l.getYaw());
            getConfig().set("spawn.pitch", l.getPitch());
            saveConfig();
            p.sendMessage(msg("spawn-set"));
            return true;
        }
        return false;
    }
}
