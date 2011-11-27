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
import org.codehaus.jackson.map.MappingJsonFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonDocument
{
    private static final int DEFAULT_SIZE = 4096;

    protected static final JsonFactory factory = new MappingJsonFactory(null);

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

            JsonToken token = parser.nextToken();

            if (token != JsonToken.START_OBJECT)
                throw new IOException("not an object");

            parser.nextToken();

            while ((token = parser.getCurrentToken()) != JsonToken.END_OBJECT) {
                switch (token) {
                    case FIELD_NAME:
                        final String text = parser.getText();
                        parser.nextValue();
                        offset = calculateOffset(text, parser);
                        fields.put(text, offset);
                        break;
                    case START_OBJECT: case START_ARRAY:
                        parser.skipChildren();
                        /* fall through */
                    default:
                        parser.nextToken();
                }
            }
        } finally {
            in.close();
        }
    }

    private long calculateOffset(final String s, final JsonParser parser)
    {
        /*
         * I hate to have to do that, but I have no choice: Jackson doesn't
         * provide a way to point to the beginning of a value!!
         */
        int ret = (int) parser.getTokenLocation().getCharOffset() + s.length()
            + 2;

        final byte[] b = buf.array();

        while (b[ret++] != ':')
            ; /* nothing */

        return ret;
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

        long beginOffset = fields.get(field);

        final byte[] array = buf.array();

        final InputStream in = new ByteArrayInputStream(array);

        if (beginOffset != in.skip(beginOffset))
            throw new IOException("cannot seek " + beginOffset + " in stream");

        final JsonParser parser = factory.createJsonParser(in);

        parser.nextToken();

        beginOffset += parser.getTokenLocation().getCharOffset();

        if (!parser.getCurrentToken().isScalarValue())
            parser.skipChildren();

        final int size = (int) parser.getCurrentLocation().getCharOffset();

        final ByteBuffer ret = ByteBuffer.allocate(size);

        ret.put(array, (int) beginOffset, size);
        return ret;
    }

    public final Number getNumber(final String field)
        throws IOException
    {
        Number ret = numberValues.get(field);

        if (ret != null)
            return ret;

        final JsonParser parser = getParser(field);
        ret = parser.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT
            ? parser.getDecimalValue() : parser.getNumberValue();

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
