/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.scripting.javascript.io;

import javax.script.ScriptException;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.stream.Stream;

import org.apache.sling.scripting.javascript.internal.ScriptEngineHelper;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The <code>EspReaderTest</code> contains some simple test cases for the
 * <code>EspReader</code> class which processes ESP (ECMA Server Page) templated
 * JavaScript and produces plain JavaScript.
 */
class EspReaderTest {

    /** Test read() method */
    @Test
    void testReadSingle() throws IOException {
        String src = "<%var%>"; // expect var on reader

        Reader reader = new EspReader(new StringReader(src));
        try {
            assertEquals('v', reader.read(), "Character 1 must be 'v'");
            assertEquals('a', reader.read(), "Character 2 must be 'a'");
            assertEquals('r', reader.read(), "Character 3 must be 'r'");
            assertEquals(-1, reader.read(), "Character 4 must be -1");
        } finally {
            reader.close();
        }
    }

    /** Test read(char[], int, int) method */
    @Test
    void testReadArrayAll() throws IOException {
        String src = "<%var%>"; // expect var on reader

        Reader reader = new EspReader(new StringReader(src));
        try {
            char[] buf = new char[3];
            int rd = reader.read(buf, 0, buf.length);

            assertEquals(3, rd);
            assertEquals("var", new String(buf, 0, rd));

            // nothing more to read, expect EOF
            rd = reader.read(buf, 0, buf.length);
            assertEquals(-1, rd);
        } finally {
            reader.close();
        }
    }

    /** Test read(char[], int, int) method */
    @Test
    void testReadArrayOffset() throws IOException {
        String jsSrc = "var x = 0;";
        String src = "<%" + jsSrc + "%>";

        Reader reader = new EspReader(new StringReader(src));
        try {
            char[] buf = new char[10];
            int off = 2;
            int len = 3;
            int rd = reader.read(buf, off, len);
            assertEquals(len, rd);
            assertEquals("var", new String(buf, off, rd));

            off = 2;
            len = 7;
            rd = reader.read(buf, off, len);
            assertEquals(len, rd);
            assertEquals(" x = 0;", new String(buf, off, rd));

            // nothing more to read, expect EOF
            rd = reader.read(buf, 0, buf.length);
            assertEquals(-1, rd);
        } finally {
            reader.close();
        }
    }

    /** Test standard template text */
    @Test
    void testTemplate() throws IOException {
        assertEquals("out=response.writer;out.write(\"test\");", parse("test"));
        assertEquals("out=response.writer;out.write(\"test\\n\");\nout.write(\"test2\");", parse("test\ntest2"));
    }

    /** Test with a custom "out" initialization */
    @Test
    void testOutInit() throws IOException {
        final String input = "test";
        final String expected = "out=getOut();out.write(\"test\");";

        StringBuffer buf = new StringBuffer();

        EspReader r = new EspReader(new StringReader(input));
        r.setOutInitStatement("out=getOut();");
        int c;
        while ((c = r.read()) >= 0) {
            buf.append((char) c);
        }

        assertEquals(expected, buf.toString());
    }

    /** Test plain JavaScript code */
    @Test
    void testCode() throws IOException {
        assertEquals(" test(); ", parse("<% test(); %>"));
        assertEquals(" \ntest();\ntest2(); ", parse("<% \ntest();\ntest2(); %>"));
    }

    /** Test JavaScript expressions */
    @Test
    void testExpr() throws IOException {
        assertEquals("out=response.writer;out.write( x + 1 );", parse("<%= x + 1 %>"));
        assertEquals(
                "out=response.writer;out.write(\"<!-- \");out.write( x + 1 );out.write(\" -->\");",
                parse("<!-- <%= x + 1 %> -->"));
    }

    /** Test JavaScript comment */
    @Test
    void testComment() throws IOException {
        assertEquals("", parse("<%-- test(); --%>"));
    }

    @ParameterizedTest
    @MethodSource("CompactExpressionCases")
    void testCompactExpressions(final String input, final String expected) throws IOException {
        final String actual = parse(input);
        assertEquals(flatten(expected), flatten(actual));
    }

    static Stream<Arguments> CompactExpressionCases() {
        return Stream.of(
                Arguments.of(
                        // input
                        Named.of("testCompactExpressionsDouble", "<html version=\"${1+1}\">\n"),
                        // expected
                        "out=response.writer;out.write(\"<html version=\\\"\");out.write(1+1);out.write(\"\\\">\\n\");\n"),
                Arguments.of(
                        // input
                        Named.of("testCompactExpressionsDoubleNegative", "<html version=\"{1+1}\">\n"),
                        // expected
                        "out=response.writer;out.write(\"<html version=\\\"{1+1}\\\">\\n\");\n"),
                Arguments.of(
                        // input
                        Named.of("testCompactExpressionsSingle", "<html version='${1+1}'>\n"),
                        // expected
                        "out=response.writer;out.write(\"<html version='\");out.write(1+1);out.write(\"'>\\n\");\n"),
                Arguments.of(
                        // input
                        Named.of("testCompactExpressionsSingleNegative", "<html version='{1+1}'>\n"),
                        // expected
                        "out=response.writer;out.write(\"<html version='{1+1}'>\\n\");\n"));
    }

