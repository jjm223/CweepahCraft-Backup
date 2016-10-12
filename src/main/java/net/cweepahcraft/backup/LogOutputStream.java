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

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogOutputStream extends OutputStream
{
    private Logger logger;
    private Level logLevel;

    private String buffer = "";

    public LogOutputStream(Logger logger, Level logLevel)
    {
        this.logger = logger;
        this.logLevel = logLevel;
    }

    @Override
    public void write(int b) throws IOException
    {
        byte[] bytes = new byte[1];
        bytes[0] = (byte) (b & 0xff);
        buffer = buffer + new String(bytes);

        if (buffer.endsWith("\n"))
        {
            buffer = buffer.substring(0, buffer.length() - 1);
            flush();
        }
    }

    public void flush()
    {
        logger.log(logLevel, buffer);
        buffer = "";
    }
}
