/*
 * CweepahCraft-Backup
 * Copyright (C) 2017  Jacob Martin
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

public class ProcessTimeout
{
    private final long timeout;
    private final String timeoutIdentifier;
    private final Runnable timeoutTask;

    private volatile Thread timeoutThread;

    public ProcessTimeout(long timeout, String timeoutIdentifier, Runnable timeoutTask)
    {
        this.timeout = timeout;
        this.timeoutIdentifier = timeoutIdentifier;
        this.timeoutTask = timeoutTask;
    }

    public void start()
    {
        if (timeout <= 0 || timeoutThread != null)
        {
            return;
        }

        timeoutThread = new Thread(() ->
        {
            try
            {
                Thread.sleep(timeout);
                timeoutTask.run();
            }
            catch (InterruptedException ignored) {}
        }, "TimeoutThread-" + timeoutIdentifier);

        timeoutThread.start();
    }

    public void finish()
    {
        if (timeoutThread != null) timeoutThread.interrupt();
    }
}
