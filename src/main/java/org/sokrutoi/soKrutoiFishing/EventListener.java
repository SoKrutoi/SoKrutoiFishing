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

public class EventListener implements Listener {

    private final SoKrutoiFishing plugin;
    private final Map<UUID, FishingSession> sessions = new HashMap<>();
    private final Random random = new Random();

    private final List<String> patterns = List.of(
            "RRRYYYYGGGGGYYYYRRYYYGGYY",
            "RRYYYYGGGYYYYRRRRYYYYGG",
            "RRRYYYGGGGYYYYYYRRRGG",
            "RRYYYYYGGGGYYYYRRRYYY",
            "GGGRRRGGGRRRGGGRRRGGGRRR"
    );

    public EventListener(SoKrutoiFishing plugin) {
        this.plugin = plugin;
    }

    // =========================================================
    //                         МИНИИГРА
    // =========================================================

    private class FishingSession {

        private final Player player;
        private final FishHook hook;
        private final UUID sessionId = UUID.randomUUID(); // защита от дублей

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
            this.speed = 0.35 + random.nextDouble() * 0.25;
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

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (sessions.get(player.getUniqueId()) == FishingSession.this) {
                        sessions.remove(player.getUniqueId());
                        stop();
                        plugin.getLogger().info(player.getName() + " не успел вытащить рыбу (таймаут 25 сек)");
                        player.sendMessage(Component.text("Рыба сорвалась...", NamedTextColor.GRAY));
                    }
                }, 25 * 20L);

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
    //                          РЫБА (без изменений)
    // =========================================================

    private void spawnFish(Player player, Location loc) {
        FishData fishData = generateFish();
        plugin.getLogger().info(player.getName() + " выловил " + fishData.name() + " размером " + fishData.size() + " см");

        ItemStack fish = new ItemStack(Material.COD);
        ItemMeta meta = fish.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(fishData.name(), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Размер: " + fishData.size() + " см", NamedTextColor.GRAY)));
            meta.setCustomModelData(fishData.modelData());
            fish.setItemMeta(meta);
        }

        Item item = loc.getWorld().dropItem(loc, fish);

        Vector direction = player.getLocation().toVector().subtract(loc.toVector());
        direction.setY(0);
        Vector velocity = direction.normalize().multiply(0.45);
        velocity.setY(0.45);

        item.setVelocity(velocity);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    private FishData generateFish() {
        List<FishData> fishes = List.of(
                new FishData("Окунь",               1001, realisticSize(15, 45)),
                new FishData("Плотва",              1002, realisticSize(10, 45)),
                new FishData("Карась",              1003, realisticSize(10, 50)),
                new FishData("Краснопёрка",         1004, realisticSize(10, 40)),
                new FishData("Густера",             1005, realisticSize(12, 38)),
                new FishData("Лещ",                 1006, realisticSize(20, 80)),

                new FishData("Щука",                1007, realisticSize(40, 110)),
                new FishData("Судак",               1008, realisticSize(40, 100)),
                new FishData("Голавль",             1009, realisticSize(20, 60)),
                new FishData("Язь",                 1010, realisticSize(25, 65)),
                new FishData("Карп",                1011, realisticSize(25, 100)),
                new FishData("Сазан",               1012, realisticSize(35, 110)),

                new FishData("Налим",               1013, realisticSize(30, 90)),
                new FishData("Линь",                1014, realisticSize(20, 70)),
                new FishData("Амур белый",          1015, realisticSize(50, 130)),
                new FishData("Толстолобик",         1016, realisticSize(50, 140)),
                new FishData("Сом",                 1017, realisticSize(70, 300)),

                new FishData("Стерлядь",            1018, realisticSize(40, 110)),
                new FishData("Осётр",               1019, realisticSize(80, 220)),
                new FishData("Белуга",              1020, realisticSize(150, 500)),
                new FishData("Пиранья красная",     1021, realisticSize(15, 50)),
                new FishData("Тигровая рыба",       1024, realisticSize(50, 160)),
                new FishData("Аллигатор гар",       1025, realisticSize(120, 320)),
                new FishData("Гигантский сом Меконга", 1026, realisticSize(150, 350)),

                new FishData("Нильский окунь",      1027, realisticSize(80, 220)),
                new FishData("Гигантский барбус",   1028, realisticSize(70, 180)),

                new FishData("Снук",                1030, realisticSize(50, 130)),
                new FishData("Тарпон",              1031, realisticSize(100, 280)),
                new FishData("Пермит",              1032, realisticSize(40, 120)),
                new FishData("Синяя медуза",        1033, realisticSize(7, 70)),

                new FishData("Гигантская ставрида", 1034, realisticSize(70, 180)),
                new FishData("Корифена (Махи-Махи)",1035, realisticSize(80, 220)),
                new FishData("Ваху",                1036, realisticSize(90, 250)),
                new FishData("Жёлтопёрый тунец",    1037, realisticSize(100, 300)),
                new FishData("Синий марлин",        1038, realisticSize(250, 600)),
                new FishData("Парусник",            1039, realisticSize(200, 400)),
                new FishData("Меч-рыба",            1040, realisticSize(180, 450)),
                new FishData("Рыба-петух",          1041, realisticSize(70, 160))
        );
        return fishes.get(random.nextInt(fishes.size()));
    }

    private int realisticSize(int min, int max) {
        double gaussian = random.nextGaussian();
        double mean = (min + max) / 2.0;
        double deviation = (max - min) / 6.0;
        int size = (int) (mean + gaussian * deviation);
        return Math.max(min, Math.min(max, size));
    }

    private record FishData(String name, int modelData, int size) {}
}