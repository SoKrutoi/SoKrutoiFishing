package org.sokrutoi.soKrutoiFishing;

import org.bukkit.plugin.java.JavaPlugin;

public final class SoKrutoiFishing extends JavaPlugin {

    private static SoKrutoiFishing instance;
    private EventListener eventListener;

    @Override
    public void onEnable() {
        instance = this;
        eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);
    }

    @Override
    public void onDisable() {
        eventListener.stopAllSessions();
    }

    public static SoKrutoiFishing getInstance() {
        return instance;
    }
}
