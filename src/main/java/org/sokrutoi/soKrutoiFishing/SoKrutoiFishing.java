package org.sokrutoi.soKrutoiFishing;

import org.sokrutoi.soKrutoiFishing.command.FirstCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoKrutoiFishing extends JavaPlugin {

    private static SoKrutoiFishing instance;
    private EventListener eventListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        eventListener = new EventListener(this);
        eventListener.loadConfig();
        getServer().getPluginManager().registerEvents(eventListener, this);
        new FirstCommand();
    }

    @Override
    public void onDisable() {
        eventListener.stopAllSessions();
    }

    public EventListener getEventListener() {
        return eventListener;
    }

    public static SoKrutoiFishing getInstance() {
        return instance;
    }
}
