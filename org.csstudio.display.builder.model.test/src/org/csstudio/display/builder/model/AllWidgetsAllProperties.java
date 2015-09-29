/*******************************************************************************
 * Copyright (c) 2015 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model;


import java.io.FileOutputStream;
import java.util.Arrays;

import org.csstudio.display.builder.model.macros.MacroParser;
import org.csstudio.display.builder.model.persist.ModelWriter;
import org.csstudio.display.builder.model.properties.OpenDisplayActionInfo;
import org.csstudio.display.builder.model.properties.OpenDisplayActionInfo.Target;
import org.csstudio.display.builder.model.widgets.ActionButtonWidget;

/** Tool that creates demo file with all widget types and all properties
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class AllWidgetsAllProperties
{
    private static final String EXAMPLE_FILE = "../org.csstudio.display.builder.runtime.test/examples/all_widgets.opi";

    public static void main(String[] args) throws Exception
    {
        final DisplayModel model = new DisplayModel();
        for (final WidgetDescriptor widget_type : WidgetFactory.getInstance().getWidgetDescriptions())
        {
            Widget widget = widget_type.createWidget();
            widget.setPropertyValue("name", widget_type.getName() + " 1");

            // For some widgets, adjust default values
            if (widget_type == ActionButtonWidget.WIDGET_DESCRIPTOR)
            {
                ActionButtonWidget button = (ActionButtonWidget) widget;
                button.widgetMacros().setValue(MacroParser.parseDefinition("S=Test, N=1"));
                button.behaviorActions().setValue(Arrays.asList(
                    new OpenDisplayActionInfo("Display", "other.opi", MacroParser.parseDefinition("N=2"), Target.REPLACE)));
            }

            model.addChild(widget);
        }
        ModelWriter.skip_defaults = false;
        try
        {
            final ModelWriter writer = new ModelWriter(new FileOutputStream(EXAMPLE_FILE));
            writer.writeModel(model);
            writer.close();
        }
        finally
        {
            ModelWriter.skip_defaults = true;
        }
    }
}