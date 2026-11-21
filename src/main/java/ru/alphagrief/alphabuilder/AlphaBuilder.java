package ru.alphagrief.alphabuilder;

import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;

public final class AlphaBuilder extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Location> previews = new HashMap<>();
    private final Map<UUID, String> pendingBuilds = new HashMap<>();
    private HttpClient http;

    @Override public void onEnable() {
        saveDefaultConfig();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
        Objects.requireNonNull(getCommand("ab")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        initDatabase();
        if (getConfig().getBoolean("world.auto-create", true))
            getServer().getScheduler().runTask(this, this::ensureWorld);
        getLogger().info("AlphaBuilder enabled.");
    }

    @Override public void onDisable() {
        for (UUID id : new ArrayList<>(previews.keySet())) clearPreview(Bukkit.getPlayer(id));
    }

    private void initDatabase() {
        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder().toPath().resolve("alphabuilder.db"));
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS player_stats (uuid TEXT PRIMARY KEY, wins INTEGER DEFAULT 0, parkour_best BIGINT DEFAULT 0)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");
            }
        } catch (Exception ex) { getLogger().warning("SQLite init failed: " + ex.getMessage()); }
    }

    private World ensureWorld() {
        String name = getConfig().getString("world.name", "alphabuilder");
        World world = Bukkit.getWorld(name);
        if (world == null) world = Bukkit.createWorld(new WorldCreator(name).environment(World.Environment.NORMAL));
        if (world != null) world.setSpawnLocation(getConfig().getInt("world.spawn-x", 0),
                getConfig().getInt("world.spawn-y", 80), getConfig().getInt("world.spawn-z", 0));
        return world;
    }

    private String prefix() {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "&8[&6AlphaBuilder&8] "));
    }
    private void msg(Player p, String s) { p.sendMessage(prefix() + ChatColor.translateAlternateColorCodes('&', s)); }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name)), NamedTextColor.GOLD));
        if (lore.length > 0) meta.lore(Arrays.stream(lore)
                .map(x -> Component.text(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', x)), NamedTextColor.GRAY)).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private void openMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("AlphaBuilder", NamedTextColor.GOLD));
        inv.setItem(10, item(Material.BRICKS, "&6Постройки", "&7Каталог готовых шаблонов"));
        inv.setItem(11, item(Material.IRON_SWORD, "&cPvP", "&7Арены и дуэли"));
        inv.setItem(12, item(Material.SNOW_BLOCK, "&bSpleef", "&7Снежные арены"));
        inv.setItem(13, item(Material.FEATHER, "&aПаркур", "&7Маршруты и рекорды"));
        inv.setItem(14, item(Material.GOLD_INGOT, "&eТурниры", "&7Соревнования"));
        inv.setItem(15, item(Material.EMERALD, "&dAlpha AI", "&7Идеи: что построить?"));
        inv.setItem(16, item(Material.COMPASS, "&3Мир строителя", "&7Перейти в отдельный мир"));
        if (p.hasPermission("alphabuilder.admin")) inv.setItem(22, item(Material.COMMAND_BLOCK, "&4Админ-панель", "&7Управление режимами"));
        p.openInventory(inv);
    }

    private void openBuildMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("AlphaBuilder • Постройки", NamedTextColor.GOLD));
        inv.setItem(11, item(Material.OAK_PLANKS, "&6Современный дом", "&7Предпросмотр и установка"));
        inv.setItem(13, item(Material.STONE_BRICKS, "&6Башня", "&7Предпросмотр и установка"));
        inv.setItem(15, item(Material.WATER_BUCKET, "&6Фонтан", "&7Предпросмотр и установка"));
        inv.setItem(22, item(Material.ARROW, "&7Назад"));
        p.openInventory(inv);
    }

    private void openSection(Player p, String section) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("AlphaBuilder • " + section, NamedTextColor.GOLD));
        Material icon = switch (section) {
            case "PvP" -> Material.IRON_SWORD;
            case "Spleef" -> Material.SNOW_BLOCK;
            case "Паркур" -> Material.FEATHER;
            default -> Material.GOLD_INGOT;
        };
        inv.setItem(13, item(icon, "&e" + section + " • скоро", "&7Раздел подготовлен", "&7Здесь появится управление режимом"));
        inv.setItem(22, item(Material.ARROW, "&7Назад"));
        p.openInventory(inv);
    }

    private void openAdmin(Player p) {
        if (!p.hasPermission("alphabuilder.admin")) { msg(p, "&cНет прав."); return; }
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("AlphaBuilder • Админ", NamedTextColor.RED));
        inv.setItem(10, item(Material.GRASS_BLOCK, "&aСоздать/загрузить мир"));
        inv.setItem(12, item(Material.IRON_SWORD, "&cУправление PvP"));
        inv.setItem(13, item(Material.SNOW_BLOCK, "&bУправление Spleef"));
        inv.setItem(14, item(Material.FEATHER, "&aУправление паркуром"));
        inv.setItem(16, item(Material.GOLD_INGOT, "&eУправление турнирами"));
        inv.setItem(22, item(Material.ARROW, "&7Назад"));
        p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!title.startsWith("AlphaBuilder")) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().displayName() == null) return;
        String name = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        p.closeInventory();
        if (title.equals("AlphaBuilder")) {
            switch (name) {
                case "Постройки" -> openBuildMenu(p);
                case "PvP" -> openSection(p, "PvP");
                case "Spleef" -> openSection(p, "Spleef");
                case "Паркур" -> openSection(p, "Паркур");
                case "Турниры" -> openSection(p, "Турниры");
                case "Alpha AI" -> msg(p, "&dНапиши: &f/ab ai <что построить?>");
                case "Мир строителя" -> teleportBuilderWorld(p);
                case "Админ-панель" -> openAdmin(p);
            }
        } else if (title.contains("Постройки")) {
            if (name.equals("Назад")) { openMenu(p); return; }
            if (!p.hasPermission("alphabuilder.build")) { msg(p, "&cНет прав на строительство."); return; }
            World world = ensureWorld();
            if (world == null) { msg(p, "&cНе удалось создать мир строителя."); return; }
            if (p.getWorld() != world) p.teleport(new Location(world,
                    world.getSpawnLocation().getX() + .5, world.getSpawnLocation().getY(), world.getSpawnLocation().getZ() + .5));
            String type = name.equals("Современный дом") ? "house" : name.equals("Башня") ? "tower" : "fountain";
            pendingBuilds.put(p.getUniqueId(), type);
            showPreview(p, p.getLocation().getBlock().getLocation().add(3, 0, 0));
            msg(p, "&eПредпросмотр в мире строителя. &a/ab confirm &e— установить, &c/ab cancel &e— отменить.");
        } else if (name.equals("Назад")) openMenu(p);
        else if (title.contains("Админ")) {
            if (name.contains("мир")) teleportBuilderWorld(p);
            else msg(p, "&eРаздел " + name + " пока подготовлен как заготовка.");
        }
    }

    private void teleportBuilderWorld(Player p) {
        World world = ensureWorld();
        if (world == null) { msg(p, "&cНе удалось создать мир."); return; }
        p.teleport(new Location(world, world.getSpawnLocation().getX() + .5,
                world.getSpawnLocation().getY(), world.getSpawnLocation().getZ() + .5));
        msg(p, "&aТы в отдельном мире AlphaBuilder.");
    }

    private void showPreview(Player p, Location loc) {
        clearPreview(p);
        previews.put(p.getUniqueId(), loc);
        if (loc.getWorld() == null) return;
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
            p.sendBlockChange(loc.clone().add(x, 0, z), Bukkit.createBlockData(Material.LIGHT_BLUE_STAINED_GLASS));
    }

    private void clearPreview(Player p) {
        if (p == null) return;
        Location loc = previews.remove(p.getUniqueId());
        if (loc == null || loc.getWorld() == null) return;
        for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            Block block = loc.clone().add(x, 0, z).getBlock();
            p.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    private void build(Player p, String type, Location base) {
        World world = base.getWorld();
        if (world == null) return;
        int x = base.getBlockX(), y = base.getBlockY(), z = base.getBlockZ();
        Material wall = type.equals("house") ? Material.WHITE_CONCRETE : Material.STONE_BRICKS;
        if (type.equals("house")) {
            for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.SPRUCE_PLANKS);
                for (int dy = 1; dy <= 4; dy++) {
                    boolean edge = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                    if (edge) world.getBlockAt(x + dx, y + dy, z + dz).setType(
                            (dy == 2 && (dx == 0 || dx == 6) && dz == 3) ? Material.GLASS : wall);
                }
            }
            for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++)
                if (dx + dz >= 5 && dx + dz <= 7) world.getBlockAt(x + dx, y + 5, z + dz).setType(Material.SPRUCE_STAIRS);
            world.getBlockAt(x + 3, y + 1, z).setType(Material.AIR);
        } else if (type.equals("tower")) {
            for (int dy = 0; dy < 12; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) world.getBlockAt(x + dx, y + dy, z + dz).setType(wall);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                world.getBlockAt(x + dx, y + 12, z + dz).setType(Material.DARK_OAK_PLANKS);
        } else {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                if (dx * dx + dz * dz <= 9) world.getBlockAt(x + dx, y, z + dz).setType(Material.QUARTZ_BLOCK);
            world.getBlockAt(x, y + 1, z).setType(Material.WATER);
            world.getBlockAt(x, y + 2, z).setType(Material.WATER);
            world.getBlockAt(x, y + 3, z).setType(Material.WATER);
        }
        msg(p, "&aПостройка установлена!");
    }

    private void askAI(Player p, String question) {
        if (!getConfig().getBoolean("ai.enabled", true)) { msg(p, "&cAI отключён в config.yml."); return; }
        String key = System.getenv("ALPHABUILDER_API_KEY");
        if (key == null || key.isBlank()) key = getConfig().getString("ai.api-key", "");
        if (key == null || key.isBlank()) { msg(p, "&cНе задан API-ключ. Настрой ALPHABUILDER_API_KEY."); return; }
        String endpoint = getConfig().getString("ai.endpoint", "https://api.openai.com/v1/chat/completions");
        JsonObject body = new JsonObject();
        body.addProperty("model", getConfig().getString("ai.model", "gpt-4o-mini"));
        body.addProperty("max_tokens", getConfig().getInt("ai.max-tokens", 450));
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", getConfig().getString("ai.system-prompt", "Ты помощник строителя Minecraft."));
        JsonObject user = new JsonObject(); user.addProperty("role", "user"); user.addProperty("content", question);
        messages.add(system); messages.add(user); body.add("messages", messages);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        } catch (Exception ex) { msg(p, "&cНеверный AI endpoint: " + ex.getMessage()); return; }
        msg(p, "&7Alpha AI думает...");
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            String answer;
            try {
                if (error != null) throw new RuntimeException(error);
                if (response.statusCode() < 200 || response.statusCode() >= 300) throw new RuntimeException("HTTP " + response.statusCode());
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                answer = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
            } catch (Exception ex) { answer = "Не удалось получить ответ AI: " + ex.getMessage(); }
            String result = answer;
            Bukkit.getScheduler().runTask(this, () -> {
                if (p.isOnline()) p.sendMessage(prefix() + ChatColor.LIGHT_PURPLE + "Alpha AI: " + ChatColor.WHITE + result);
            });
        });
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (p.hasPermission("alphabuilder.use")) openMenu(p); else msg(p, "&cНет прав.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "build" -> openBuildMenu(p);
            case "world" -> teleportBuilderWorld(p);
            case "admin" -> openAdmin(p);
            case "pvp" -> openSection(p, "PvP");
            case "spleef" -> openSection(p, "Spleef");
            case "parkour" -> openSection(p, "Паркур");
            case "tournament" -> openSection(p, "Турниры");
            case "ai" -> {
                if (!p.hasPermission("alphabuilder.ai")) { msg(p, "&cНет прав."); return true; }
                if (args.length < 2) { msg(p, "&eИспользование: /ab ai <вопрос>"); return true; }
                askAI(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }
            case "confirm" -> {
                if (!p.hasPermission("alphabuilder.build")) { msg(p, "&cНет прав на строительство."); return true; }
                String type = pendingBuilds.get(p.getUniqueId());
                Location loc = previews.get(p.getUniqueId());
                World builderWorld = Bukkit.getWorld(getConfig().getString("world.name", "alphabuilder"));
                if (type == null || loc == null) { msg(p, "&cНет активного предпросмотра."); return true; }
                if (builderWorld == null || loc.getWorld() != builderWorld || p.getWorld() != builderWorld) {
                    pendingBuilds.remove(p.getUniqueId()); clearPreview(p);
                    msg(p, "&cСтроить можно только в отдельном мире AlphaBuilder."); return true;
                }
                pendingBuilds.remove(p.getUniqueId()); clearPreview(p); build(p, type, loc);
            }
            case "cancel" -> {
                pendingBuilds.remove(p.getUniqueId()); clearPreview(p); msg(p, "&eПредпросмотр отменён.");
            }
            default -> msg(p, "&eКоманды: /ab, build, world, ai, admin, pvp, spleef, parkour, tournament");
        }
        return true;
    }
}