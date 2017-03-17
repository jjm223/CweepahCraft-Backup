/*
 * CweepahCraft-Backup
 * Copyright (C) 2016  Jacob Martin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.cweepahcraft.backup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public class BackupPlugin extends JavaPlugin
{
    @Override
    public void onEnable()
    {
        saveDefaultConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        if (!sender.hasPermission("cc.backup"))
        {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length != 1)
        {
            return false;
        }

        reloadConfig();

        File workingDir = Bukkit.getWorldContainer();
        String worldName = args[0];
        if (Bukkit.getWorld(worldName) == null)
            return false;
        String backupUser = getConfig().getString("backup_user");
        String address = getConfig().getString("backup_server_addr");
        String backupPath = getConfig().getString("backup_path");
        String folderFormat = getConfig().getString("backup_folder_format");
        String driveName = getConfig().getString("drive_name");
        long rsyncTimeout = getConfig().getLong("rsync_timeout");

        UUID player;
        if (sender instanceof Player)
        {
            player = ((Player) sender).getUniqueId();
        }
        else
        {
            player = null;
        }

        new BackupRunnable(this, workingDir, worldName, backupUser, address, backupPath, folderFormat, driveName,
                rsyncTimeout, player).start();
        return true;
    }
}
