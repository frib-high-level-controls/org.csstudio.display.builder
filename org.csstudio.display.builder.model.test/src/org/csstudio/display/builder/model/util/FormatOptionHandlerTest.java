/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.csstudio.display.builder.model.properties.FormatOption;
import org.diirt.vtype.Display;
import org.diirt.vtype.VType;
import org.diirt.vtype.ValueFactory;
import org.junit.Test;

/** JUnit test of {@link FormatOptionHandler}
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class FormatOptionHandlerTest
{
    final NumberFormat fmt = DecimalFormat.getNumberInstance();
    final Display display = ValueFactory.newDisplay(-10.0, -9.0, -8.0, "V", fmt, 8.0, 9.0, 10.0, -10.0, 10.0);

    @Test
    public void testNaNInf() throws Exception
    {
        VType number = ValueFactory.newVDouble(Double.NaN, display);
        String text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("NaN V"));

        number = ValueFactory.newVDouble(Double.POSITIVE_INFINITY, display);
        text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("Infinity V"));

        number = ValueFactory.newVDouble(Double.NEGATIVE_INFINITY, display);
        text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("-Infinity V"));
    }

    @Test
    public void testDecimal() throws Exception
    {
        VType number = ValueFactory.newVDouble(3.16, display);

        assertThat(fmt.format(3.16), equalTo("3.16"));

        String text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("3.16 V"));

        text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, false);
        System.out.println(text);
        assertThat(text, equalTo("3.16"));

        text = FormatOptionHandler.format(number, FormatOption.DECIMAL, 4, true);
        System.out.println(text);
        assertThat(text, equalTo("3.1600 V"));

        text = FormatOptionHandler.format(number, FormatOption.DECIMAL, 1, true);
        System.out.println(text);
        assertThat(text, equalTo("3.2 V"));

        // For running in debugger: Repeated use of precision 4 should use cached format
        text = FormatOptionHandler.format(number, FormatOption.DECIMAL, 4, true);
        System.out.println(text);
        assertThat(text, equalTo("3.1600 V"));
    }

    @Test
    public void testExponential() throws Exception
    {
        VType number = ValueFactory.newVDouble(3.16, display);

        String text = FormatOptionHandler.format(number, FormatOption.DEFAULT, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("3.16 V"));

        text = FormatOptionHandler.format(number, FormatOption.EXPONENTIAL, 3, true);
        System.out.println(text);
        assertThat(text, equalTo("3.160E0 V"));

        text = FormatOptionHandler.format(number, FormatOption.EXPONENTIAL, 1, true);
        System.out.println(text);
        assertThat(text, equalTo("3.2E0 V"));
    }

    @Test
    public void testEngineering() throws Exception
    {
        VType number = ValueFactory.newVDouble(0.0316, display);

        // Somewhat unclear about the DecimalFormat's handling of 'engineering'

        // 3 'significant digits': 31.6
        String text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 3, true);
        System.out.println(text);
        assertThat(text, equalTo("31.6E-3 V"));

        text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 3, false);
        System.out.println(text);
        assertThat(text, equalTo("31.6E-3"));

        // 6 'significant digits': 31.6000
        text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 6, true);
        System.out.println(text);
        assertThat(text, equalTo("31.6000E-3 V"));

        // .. but isn't 12.35 4 sig. digits?
        number = ValueFactory.newVDouble(12345678.0, display);
        text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 2, true);
        System.out.println(text);
        assertThat(text, equalTo("12.35E6 V"));

        text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 3, true);
        System.out.println(text);
        assertThat(text, equalTo("12.346E6 V"));

        number = ValueFactory.newVDouble(3.14, display);
        text = FormatOptionHandler.format(number, FormatOption.ENGINEERING, 2, true);
        System.out.println(text);
        assertThat(text, equalTo("3.14E0 V"));
    }

    @Test
    public void testHex() throws Exception
    {
        VType number = ValueFactory.newVDouble(65535.0, display);

        String text = FormatOptionHandler.format(number, FormatOption.HEX, 4, true);
        System.out.println(text);
        assertThat(text, equalTo("0xFFFF V"));

        text = FormatOptionHandler.format(number, FormatOption.HEX, 8, true);
        System.out.println(text);
        assertThat(text, equalTo("0x0000FFFF V"));

        text = FormatOptionHandler.format(number, FormatOption.HEX, 16, true);
        System.out.println(text);
        assertThat(text, equalTo("0x000000000000FFFF V"));
    }

    @Test
    public void testString() throws Exception
    {
        VType number = ValueFactory.newVDouble(65.0, display);

        String text = FormatOptionHandler.format(number, FormatOption.STRING, -1, true);
        System.out.println(text);
        assertThat(text, equalTo("A V"));
    }

    @Test
    public void testCompact() throws Exception
    {
        String text = FormatOptionHandler.format(ValueFactory.newVDouble(65.43, display), FormatOption.COMPACT, 2, true);
        System.out.println(text);
        assertThat(text, equalTo("65.43 V"));

        text = FormatOptionHandler.format(ValueFactory.newVDouble(0.00006543, display), FormatOption.COMPACT, 2, true);
        System.out.println(text);
        assertThat(text, equalTo("6.54E-5 V"));

        text = FormatOptionHandler.format(ValueFactory.newVDouble(65430000.0, display), FormatOption.COMPACT, 2, true);
        System.out.println(text);
        assertThat(text, equalTo("6.54E7 V"));
    }
}
