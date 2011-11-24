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

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonDocument
{
    private static final int DEFAULT_SIZE = 4096;

    protected static final ObjectMapper mapper = new ObjectMapper();

    protected static final JsonFactory factory;

    static {
        mapper.enable(DeserializationConfig.Feature.USE_BIG_DECIMAL_FOR_FLOATS);
        factory = new MappingJsonFactory(mapper);
    }

    protected final Map<String, Long> fields
        = new LinkedHashMap<String, Long>();

    protected final Map<String, JsonNode> rawValues
        = new HashMap<String, JsonNode>();

    protected final Map<String, Number> numberValues
        = new HashMap<String, Number>();

    protected final Map<String, String> stringValues
        = new HashMap<String, String>();

    protected final Map<String, Boolean> booleanValues
        = new HashMap<String, Boolean>();

    private final ByteBuffer buf;

    protected JsonDocument(final ByteBuffer buf)
        throws IOException
    {
        this.buf = buf;
        computeFields();
    }

    private void computeFields()
        throws IOException
    {
        long offset;
        final InputStream in = new ByteArrayInputStream(buf.array());

        try {
            final JsonParser parser = factory.createJsonParser(in);

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IOException("not an object");

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                offset = parser.getCurrentLocation().getCharOffset();
                final String text = parser.getText();
                fields.put(text, offset);
                parser.nextToken();
                parser.skipChildren();
            }
        } finally {
            in.close();
        }
    }

    public static JsonDocument fromInputStream(final InputStream in)
        throws IOException
    {
        ByteBuffer buf = ByteBuffer.allocate(DEFAULT_SIZE);

        final byte[] tmp = new byte[DEFAULT_SIZE];

        int read, size = 0;

        try {
            while ((read = in.read(tmp)) != -1) {
                size += read;
                if (size > buf.capacity())
                    buf = renew(buf);
                buf.put(tmp, 0, read);
            }
        } finally {
            in.close();
        }

        final ByteBuffer ret = ByteBuffer.allocate(size);
        ret.put(buf.array(), 0, size);
        return new JsonDocument(ret);
    }

    private static ByteBuffer renew(final ByteBuffer buf)
    {
        final int newSize = buf.capacity() * 2;

        final ByteBuffer ret = ByteBuffer.allocate(newSize);
        ret.put(buf.array());
        return ret;
    }

    protected final JsonParser getParser(final String field)
        throws IOException
    {
        if (!fields.containsKey(field))
            throw new IOException("no such field " + field);

        final InputStream ret = new ByteArrayInputStream(buf.array());

        final Long expected = fields.get(field);
        final long offset = ret.skip(expected);

        if (offset != expected)
            throw new IOException("cannot seek " + expected + " bytes into "
                + "stream (got result " + offset + ")");

        final JsonParser parser = factory.createJsonParser(ret);
        parser.nextToken();

        return parser;
    }

    public final JsonNode getRawValue(final String field)
        throws IOException
    {
        JsonNode ret = rawValues.get(field);

        if (ret != null)
            return ret;

        final JsonParser parser = getParser(field);

        ret = parser.readValueAsTree();
        rawValues.put(field, ret);

        return ret;
    }

    public final ByteBuffer getBuf(final String field)
        throws IOException
    {
        if (!fields.containsKey(field))
            throw new IOException("no such field " + field);

        final int beginOffset = fields.get(field).intValue();

        final Iterator<String> iterator = fields.keySet().iterator();

        String s;

        while (iterator.hasNext()) {
            s = iterator.next();
            if (s.equals(field))
                break;
        }

        int size = buf.capacity() - beginOffset - 1;

        if (iterator.hasNext()) {
            /*
            * Ugly :/ But I haven't found a way for Jackson to tell me the
            * location of the comma
            */
            int commaOffset = fields.get(iterator.next()).intValue();
            while ((char) buf.array()[commaOffset--] != ',')
                ; /* Nothing */
            size = commaOffset - beginOffset + 1;
        }

        final ByteBuffer ret = ByteBuffer.allocate(size);

        ret.put(buf.array(), beginOffset, size);
        return ret;
    }

    public final Number getNumber(final String field)
        throws IOException
    {
        Number ret = numberValues.get(field);

        if (ret != null)
            return ret;

        ret = getParser(field).getNumberValue();

        numberValues.put(field, ret);

        return ret;
    }

    public final String getString(final String field)
        throws IOException
    {
        String ret = stringValues.get(field);

        if (ret != null)
            return ret;

        final JsonParser parser = getParser(field);

        if (parser.getCurrentToken() != JsonToken.VALUE_STRING)
            throw new IOException(field + " is not a string");

        ret = parser.getText();
        stringValues.put(field, ret);

        return ret;
    }

    public final boolean getBoolean(final String field)
        throws IOException
    {
        Boolean ret = booleanValues.get(field);

        if (ret != null)
            return ret;

        final JsonParser parser = getParser(field);

        switch (parser.getCurrentToken()) {
            case VALUE_TRUE: case VALUE_FALSE:
                ret = parser.getBooleanValue();
                booleanValues.put(field, ret);
                return ret;
            default:
                throw new IOException(field + " is not a boolean");
        }
    }

    @Override
    public int hashCode()
    {
        return buf.hashCode();
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (getClass() != o.getClass())
            return false;

        final JsonDocument other = (JsonDocument) o;

        return buf.equals(other.buf);
    }
}
