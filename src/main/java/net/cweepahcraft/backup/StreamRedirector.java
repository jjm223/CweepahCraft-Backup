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

import java.io.*;

public class StreamRedirector implements Runnable
{
    private final InputStream in;
    private final PrintStream out;

    public StreamRedirector(InputStream in, PrintStream out)
    {
        this.in = in;
        this.out = out;
    }

    @Override
    public void run()
    {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                out.println(line);
            }
        }
        catch (IOException ignored) {}
    }
}
