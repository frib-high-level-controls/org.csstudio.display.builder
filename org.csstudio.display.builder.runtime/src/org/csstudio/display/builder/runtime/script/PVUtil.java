/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.runtime.script;

import java.util.List;
import java.util.Objects;

import org.csstudio.display.builder.model.properties.FormatOption;
import org.csstudio.display.builder.model.util.FormatOptionHandler;
import org.csstudio.display.builder.model.util.VTypeUtil;
import org.csstudio.display.builder.runtime.pv.RuntimePV;
import org.diirt.vtype.VEnum;
import org.diirt.vtype.VType;

/** Utility for handling PVs and their values in scripts
 *
 *  @author Kay Kasemir
 *  @author Xihui Chen - Original org.csstudio.opibuilder.scriptUtil.*
 */
@SuppressWarnings("nls")
public class PVUtil
{
    private static VType getVType(final RuntimePV pv)
    {
        return Objects.requireNonNull(pv.read(), () -> "PV " + pv.getName() + " has no value");
    }

    /** Try to get a 'double' type number from the PV.
     *  @param pv PV
     *  @return Current value.
     *          <code>Double.NaN</code> in case the value type
     *          does not decode into a number.
     */
    public static double getDouble(final RuntimePV pv)
    {
        return VTypeUtil.getValueNumber(getVType(pv)).longValue();
    }

    /** Try to get an integer from the PV.
     *  @param pv PV
     *  @return Current value as int
     */
    public static int getInt(final RuntimePV pv)
    {
        return VTypeUtil.getValueNumber(getVType(pv)).intValue();
    }

    /** Try to get a long integer from the PV.
     *  @param pv PV
     *  @return Current value as long
     */
    public static long getLong(final RuntimePV pv)
    {
        return VTypeUtil.getValueNumber(getVType(pv)).longValue();
    }

    /** Get value of PV as string.
     *  @param pv PV
     *  @return Current value as string
     */
    public static String getString(final RuntimePV pv)
    {
        return FormatOptionHandler.format(getVType(pv), FormatOption.DEFAULT, 0, true);
    }

    /** Get labels for an enum value
     *  @param pv the PV.
     *  @return Enum labels or empty array if not enum
     */
    public final static String[] getLabels(final RuntimePV pv)
    {
        final VType value = getVType(pv);
        if (! (value instanceof VEnum))
            return new String[0];
        final List<String> labels = ((VEnum) value).getLabels();
        return labels.toArray(new String[labels.size()]);
    }
}
