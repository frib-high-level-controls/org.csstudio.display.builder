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
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newFilenamePropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.newIntegerPropertyDescriptor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propBackgroundColor;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propEnabled;
import static org.csstudio.display.builder.model.properties.CommonWidgetProperties.propTransparent;

import java.util.Collections;
import java.util.List;

import org.csstudio.display.builder.model.ArrayWidgetProperty;
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
import org.csstudio.display.builder.model.persist.WidgetColorService;
import org.csstudio.display.builder.model.properties.WidgetColor;
import org.osgi.framework.Version;
import org.w3c.dom.Element;

/**
 * @author claudiorosati, European Spallation Source ERIC
 * @version 1.0.0 19 Jun 2017
 */
public class SymbolWidget extends PVWidget {

    public final static String DEFAULT_SYMBOL = "platform:/plugin/org.csstudio.display.builder.model/icons/default_symbol.png"; //$NON-NLS-1$

    public static final WidgetDescriptor WIDGET_DESCRIPTOR = new WidgetDescriptor(
            "symbol",
            WidgetCategory.MONITOR,
            "Symbol",
            "platform:/plugin/org.csstudio.display.builder.model/icons/symbol.png",
            "A container of symbols displayed depending of the value of a PV"
        ) {
            @Override
            public Widget createWidget ( ) {
                return new SymbolWidget();
            }
        };

    public static final WidgetPropertyDescriptor<Integer>                       propArrayIndex    = newIntegerPropertyDescriptor (WidgetPropertyCategory.BEHAVIOR, "array_index",    Messages.WidgetProperties_ArrayIndex, 0, Integer.MAX_VALUE);
    public static final WidgetPropertyDescriptor<Boolean>                       propPreserveRatio = newBooleanPropertyDescriptor (WidgetPropertyCategory.BEHAVIOR, "preserve_ratio", Messages.WidgetProperties_PreserveRatio);

    /** 'symbol' property: element for list of 'symbols' property */
    private static final WidgetPropertyDescriptor<String>                       propSymbol        = newFilenamePropertyDescriptor(WidgetPropertyCategory.WIDGET,   "symbol",         Messages.WidgetProperties_Symbol);

    /** 'items' property: list of items (string properties) for combo box */
    public static final ArrayWidgetProperty.Descriptor<WidgetProperty<String> > propSymbols       = new ArrayWidgetProperty.Descriptor< WidgetProperty<String> >(
        WidgetPropertyCategory.WIDGET,
        "symbols",
        Messages.WidgetProperties_Symbols,
        (widget, index) -> propSymbol.createProperty(widget, DEFAULT_SYMBOL)
    );

    private volatile WidgetProperty<Integer>                     array_index;
    private volatile WidgetProperty<WidgetColor>                 background;
    private volatile WidgetProperty<Boolean>                     enabled;
    private volatile WidgetProperty<Boolean>                     preserve_ratio;
    private volatile ArrayWidgetProperty<WidgetProperty<String>> symbols;
    private volatile WidgetProperty<Boolean>                     transparent;

    /**
     * @param type Widget type.
     * @param default_width Default widget width.
     * @param default_height Default widget height.
     */
    public SymbolWidget ( ) {
        super(WIDGET_DESCRIPTOR.getType(), 100, 100);
    }

    public void addSymbol( String fileName ) {
        symbols.addElement(propSymbol.createProperty(this, fileName));
    }

    @Override
    public WidgetConfigurator getConfigurator ( final Version persistedVersion ) throws Exception {
        return new SymbolConfigurator(persistedVersion);
    }

    public WidgetProperty<Integer> propArrayIndex ( ) {
        return array_index;
    }

    public WidgetProperty<WidgetColor> propBackgroundColor ( ) {
        return background;
    }

    public WidgetProperty<Boolean> propEnabled ( ) {
        return enabled;
    }

    public WidgetProperty<Boolean> propPreserveRatio ( ) {
        return preserve_ratio;
    }

    public ArrayWidgetProperty<WidgetProperty<String>> propSymbols ( ) {
        return symbols;
    }

    public WidgetProperty<Boolean> propTransparent ( ) {
        return transparent;
    }

    @Override
    protected void defineProperties ( final List<WidgetProperty<?>> properties ) {

        super.defineProperties(properties);

        properties.add(symbols        = propSymbols.createProperty(this, Collections.emptyList()));

        properties.add(background     = propBackgroundColor.createProperty(this, WidgetColorService.getColor(NamedWidgetColors.BACKGROUND)));
        properties.add(transparent    = propTransparent.createProperty(this, true));

        properties.add(array_index    = propArrayIndex.createProperty(this, 0));
        properties.add(enabled        = propEnabled.createProperty(this, true));
        properties.add(preserve_ratio = propPreserveRatio.createProperty(this, true));

    }

    /**
     * Custom configurator to read legacy *.opi files.
     */
    protected static class SymbolConfigurator extends WidgetConfigurator {

        public SymbolConfigurator ( Version xmlVersion ) {
            super(xmlVersion);
        }

        @Override
        public boolean configureFromXML ( final ModelReader reader, final Widget widget, final Element xml ) throws Exception {

//            if ( !super.configureFromXML(reader, widget, xml) ) {
//                return false;
//            }
//
//            if ( xml_version.getMajor() < 2 ) {
//
//                SymbolWidget symbol = (SymbolWidget) widget;
//
//                XMLUtil.getChildColor(xml, "color_hi").ifPresent(c -> symbol.propColorHigh().setValue(c));
//                XMLUtil.getChildColor(xml, "color_lo").ifPresent(c -> symbol.propColorLow().setValue(c));
//                XMLUtil.getChildColor(xml, "foreground_color").ifPresent(c -> symbol.propValueColor().setValue(c));
//                XMLUtil.getChildDouble(xml, "level_hi").ifPresent(v -> symbol.propLevelHigh().setValue(v));
//                XMLUtil.getChildDouble(xml, "level_lo").ifPresent(v -> symbol.propLevelLow().setValue(v));
//                XMLUtil.getChildBoolean(xml, "show_hi").ifPresent(s -> symbol.propShowHigh().setValue(s));
//                XMLUtil.getChildBoolean(xml, "show_lo").ifPresent(s -> symbol.propShowLow().setValue(s));
//
//                //  BOY meters are always opaque.
//                symbol.propTransparent().setValue(false);
//
//            }

            return true;

        }

    }

}
