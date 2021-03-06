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

package org.eel.kitchen.jsonschema.other;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.eel.kitchen.jsonschema.main.JsonValidationFailureException;
import org.eel.kitchen.jsonschema.main.JsonValidator;
import org.eel.kitchen.jsonschema.main.ValidationConfig;
import org.eel.kitchen.jsonschema.main.ValidationReport;
import org.eel.kitchen.util.JsonLoader;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.testng.Assert.*;

public final class FormatTest
{
    private static final JsonNodeFactory factory = JsonNodeFactory.instance;
    private JsonNode testNode;
    private final ValidationConfig cfg = new ValidationConfig();

    @BeforeClass
    public void setUp()
        throws IOException
    {
        testNode = JsonLoader.fromResource("/other/format.json");
    }

    @Test
    public void testStyle()
        throws JsonValidationFailureException
    {
        testOne("style");
    }

    @Test
    public void testIPV4()
        throws JsonValidationFailureException
    {
        testOne("ip-address");
    }

    @Test
    public void testPhone()
        throws JsonValidationFailureException
    {
        testOne("phone");
    }

    @Test
    public void testUnixEpoch()
        throws JsonValidationFailureException
    {
        testOne("utc-millisec");
    }

    @Test
    public void testURI()
        throws JsonValidationFailureException
    {
        testOne("uri");
    }

    @Test
    public void testDate()
        throws JsonValidationFailureException
    {
        testOne("date");
    }

    @Test
    public void testDateTime()
        throws JsonValidationFailureException
    {
        testOne("date-time");
    }

    @Test
    public void testTime()
        throws JsonValidationFailureException
    {
        testOne("time");
    }

    @Test
    public void testHostName()
        throws JsonValidationFailureException
    {
        final ObjectNode schema = factory.objectNode();
        schema.put("format", "host-name");

        final JsonValidator validator = new JsonValidator(cfg, schema);
        ValidationReport report;

        final String msg = "#: string is not a valid hostname";

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i <= 257; i++)
            sb.append('a');

        report = validator.validate(factory.textNode(sb.toString()));

        assertFalse(report.isSuccess());

        assertEquals(msg, report.getMessages().get(0));

        report = validator.validate(factory.textNode("a+"));

        assertFalse(report.isSuccess());

        assertEquals(msg, report.getMessages().get(0));
    }

    @Test
    public void testUnknownFormatFails()
        throws JsonValidationFailureException
    {
        final ObjectNode schema = factory.objectNode();
        schema.put("format", "izjefoizjoeijf");

        final JsonValidator validator = new JsonValidator(cfg, schema);

        final ValidationReport report = validator.validate(factory.nullNode());

        assertFalse(report.isSuccess());
        assertFalse(report.isError());

        assertEquals(report.getMessages().size(), 1);
        assertEquals(report.getMessages().get(0), "#: no validator for "
            + "format izjefoizjoeijf");
    }

    private void testOne(final String fmt)
        throws JsonValidationFailureException
    {
        final JsonNode node = testNode.get(fmt);
        final JsonNode instance = node.get("instance");

        final ObjectNode schema = factory.objectNode();

        schema.put("format", fmt);

        final JsonValidator validator = new JsonValidator(cfg, schema);

        final ValidationReport report = validator.validate(instance);

        assertFalse(report.isSuccess());

        final List<String> messages = new LinkedList<String>();

        for (final JsonNode msg: node.get("messages"))
            messages.add(msg.getTextValue());

        assertEquals(report.getMessages(), messages);
    }
}
