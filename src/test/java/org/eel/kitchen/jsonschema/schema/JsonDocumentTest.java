/*
 * Copyright (c) 2011, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.eel.kitchen.jsonschema.schema;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;

import static org.testng.Assert.*;

public final class JsonDocumentTest
{
    @Test
    public void testNonObject()
    {
        final InputStream in = new ByteArrayInputStream("2".getBytes());

        try {
            JsonDocument.fromInputStream(in);
            fail("No exception thrown");
        } catch (IOException e) {
            assertEquals(e.getMessage(), "not an object");
        }
    }

    @Test
    public void testGetString()
        throws IOException
    {
        final String input = "{\"hello\":\"world\",\"a\":5,\"b\":\"c\"}";

        final InputStream in = new ByteArrayInputStream(input.getBytes());

        final JsonDocument document = JsonDocument.fromInputStream(in);

        assertEquals(document.getString("hello"), "world");
        assertEquals(document.getString("b"), "c");

        try {
            document.getString("a");
            fail("No exception thrown");
        } catch (IOException e) {
            assertEquals(e.getMessage(), "a is not a string");
        }
    }

    @Test
    public void testGetNumber()
        throws IOException
    {
        final String input = "{  \"a\"  :  1,\"b\":   99018230980918,"
            + "    \"c\"  :    0.1     }";

        final InputStream in = new ByteArrayInputStream(input.getBytes());

        final JsonDocument document = JsonDocument.fromInputStream(in);

        Number n;

        n = document.getNumber("a");
        assertEquals(Integer.class, n.getClass());

        n = document.getNumber("b");
        assertEquals(Long.class, n.getClass());

        n = document.getNumber("c");
        assertEquals(BigDecimal.class, n.getClass());
    }
}
