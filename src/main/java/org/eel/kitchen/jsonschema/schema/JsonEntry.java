package org.eel.kitchen.jsonschema.schema;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonLocation;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.eel.kitchen.util.NodeType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class JsonEntry
{
    private static final int COLON = (int) ':';

    private static final JsonFactory factory = new JsonFactory();

    private final byte[] array;
    private final int offset;
    private final NodeType type;
    private final int size;

    public JsonEntry(final String field, final JsonLocation location,
        final ByteBuffer buf)
        throws IOException
    {

        array = buf.array();
        /*
         * First calculate the minimum offset from which we must search: skip
          * the field name and its enclosing quotes
         */
        int i = (int) location.getCharOffset() + field.length() + 2;

        /*
         * Then go one position behind the colon...
         */
        while (array[i++] != COLON)
            ;

        /*
         * Now create a parser from the calculated position
         */
        final int remaining = array.length - i + 1;
        final InputStream in = new ByteArrayInputStream(array, i, remaining);
        final JsonParser parser = factory.createJsonParser(in);

        JsonLocation tmp;

        final JsonToken  token = parser.nextToken();
        tmp = parser.getTokenLocation();

        /*
         * The beginning of the actual value is the offset within the array
         * plus the offset the parser found in the input stream
         */
        final int valueOffset = (int) tmp.getCharOffset();
        offset = i + valueOffset;

        /*
         * The type of the value is the token type of the current token
         */
        type = NodeType.fromToken(token);

        if (!token.isScalarValue())
            parser.skipChildren();

        /*
         * And the size is the end offset minus the beginning offset plus 1
         */
        tmp = parser.getCurrentLocation();
        size = (int) tmp.getCharOffset() - valueOffset + 1;
    }

    public NodeType getType()
    {
        return type;
    }

    public ByteBuffer asBuffer()
    {
        final ByteBuffer ret = ByteBuffer.allocate(size);

        ret.put(array, offset, size);
        return ret;
    }
}