    /** Test a complete template, using all features */
    @Test
    void testCompleteTemplate() throws IOException {
        final String input = "<html>\n"
                + "<head><title><%= someExpr %></title></head>\n"
                + "<!-- some HTML comment -->\n"
                + "<-- some ESP comment -->\n"
                + "// some javascript comment\n"
                + "/* another javascript comment /*\n"
                + "<%\n"
                + "expr on\n"
                + "two lines\n"
                + "%>\n"
                + "<verbatim stuff=\"quoted\">xyz</verbatim>\n"
                + "<moreverbatim stuff=\'single\'>xx</moreverbatim>\n"
                + "<!-- HTML comment with <% expr.here; %> and EOL\n-->\n"
                + "</html>";

        final String expected = "out=response.writer;out.write(\"<html>\\n\");\n"
                + "out.write(\"<head><title>\");out.write( someExpr );out.write(\"</title></head>\\n\");\n"
                + "out.write(\"<!-- some HTML comment -->\\n\");\n"
                + "out.write(\"<-- some ESP comment -->\\n\");\n"
                + "out.write(\"// some javascript comment\\n\");\n"
                + "out.write(\"/* another javascript comment /*\\n\");\n"
                + "\n"
                + "expr on\n"
                + "two lines\n"
                + "out.write(\"\\n\");\n"
                + "out.write(\"<verbatim stuff=\\\"quoted\\\">xyz</verbatim>\\n\");\n"
                + "out.write(\"<moreverbatim stuff='single'>xx</moreverbatim>\\n\");\n"
                + "out.write(\"<!-- HTML comment with \"); expr.here; out.write(\" and EOL\\n\");\n"
                + "out.write(\"-->\\n\");\n"
                + "out.write(\"</html>\");";

        final String actual = parse(input);
        assertEquals(flatten(expected), flatten(actual));
    }

    /** Test a complete template, using all features */
    @Test
    void testNumericExpression() throws IOException {
        String input = "<%= 1 %>";
        String expected = "out=response.writer;out.write( 1 );";
        String actual = parse(input);
        assertEquals(expected, actual);

        input = "<%= \"1\" %>";
        expected = "out=response.writer;out.write( \"1\" );";
        actual = parse(input);
        assertEquals(expected, actual);

        input = "<%= '1' %>";
        expected = "out=response.writer;out.write( '1' );";
        actual = parse(input);
        assertEquals(expected, actual);
    }

    /** Test a complete template, using all features */
    @Test
    void testNumericExpressionOutput() throws ScriptException {
        ScriptEngineHelper script = new ScriptEngineHelper();

        String input = "out.write( 1 );";
        String actual = script.evalToString(input);
        String expected = "1";
        assertEquals(expected, actual);

        input = "out.write( \"1\" );";
        actual = script.evalToString(input);
        expected = "1";
        assertEquals(expected, actual);

        input = "out.write( '1' );";
        actual = script.evalToString(input);
        expected = "1";
        assertEquals(expected, actual);
    }

    @Test
    void testColon() throws IOException {
        final String input = "currentNode.text:<%= currentNode.text %>";
        final String expected =
                "out=response.writer;" + "out.write(\"currentNode.text:\");" + "out.write( currentNode.text );";
        final String actual = parse(input);
        assertEquals(expected, actual);
    }

    @Test
    void testEqualSigns() throws IOException {
        final String input = "currentNode.text=<%= currentNode.text %>";
        final String expected =
                "out=response.writer;" + "out.write(\"currentNode.text=\");" + "out.write( currentNode.text );";
        final String actual = parse(input);
        assertEquals(expected, actual);
    }

    @Test
    void testSingleQuoted() throws IOException {
        final String input = "currentNode.text='<%= currentNode.text %>'";
        final String expected = "out=response.writer;"
                + "out.write(\"currentNode.text='\");"
                + "out.write( currentNode.text );"
                + "out.write(\"'\");";
        final String actual = parse(input);
        assertEquals(expected, actual);
    }

    @Test
    void testDoubleQuoted() throws IOException {
        final String input = "currentNode.text=\"<%= currentNode.text %>\"";
        final String expected = "out=response.writer;"
                + "out.write(\"currentNode.text=\\\"\");"
                + "out.write( currentNode.text );"
                + "out.write(\"\\\"\");";
        final String actual = parse(input);
        assertEquals(expected, actual);
    }

    /** Helper to pass an ESP text through the EspReader and return the result */
    private String parse(String text) throws IOException {
        StringBuffer buf = new StringBuffer();

        Reader r = new EspReader(new StringReader(text));
        try {
            int c;
            while ((c = r.read()) >= 0) {
                buf.append((char) c);
            }

            return buf.toString();
        } finally {
            r.close();
        }
    }

    /** Replace \n with . in strings to make it easier to compare visually for testing */
    private static String flatten(String str) {
        return str.replace('\n', '.');
    }
}
