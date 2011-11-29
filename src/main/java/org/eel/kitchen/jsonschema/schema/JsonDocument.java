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
import org.codehaus.jackson.JsonLocation;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.eel.kitchen.util.NodeType;

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

    protected static final ObjectMapper mapper = new ObjectMapper()
        .enable(DeserializationConfig.Feature.USE_BIG_DECIMAL_FOR_FLOATS);

    protected static final JsonFactory factory
        = new MappingJsonFactory(mapper);

    protected final Map<String, KeywordInfo> fields
        = new LinkedHashMap<String, KeywordInfo>();

    protected final Map<String, JsonNode> rawValues
        = new HashMap<String, JsonNode>();

    protected final Map<String, Number> numberValues
        = new HashMap<String, Number>();

    protected final Map<String, String> stringValues
        = new HashMap<String, String>();

    protected final Map<String, Boolean> booleanValues
        = new HashMap<String, Boolean>();

    private final ByteBuffer buf;

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
        ret.position(0);
        return new JsonDocument(ret);
    }

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
        KeywordInfo info;

        final InputStream in = new ByteArrayInputStream(buf.array());

        try {
            final JsonParser parser = factory.createJsonParser(in);

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IOException("not an object");

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String text = parser.getText();
                parser.nextValue();
                info = new KeywordInfo();
                offset = calculateOffset(text, parser);
                info.setOffset(offset);
                info.setType(NodeType.fromToken(parser.getCurrentToken()));
                fields.put(text, info);
                parser.skipChildren();
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

    private static ByteBuffer renew(final ByteBuffer buf)
    {
        final int newSize = buf.capacity() * 2;

        final ByteBuffer ret = ByteBuffer.allocate(newSize);
        ret.put(buf.array());
        return ret;
    }

    protected final KeywordInfo getInfo(final String field)
        throws IOException
    {
        final KeywordInfo ret = fields.get(field);

        if (ret == null)
            throw new IOException("no such field " + field);

        return ret;
    }

    protected final JsonParser getParser(final String field)
        throws IOException
    {
        final InputStream ret = new ByteArrayInputStream(buf.array());

        final Long offset = fields.get(field).getOffset();

        if (offset != ret.skip(offset)) {
            ret.close();
            throw new IOException("cannot seek " + offset + " bytes in stream");
        }

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

        try {
            ret = parser.readValueAsTree();
            rawValues.put(field, ret);
        } finally {
            parser.close();
        }

        return ret;
    }

    public final ByteBuffer getBuf(final String field)
        throws IOException
    {
        final ByteBuffer ret;

        final JsonParser parser = getParser(field);
        final JsonToken token = parser.getCurrentToken();

        try {
            if (token.isScalarValue()) {
                String s = parser.getText();
                if (token == JsonToken.VALUE_STRING)
                    s = '"' + s + '"';
                ret = ByteBuffer.allocate(s.length());
                ret.put(s.getBytes());
                return ret;
            }

            JsonLocation location;

            location = parser.getTokenLocation();
            final long offset = location.getCharOffset();

            parser.skipChildren();

            location = parser.getCurrentLocation();
            final int size = (int) (location.getCharOffset() - offset + 1);

            ret = ByteBuffer.allocate(size);

            final int start = (int) (fields.get(field).getOffset() + offset);
            ret.put(buf.array(), start, size);
            return ret;
        } finally {
            parser.close();
        }
    }

    public final Number getNumber(final String field)
        throws IOException
    {
        Number ret = numberValues.get(field);

        if (ret != null)
            return ret;

        final KeywordInfo info = fields.get(field);
        final NodeType type = info.getType();

        if (type != NodeType.INTEGER && type != NodeType.NUMBER)
            throw new IOException(field + " is not a number");

        final JsonParser parser = getParser(field);

        try {
            ret = type == NodeType.INTEGER ? parser.getNumberValue()
                : parser.getDecimalValue();
            numberValues.put(field, ret);
            return ret;
        } finally {
            parser.close();
        }
    }

    public final String getString(final String field)
        throws IOException
    {
        String ret = stringValues.get(field);

        if (ret != null)
            return ret;

        final NodeType type = fields.get(field).getType();

        if (type != NodeType.STRING)
            throw new IOException(field + " is not a string");

        final JsonParser parser = getParser(field);

        try {
            ret = parser.getText();
            stringValues.put(field, ret);
            return ret;
        } finally {
            parser.close();
        }
    }

    public final boolean getBoolean(final String field)
        throws IOException
    {
        Boolean ret = booleanValues.get(field);

        if (ret != null)
            return ret;

        final NodeType type = fields.get(field).getType();

        if (type != NodeType.BOOLEAN)
            throw new IOException(field + " is not a boolean");

        final JsonParser parser = getParser(field);

        try {
            ret = parser.getBooleanValue();
            booleanValues.put(field, ret);
            return ret;
        } finally {
            parser.close();
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

    protected static class KeywordInfo
    {
        private Long offset;
        private NodeType type;

        public Long getOffset()
        {
            return offset;
        }

        public void setOffset(final Long offset)
        {
            this.offset = offset;
        }

        public NodeType getType()
        {
            return type;
        }

        public void setType(final NodeType type)
        {
            this.type = type;
        }
    }
}
