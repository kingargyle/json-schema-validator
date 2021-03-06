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

public final class ExtendsTest
{
    private JsonNode testNode;

    @BeforeClass
    public void setUp()
        throws IOException
    {
        testNode = JsonLoader.fromResource("/other/extends.json");
    }

    @Test
    public void testSimple()
        throws JsonValidationFailureException
    {
        testOne("simple");
    }

    @Test
    public void testMultiple()
        throws JsonValidationFailureException
    {
        testOne("multiple");
    }

    private void testOne(final String testName)
        throws JsonValidationFailureException
    {
        final JsonNode node = testNode.get(testName);
        final JsonNode schema = node.get("schema");
        final JsonNode good = node.get("good");
        final JsonNode bad = node.get("bad");

        final ValidationConfig cfg = new ValidationConfig();
        final JsonValidator validator = new JsonValidator(cfg, schema);

        ValidationReport report = validator.validate(good);

        assertTrue(report.isSuccess());
        assertTrue(report.getMessages().isEmpty());

        final List<String> expected = new LinkedList<String>();

        for (final JsonNode element: node.get("messages"))
            expected.add(element.getTextValue());

        report = validator.validate(bad);

        assertFalse(report.isSuccess());

        assertEquals(report.getMessages(), expected);
    }
}
