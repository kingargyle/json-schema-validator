/*
 * Copyright (c) 2011, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the Lesser GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.eel.kitchen.jsonschema.syntax;

import org.codehaus.jackson.JsonNode;
import org.eel.kitchen.jsonschema.context.ValidationContext;
import org.eel.kitchen.util.NodeType;

/**
 * Syntax validator specialized in validating "type" keywords ({@code type}
 * and {@code disallow})
 *
 * <p>It is here that the check for valid type names is performed (by
 * {@link #validateOne(String, JsonNode)}, which means the keyword validators
 * for both of these won't have to worry about this.</p>
 */
public abstract class TypeNodeSyntaxValidator
    extends SyntaxValidator
{
    private static final String ANY = "any";

    protected TypeNodeSyntaxValidator(final ValidationContext context,
        final String field)
    {
        super(context, field, NodeType.STRING, NodeType.ARRAY);
    }

    /**
     * <p>Check that:</p>
     * <ul>
     *     <li>if the keyword is a simple type, it is a known type;</li>
     *     <li>if it is an array, then its elements must be either known
     *     simple types or schemas.</li>
     * </ul>
     *
     * @see #validateOne(String, JsonNode)
     */
    @Override
    protected final void checkFurther()
    {
        if (!node.isArray()) {
            validateOne(node);
            return;
        }

        int i = 0;
        for (final JsonNode element : node)
            validateOne(String.format("array element %d: ", i++), element);
    }

    /**
     * Validate an element of a type array (also used for single element
     * validation)
     *
     * @param prefix the prefix to append to the report message in case of
     * error
     * @param element the element of the array to check
     */
    private void validateOne(final String prefix, final JsonNode element)
    {
        final NodeType type = NodeType.getNodeType(element);

        switch (type) {
            case OBJECT:
                return;
            case STRING:
                final String s = element.getTextValue();
                try {
                    if (!ANY.equals(s))
                        NodeType.valueOf(s.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    report.addMessage(String.format("%sunknown simple type %s",
                        prefix, s));
                }
                return;
            default:
                report.addMessage(String.format("%selement has wrong "
                    + "type %s (expected a simple type or a schema)",
                    prefix, type));
        }
    }

    /**
     * Shortcut to {@link #validateOne(String, JsonNode)} with an empty prefix
     *
     * @param element the element to check
     */
    private void validateOne(final JsonNode element)
    {
        validateOne("", element);
    }
}