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

import net.cweepahcraft.framework.api.NotifySlack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackupRunnable extends BukkitRunnable
{
    private BackupPlugin plugin;

    private final File workingDirectory;
    private final String world;
    private final String backupUser;
    private final String serverAddress;
    private final String backupPath;
    private final String folderFormat;
    private final String driveName;
    private final long rsyncTimeout;
    private final UUID player;

    private volatile Process activeProcess;

    private PrintStream logOut;
    private PrintStream logErr;

    public BackupRunnable(BackupPlugin plugin, File workingDirectory, String world, String backupUser,
                          String serverAddress, String backupPath, String folderFormat, String driveName,
                          long rsyncTimeout, UUID player)
    {
        this.plugin = plugin;
        this.workingDirectory = workingDirectory;
        this.world = world;
        this.backupUser = backupUser;
        this.serverAddress = serverAddress;
        this.backupPath = backupPath;
        this.folderFormat = folderFormat;
        this.driveName = driveName.replace("/", "\\/");
        this.rsyncTimeout = rsyncTimeout;
        this.player = player;

        this.logOut = new PrintStream(new LogOutputStream(plugin.getLogger(), Level.INFO));
        this.logErr = new PrintStream(new LogOutputStream(plugin.getLogger(), Level.SEVERE));
    }

    public void start()
    {
        runTaskAsynchronously(plugin);
    }

    public void run()
    {
        Bukkit.getScheduler().runTask(plugin, () -> {
            CommandSender player = getSender();
            player.sendMessage(ChatColor.YELLOW + "Getting remaining space on remote drive...");
        });

        int kibibytesLeft;

        try
        {
            kibibytesLeft = kibibytesLeft();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            finish(false);
            return;
        }

        FutureTask<File> saveOff = new FutureTask<>(() -> {
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null)
                return null;
            bukkitWorld.setAutoSave(false);
            CommandSender player = getSender();

            player.sendMessage(ChatColor.YELLOW + "Remote drive has " + (kibibytesLeft / 1048576) + " GiB left.");
            player.sendMessage(ChatColor.AQUA + "Backing up world '" + world + "'");

            return bukkitWorld.getWorldFolder();
        });

        Bukkit.getScheduler().runTask(plugin, saveOff);

        File worldFolder;
        try
        {
            worldFolder = saveOff.get();
        }
        catch (InterruptedException | ExecutionException ex)
        {
            ex.printStackTrace();
            finish(false);
            return;
        }

        if (worldFolder == null)
            return;

        ProcessTimeout timeout = new ProcessTimeout(rsyncTimeout, "Backup-" + this.world, () ->
        {
            plugin.getLogger().severe("Backup timed out.");
            activeProcess.destroyForcibly();
        });

        timeout.start();

        try
        {
            rsync(worldFolder);
            timeout.finish();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            finish(false);
            return;
        }

        finish(true);
    }

    private void finish(boolean success)
    {
        StringBuilder builder = new StringBuilder("Backup of ").append(world);
        if (success)
        {
            builder.append(" finished.");
        }
        else
        {
            builder.append(" failed. See console for details.");
        }
        builder.insert(0, success ? ChatColor.GREEN : ChatColor.RED);

        new BukkitRunnable()
        {
            @Override
            public void run()
            {
                CommandSender player = getSender();
                player.sendMessage(builder.toString());

                Bukkit.getWorld(world).setAutoSave(true);

                if (!success && Bukkit.getPluginManager().getPlugin("CweepahCraft-Framework") != null)
                {
                    new NotifySlack(builder.toString().substring(2), "@jacob").sendMessage();
                }
            }
        }.runTask(plugin);
    }

    private CommandSender getSender()
    {
        Player player;

        return this.player == null ? Bukkit.getConsoleSender() :
                (((player = Bukkit.getPlayer(this.player)) == null) ? Bukkit.getConsoleSender() : player);
    }

    private void rsync(File worldFolder) throws Exception
    {
        Date date = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String dateString = format.format(date);

        String backupPath = this.backupPath;
        backupPath += File.separator + String.format(this.folderFormat, dateString) + File.separator;
        makeRemoteDir(backupPath);
        String remotePath = backupUser + "@" + serverAddress + ":" + backupPath;
        runProcess(workingDirectory, "rsync", "-a", worldFolder.getName(), remotePath);
    }

    private void makeRemoteDir(String remotePath) throws Exception
    {
        runProcess(workingDirectory, "ssh", backupUser + "@" + serverAddress, "mkdir", "-p", remotePath);
    }

    private int kibibytesLeft() throws Exception
    {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        runProcess(workingDirectory, new PrintStream(outputStream), System.err, "ssh", backupUser + "@" + serverAddress, "df");

        String result = new String(outputStream.toByteArray());

        String patternString = driveName + "\\s+[0-9]*\\s+[0-9]*\\s+([0-9]*)";

        Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(result);
        if (!matcher.find())
        {
            logErr.println("Failed to match drive! Incorrect drive name?");
            return 0;
        }

        String kibibytes = matcher.group(1);
        return Integer.parseInt(kibibytes);
    }

    private int runProcess(File workingDir, String ... command) throws Exception
    {
        return runProcess(workingDir, logOut, logErr, command);
    }

    private int runProcess(File workingDir, PrintStream out, PrintStream err, String ... command) throws Exception
    {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);

        activeProcess = pb.start();

        new Thread(new StreamRedirector(activeProcess.getInputStream(), out)).start();
        new Thread(new StreamRedirector(activeProcess.getErrorStream(), err)).start();

        int status = activeProcess.waitFor();

        if (status != 0)
        {
            throw new RuntimeException("An error occurred while running process " + Arrays.toString(command));
        }

        return status;
    }
}
