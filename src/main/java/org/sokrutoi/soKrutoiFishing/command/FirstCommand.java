package org.sokrutoi.soKrutoiFishing.command;

import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.sokrutoi.soKrutoiFishing.SoKrutoiFishing;

import java.util.List;

public class FirstCommand extends AbstractCommand {

    public FirstCommand() {
        super("sokrutoi");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Нету аргументов: /" + label + " <help|toggle>");
            return;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§aМеню помощи:\n1. Рыбачьте\n2. В разных биомах разная рыба\n3. /" + label + " toggle — переключить кастомный дроп");
            return;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("sokrutoi.admin")) {
                sender.sendMessage("§cНет прав!");
                return;
            }
            boolean enabled = SoKrutoiFishing.getInstance().getEventListener().toggleCustomFishing();
            sender.sendMessage(Component.text(
                    "Кастомная рыбалка: " + (enabled ? "включена ✔" : "выключена ✗"),
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
            return;
        }

        sender.sendMessage("§cНеизвестная команда. Используй /" + label + " help");
    }

    @Override
    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) return Lists.newArrayList("help", "toggle");
        return Lists.newArrayList();
    }
}