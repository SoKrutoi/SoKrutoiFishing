package org.sokrutoi.soKrutoiFishing.command;

import com.google.common.collect.Lists;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class FirstCommand extends AbstractCommand {

    public FirstCommand() {
        super("sokrutoi");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if(args.length == 0) {
            sender.sendMessage("Нету аргументов: /" + label + " arg");
            return;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.GREEN + "Это меню помощи: \n" +
                    "1. lol \n" +
                    "2. kek");
        }
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if(args.length == 1) return Lists.newArrayList("help");
        return Lists.newArrayList();
    }
}
