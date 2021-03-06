/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * Copyright (C) 2016 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.csstudio.display.builder.model.widgets;

import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newBooleanPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newColorPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newIntegerPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propBackgroundColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propEnabled;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propFont;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propForegroundColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propLimitsFromPV;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propMaximum;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propMinimum;

import java.util.Arrays;
import java.util.List;

import org.csstudio.display.builder.model.Messages;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetCategory;
import org.csstudio.display.builder.model.WidgetConfigurator;
import org.csstudio.display.builder.model.WidgetDescriptor;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyCategory;
import org.csstudio.display.builder.model.WidgetPropertyDescriptor;
import org.csstudio.display.builder.model.persist.ModelReader;
import org.csstudio.display.builder.model.persist.NamedWidgetColors;
import org.csstudio.display.builder.model.persist.NamedWidgetFonts;
import org.csstudio.display.builder.model.persist.WidgetColorService;
import org.csstudio.display.builder.model.persist.WidgetFontService;
import org.csstudio.display.builder.model.persist.XMLUtil;
import org.csstudio.display.builder.model.properties.WidgetColor;
import org.csstudio.display.builder.model.properties.WidgetFont;
import org.osgi.framework.Version;
import org.w3c.dom.Element;

/**
 * @author claudiorosati, European Spallation Source ERIC
 * @version 1.0.0 13 Dec 2017
 */
public class ThumbWheelWidget extends WritablePVWidget {

    public static final WidgetDescriptor WIDGET_DESCRIPTOR = new WidgetDescriptor(
        "thumbwheel",
        WidgetCategory.CONTROL,
        "Thumb Wheel",
        "platform:/plugin/org.csstudio.display.builder.model/icons/thumbwheel.png",
        "Thumb wheel controller that can read/write a numeric PV",
        Arrays.asList(
            "org.csstudio.opibuilder.widgets.ThumbWheel"
        )
    ) {
        @Override
        public Widget createWidget ( ) {
            return new ThumbWheelWidget();
        }
    };

    public static final WidgetPropertyDescriptor<Integer>     propDecimalDigits    = newIntegerPropertyDescriptor(WidgetPropertyCategory.WIDGET,   "decimal_digits",     Messages.WidgetProperties_DecimalDigits, 0, Integer.MAX_VALUE);
    public static final WidgetPropertyDescriptor<Integer>     propIntegerDigits    = newIntegerPropertyDescriptor(WidgetPropertyCategory.WIDGET,   "integer_digits",     Messages.WidgetProperties_IntegerDigits, 1, Integer.MAX_VALUE);

    public static final WidgetPropertyDescriptor<WidgetColor> propInvalidColor     = newColorPropertyDescriptor  (WidgetPropertyCategory.DISPLAY,  "invalid_color",      Messages.WidgetProperties_InvalidColor);

    public static final WidgetPropertyDescriptor<Boolean>     propScrollEnabled    = newBooleanPropertyDescriptor(WidgetPropertyCategory.BEHAVIOR, "scroll_enabled",     Messages.WidgetProperties_ScrollEnabled);

    public static final WidgetPropertyDescriptor<WidgetColor> propButtonsColor     = newColorPropertyDescriptor  (WidgetPropertyCategory.MISC,     "buttons_color",      Messages.WidgetProperties_ButtonsColor);

    private volatile WidgetProperty<WidgetColor> background;
    private volatile WidgetProperty<WidgetColor> buttons_color;
    private volatile WidgetProperty<Integer>     decimal_digits;
    private volatile WidgetProperty<Boolean>     enabled;
    private volatile WidgetProperty<WidgetFont>  font;
    private volatile WidgetProperty<WidgetColor> foreground;
    private volatile WidgetProperty<Integer>     integer_digits;
    private volatile WidgetProperty<WidgetColor> invalid_color;
    private volatile WidgetProperty<Boolean>     limits_from_pv;
    private volatile WidgetProperty<Double>      maximum;
    private volatile WidgetProperty<Double>      minimum;
    private volatile WidgetProperty<Boolean>     scroll_enabled;

    public ThumbWheelWidget ( ) {
        super(WIDGET_DESCRIPTOR.getType(), 120, 50);
    }

    @Override
    public WidgetConfigurator getConfigurator ( final Version persistedVersion ) throws Exception {
        return new ThumbWheelConfigurator(persistedVersion);
    }

    public WidgetProperty<WidgetColor> propBackgroundColor ( ) {
        return background;
    }

    public WidgetProperty<WidgetColor> propButtonsColor ( ) {
        return buttons_color;
    }

    public WidgetProperty<Integer> propDecimalDigits ( ) {
        return decimal_digits;
    }

    public WidgetProperty<Boolean> propEnabled ( ) {
        return enabled;
    }

    public WidgetProperty<WidgetFont> propFont ( ) {
        return font;
    }

    public WidgetProperty<WidgetColor> propForegroundColor ( ) {
        return foreground;
    }

    public WidgetProperty<Integer> propIntegerDigits ( ) {
        return integer_digits;
    }

    public WidgetProperty<WidgetColor> propInvalidColor ( ) {
        return invalid_color;
    }

    public WidgetProperty<Boolean> propLimitsFromPV ( ) {
        return limits_from_pv;
    }

    public WidgetProperty<Double> propMaximum ( ) {
        return maximum;
    }

    public WidgetProperty<Double> propMinimum ( ) {
        return minimum;
    }

    public WidgetProperty<Boolean> propScrollEnabled ( ) {
        return scroll_enabled;
    }

    @Override
    protected void defineProperties ( final List<WidgetProperty<?>> properties ) {

        super.defineProperties(properties);

        properties.add(decimal_digits = propDecimalDigits.createProperty(this, 3));
        properties.add(integer_digits = propIntegerDigits.createProperty(this, 2));

        properties.add(background     = propBackgroundColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.WRITE_BACKGROUND)));
        properties.add(font           = propFont.createProperty(this, WidgetFontService.get(NamedWidgetFonts.DEFAULT)));
        properties.add(foreground     = propForegroundColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.TEXT)));
        properties.add(invalid_color  = propInvalidColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.ALARM_MAJOR)));

        properties.add(enabled        = propEnabled.createProperty(this, true));
        properties.add(limits_from_pv = propLimitsFromPV.createProperty(this, true));
        properties.add(minimum        = propMinimum.createProperty(this, 0.0));
        properties.add(maximum        = propMaximum.createProperty(this, 100.0));
        properties.add(scroll_enabled = propScrollEnabled.createProperty(this, false));

        properties.add(buttons_color  = propButtonsColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.BUTTON_BACKGROUND)));

    }

    /**
     * Custom configurator to read legacy *.opi files.
     */
    protected static class ThumbWheelConfigurator extends WidgetConfigurator {

        public ThumbWheelConfigurator ( Version xmlVersion ) {
            super(xmlVersion);
        }

        @Override
        public boolean configureFromXML ( final ModelReader reader, final Widget widget, final Element xml ) throws Exception {

            if ( !super.configureFromXML(reader, widget, xml) ) {
                return false;
            }

            if ( xml_version.getMajor() < 2 ) {

                ThumbWheelWidget thumbWheel = (ThumbWheelWidget) widget;

                XMLUtil.getChildInteger(xml, "decimalDigits").ifPresent(value -> thumbWheel.propDecimalDigits().setValue(value));
                XMLUtil.getChildInteger(xml, "integerDigits").ifPresent(value -> thumbWheel.propIntegerDigits().setValue(value));

            }

            return true;

        }

    }

}
