package org.sokrutoi.soKrutoiFishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.*;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

import org.bukkit.block.Biome;
import static org.bukkit.block.Biome.*;

public class EventListener implements Listener {

    private final SoKrutoiFishing plugin;
    private final Map<UUID, FishingSession> sessions = new HashMap<>();
    private final Random random = new Random();

    private List<String> patterns = new ArrayList<>();
    private List<FishData> fishList = new ArrayList<>();
    private Set<Biome> oceanBiomes = new HashSet<>();
    private int timeoutTicks = 25 * 20;
    private double speedBase = 0.35;
    private double speedRange = 0.25;

    public EventListener(SoKrutoiFishing plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    //                         МИНИИГРА
    // =========================================================

    private class FishingSession {

        private final Player player;
        private final FishHook hook;
        private final UUID sessionId = UUID.randomUUID();

        private int cursor = 0;
        private int direction = 1;
        private final double speed;
        private final String pattern;

        private boolean ready = false;
        public boolean isReady() { return ready; }

        private BukkitTask task;

        public FishingSession(Player player, FishHook hook) {
            this.player = player;
            this.hook = hook;
            this.pattern = patterns.get(random.nextInt(patterns.size()));
            this.speed = speedBase + random.nextDouble() * speedRange;
        }

        public void start() {
            plugin.getLogger().info(player.getName() + " начал мини-игру рыбалки");
            player.showTitle(Title.title(
                    Component.empty(),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(30), Duration.ZERO)
            ));

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ready = true;
                startRenderingTask();

                // ПОСЛЕ:
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (sessions.get(player.getUniqueId()) == FishingSession.this) {
                        sessions.remove(player.getUniqueId());
                        stop();
                        plugin.getLogger().info(player.getName() + " не успел вытащить рыбу (таймаут)");
                        player.sendMessage(Component.text("Рыба сорвалась...", NamedTextColor.GRAY));
                    }
                }, timeoutTicks);

            }, 3L);
        }

        private void startRenderingTask() {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        plugin.getLogger().info(player.getName() + " вышел с сервера во время рыбалки — сессия закрыта");
                        cancel();
                        return;
                    }
                    if (!hook.isValid()) {
                        plugin.getLogger().info(player.getName() + " — поплавок пропал во время мини-игры — сессия закрыта");
                        sessions.remove(player.getUniqueId());
                        cancel();
                        player.clearTitle();
                        return;
                    }
                    if (sessions.get(player.getUniqueId()) != FishingSession.this ||
                            !sessions.get(player.getUniqueId()).sessionId.equals(sessionId)) {
                        plugin.getLogger().info(player.getName() + " — сессия устарела (дубль) — задача остановлена");
                        cancel();
                        player.clearTitle();
                        return;
                    }

                    cursor += direction;
                    if (cursor <= 0 || cursor >= pattern.length() - 1) {
                        direction *= -1;
                    }

                    player.sendTitlePart(TitlePart.SUBTITLE, buildBar());
                }
            }.runTaskTimer(plugin, 0L, Math.max(1, (long) (2 + (1 / speed))));
        }

        private Component buildBar() {
            Component bar = Component.empty();
            for (int i = 0; i < pattern.length(); i++) {
                if (i == cursor) {
                    bar = bar.append(Component.text("\uE004"));
                    continue;
                }
                char c = pattern.charAt(i);
                switch (c) {
                    case 'G' -> bar = bar.append(Component.text("\uE001"));
                    case 'Y' -> bar = bar.append(Component.text("\uE002"));
                    default -> bar = bar.append(Component.text("\uE003"));
                }
            }
            return bar;
        }

        public boolean isSuccess() {
            return pattern.charAt(cursor) == 'G';
        }

        public void stop() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            player.clearTitle();
            player.resetTitle();
        }

        public FishHook getHook() {
            return hook;
        }
    }

    // =========================================================
    //                         СОБЫТИЯ
    // =========================================================

    public void loadConfig() {
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        patterns = new ArrayList<>(cfg.getStringList("patterns"));
        if (patterns.isEmpty()) {
            patterns = new ArrayList<>(List.of("RRRYYYYGGGGGYYYYRRYYYGGYY"));
            plugin.getLogger().warning("Паттерны не найдены в конфиге, использую дефолтный");
        }

        timeoutTicks = cfg.getInt("timeout-seconds", 25) * 20;
        speedBase    = cfg.getDouble("cursor-speed-base", 0.35);
        speedRange   = cfg.getDouble("cursor-speed-range", 0.25);

        oceanBiomes = new HashSet<>();
        for (String s : cfg.getStringList("ocean-biomes")) {
            try {
                oceanBiomes.add(Biome.valueOf(s));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Неизвестный биом в конфиге: " + s);
            }
        }

        fishList = new ArrayList<>();
        for (var entry : cfg.getMapList("fish")) {
            try {
                String name     = (String) entry.get("name");
                int modelData   = (int) entry.get("model-data");
                int minSize     = (int) entry.get("min-size");
                int maxSize     = (int) entry.get("max-size");
                Rarity rarity   = Rarity.valueOf((String) entry.get("rarity"));
                BiomeType biome = BiomeType.valueOf((String) entry.get("biome"));
                fishList.add(new FishData(name, modelData, minSize, maxSize, rarity, biome));
            } catch (Exception ex) {
                plugin.getLogger().warning("Ошибка в записи рыбы: " + entry + " — " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Загружено " + fishList.size() + " рыб, " + patterns.size() + " паттернов");
    }

    public void stopAllSessions() {
        if (sessions.isEmpty()) return;
        plugin.getLogger().info("Плагин выключается — принудительно закрываем " + sessions.size() + " активных сессий");
        for (FishingSession s : sessions.values()) {
            s.stop();
        }
        sessions.clear();
    }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        FishingSession session = sessions.get(uuid);

        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            e.setCancelled(true);

            if (session != null) {
                Location hookLoc = session.getHook().getLocation().clone();
                FishHook hookRef = session.getHook();
                sessions.remove(uuid);
                session.stop();

                if (session.isSuccess()) {
                    plugin.getLogger().info(player.getName() + " попал в зелёную зону (CAUGHT_FISH)");
                    spawnFish(player, hookLoc);
                } else {
                    plugin.getLogger().info(player.getName() + " промахнулся (CAUGHT_FISH, во время BITE)");
                    player.sendMessage(Component.text("Рыба сорвалась!", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                }

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    hookRef.remove();
                }, 5L);

            } else {
                plugin.getLogger().info(player.getName() + " подсёк — мини-игра запущена");
                FishingSession newSession = new FishingSession(player, e.getHook());
                sessions.put(uuid, newSession);
                newSession.start();
            }
            return;
        }

        if (session != null && session.getHook() == e.getHook()) {

            switch (e.getState()) {
                case REEL_IN:
                    if (session.isReady()) {
                        e.setCancelled(true);
                        Location hookLoc = session.getHook().getLocation().clone();
                        FishHook hookRef = session.getHook();
                        sessions.remove(uuid);
                        session.stop();

                        if (session.isSuccess()) {
                            plugin.getLogger().info(player.getName() + " попал в зелёную зону (REEL_IN)");
                            spawnFish(player, hookLoc);
                        } else {
                            plugin.getLogger().info(player.getName() + " промахнулся (REEL_IN)");
                            player.sendMessage(Component.text("Рыба сорвалась!", NamedTextColor.RED));
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                        }

                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            hookRef.remove();
                        }, 5L);
                    } else {
                        plugin.getLogger().info(player.getName() + " нажал ПКМ до готовности мини-игры — игнорируем");
                    }
                    break;

                case IN_GROUND:
                    plugin.getLogger().info(player.getName() + " — поплавок застрял в земле — сессия закрыта");
                    sessions.remove(uuid);
                    session.stop();
                    break;

                case BITE:
                case LURED:
                    e.setCancelled(true);
                    break;

                default:
                    break;
            }
        }
    }

    // =========================================================
    //                          РЫБА
    // =========================================================

    private void spawnFish(Player player, Location loc) {
        Biome biome = loc.getBlock().getBiome();
        FishData fishData = generateFish(biome);
        int size = fishData.rollSize(random);
        plugin.getLogger().info(player.getName() + " выловил " + fishData.name() + " размером " + size + " см (" + fishData.rarity() + ")");

        ItemStack fish = new ItemStack(Material.COD);
        ItemMeta meta = fish.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(fishData.name(), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Размер: " + size + " см", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Редкость: " + fishData.rarity().displayName, fishData.rarity().color)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.setCustomModelData(fishData.modelData());
            fish.setItemMeta(meta);
        }

        Item item = loc.getWorld().dropItem(loc, fish);

        Vector direction = player.getLocation().toVector().subtract(loc.toVector());
        double horizontalDist = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        direction.setY(0);

        double verticalSpeed = 0.45;

        double horizontalSpeed = horizontalDist / 18.0;

        horizontalSpeed = Math.max(horizontalSpeed, 0.15);

        Vector velocity = direction.normalize().multiply(horizontalSpeed);
        velocity.setY(verticalSpeed);

        item.setVelocity(velocity);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private FishData generateFish(Biome biome) {
        BiomeType biomeType = getBiomeType(biome);

        List<FishData> pool = fishList.stream()
                .filter(f -> f.biome() == BiomeType.ANY || f.biome() == biomeType)
                .toList();

        if (pool.isEmpty()) {
            plugin.getLogger().warning("Нет рыб для биома " + biome + ", беру первую из списка");
            return fishList.get(0);
        }

        int totalWeight = pool.stream().mapToInt(f -> f.rarity().weight).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (FishData f : pool) {
            cumulative += f.rarity().weight;
            if (roll < cumulative) return f;
        }
        return pool.get(pool.size() - 1);
    }

    // ПОСЛЕ:
    private BiomeType getBiomeType(Biome biome) {
        return oceanBiomes.contains(biome) ? BiomeType.OCEAN : BiomeType.FRESHWATER;
    }

    private enum Rarity {
        COMMON(100,    "Обычная",    NamedTextColor.WHITE),
        UNCOMMON(40,   "Необычная",  NamedTextColor.GREEN),
        RARE(15,       "Редкая",     NamedTextColor.AQUA),
        EPIC(5,        "Эпическая",  NamedTextColor.LIGHT_PURPLE),
        LEGENDARY(1,   "Легендарная",NamedTextColor.GOLD);

        final int weight;
        final String displayName;
        final NamedTextColor color;

        Rarity(int weight, String displayName, NamedTextColor color) {
            this.weight = weight;
            this.displayName = displayName;
            this.color = color;
        }
    }

    private enum BiomeType { FRESHWATER, OCEAN, ANY }

    // ПОСЛЕ:
    private record FishData(String name, int modelData, int minSize, int maxSize, Rarity rarity, BiomeType biome) {
        public int rollSize(Random rng) {
            double gaussian = rng.nextGaussian();
            double mean = (minSize + maxSize) / 2.0;
            double deviation = (maxSize - minSize) / 6.0;
            int size = (int) (mean + gaussian * deviation);
            return Math.max(minSize, Math.min(maxSize, size));
        }
    }
}