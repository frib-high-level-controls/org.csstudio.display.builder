/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * Copyright (C) 2016 European Spallation Source ERIC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.csstudio.display.builder.representation.javafx.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.csstudio.display.builder.model.DirtyFlag;
import org.csstudio.display.builder.model.UntypedWidgetPropertyListener;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.persist.NamedWidgetColors;
import org.csstudio.display.builder.model.persist.WidgetColorService;
import org.csstudio.display.builder.model.util.FormatOptionHandler;
import org.csstudio.display.builder.model.util.VTypeUtil;
import org.csstudio.display.builder.model.widgets.BaseGaugeWidget;
import org.csstudio.display.builder.representation.javafx.JFXUtil;
import org.csstudio.javafx.Styles;
import org.diirt.vtype.Display;
import org.diirt.vtype.VType;
import org.diirt.vtype.ValueUtil;

import eu.hansolo.medusa.Gauge;
import eu.hansolo.medusa.GaugeBuilder;
import eu.hansolo.medusa.Section;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * @author claudiorosati, European Spallation Source ERIC
 * @version 1.0.0 8 Feb 2017
 */
public abstract class BaseGaugeRepresentation<W extends BaseGaugeWidget> extends RegionBaseRepresentation<Gauge, W> {

    protected static final Color ALARM_MAJOR_COLOR = JFXUtil.convert(WidgetColorService.getColor(NamedWidgetColors.ALARM_MAJOR));
    protected static final Color ALARM_MINOR_COLOR = JFXUtil.convert(WidgetColorService.getColor(NamedWidgetColors.ALARM_MINOR));

    private final DirtyFlag     dirtyContent  = new DirtyFlag();
    private final DirtyFlag     dirtyGeometry = new DirtyFlag();
    private final DirtyFlag     dirtyLimits   = new DirtyFlag();
    private final DirtyFlag     dirtyLook     = new DirtyFlag();
    private final DirtyFlag     dirtyStyle    = new DirtyFlag();
    private final DirtyFlag     dirtyUnit     = new DirtyFlag();
    private final DirtyFlag     dirtyValue    = new DirtyFlag();
    private volatile double     high          = Double.NaN;
    private volatile double     hihi          = Double.NaN;
    private volatile double     lolo          = Double.NaN;
    private volatile double     low           = Double.NaN;
    private volatile double     max           = 100.0;
    private volatile double     min           = 0.0;
    private final AtomicBoolean updatingValue = new AtomicBoolean(false);

    private final UntypedWidgetPropertyListener contentChangedListener  = this::contentChanged;
    private final UntypedWidgetPropertyListener geometryChangedListener = this::geometryChanged;
    private final UntypedWidgetPropertyListener limitsChangedListener   = this::limitsChanged;
    private final UntypedWidgetPropertyListener lookChangedListener     = this::lookChanged;
    private final UntypedWidgetPropertyListener styleChangedListener    = this::styleChanged;
    private final UntypedWidgetPropertyListener unitChangedListener     = this::unitChanged;
    private final WidgetPropertyListener<VType> valueChangedListener    = this::valueChanged;

    @SuppressWarnings( "unchecked" )
    @Override
    public void updateChanges ( ) {

        if ( jfx_node == null ) {
            return;
        }

        super.updateChanges();

        Object value;

        if ( dirtyGeometry.checkAndClear() ) {

            value = model_widget.propVisible().getValue();

            if ( !Objects.equals(value, jfx_node.isVisible()) ) {
                jfx_node.setVisible((boolean) value);
            }

            jfx_node.setLayoutX(model_widget.propX().getValue());
            jfx_node.setLayoutY(model_widget.propY().getValue());
            jfx_node.setPrefWidth(model_widget.propWidth().getValue());
            jfx_node.setPrefHeight(model_widget.propHeight().getValue());

            clipper.setWidth(model_widget.propWidth().getValue() + 10);
            clipper.setHeight(model_widget.propHeight().getValue() + 10);

        }

        if ( dirtyLook.checkAndClear() ) {

            value = model_widget.propAutoScale().getValue();

            if ( !Objects.equals(value, jfx_node.isAutoScale()) ) {
                jfx_node.setAutoScale((boolean) value);
            }

            Color bgColor = JFXUtil.convert(model_widget.propBackgroundColor().getValue());

            if ( model_widget.propTransparent().getValue() ) {
                bgColor = bgColor.deriveColor(0, 1, 1, 0);
            }

            if ( !Objects.equals(bgColor, jfx_node.getBackgroundPaint()) ) {
                jfx_node.setBackgroundPaint(bgColor);
            }

            value = JFXUtil.convert(model_widget.propForegroundColor().getValue());

            if ( !Objects.equals(value, jfx_node.getTitleColor()) ) {

                Color fgColor = (Color) value;

                jfx_node.setMajorTickMarkColor(fgColor);
                jfx_node.setMediumTickMarkColor(fgColor);
                jfx_node.setMinorTickMarkColor(fgColor);
                jfx_node.setTickLabelColor(fgColor);
                jfx_node.setTickMarkColor(fgColor);
                jfx_node.setTitleColor(fgColor);
                jfx_node.setUnitColor(fgColor);
                jfx_node.setValueColor(fgColor);
                jfx_node.setZeroColor(fgColor);

            }

            value = model_widget.propMajorTickSpace().getValue();

            if ( !Objects.equals(value, jfx_node.getMajorTickSpace()) ) {
                jfx_node.setMajorTickSpace((double) value);
            }

            value = model_widget.propMinorTickSpace().getValue();

            if ( !Objects.equals(value, jfx_node.getMinorTickSpace()) ) {
                jfx_node.setMinorTickSpace((double) value);
            }

            value = model_widget.propTitle().getValue();

            if ( !Objects.equals(value, jfx_node.getTitle()) ) {
                jfx_node.setTitle((String) value);
            }

            value = model_widget.propValueVisible().getValue();

            if ( !Objects.equals(value, jfx_node.isValueVisible()) ) {
                jfx_node.setValueVisible((boolean) value);
            }

        }

        if ( dirtyContent.checkAndClear() ) {

            value = FormatOptionHandler.actualPrecision(model_widget.runtimePropValue().getValue(), model_widget.propPrecision().getValue());

            if ( !Objects.equals(value, jfx_node.getDecimals()) ) {
                jfx_node.setDecimals((int) value);
            }

        }

        if ( dirtyLimits.checkAndClear() ) {

            if ( !Objects.equals(max, jfx_node.getMaxValue()) ) {
                jfx_node.setMaxValue(max);
            }

            if ( !Objects.equals(min, jfx_node.getMinValue()) ) {
                jfx_node.setMinValue(min);
            }

            value = areZonesVisible();

            if ( !Objects.equals(value, jfx_node.getSectionsVisible()) ) {
                jfx_node.setSectionsVisible((boolean) value);
            }

            value = createZones();

            if ( !Objects.equals(value, jfx_node.getSections()) ) {
                jfx_node.setSections((List<Section>) value);
            }

        }

        if ( dirtyUnit.checkAndClear() ) {

            value = getUnit();

            if ( !Objects.equals(value, jfx_node.getUnit()) ) {
                jfx_node.setUnit((String) value);
            }

        }

        if ( dirtyStyle.checkAndClear() ) {
            Styles.update(jfx_node, Styles.NOT_ENABLED, !model_widget.propEnabled().getValue());
        }

        if ( dirtyValue.checkAndClear() && updatingValue.compareAndSet(false, true) ) {
            try {

                final VType vtype = model_widget.runtimePropValue().getValue();
                double newval = VTypeUtil.getValueNumber(vtype).doubleValue();

                if ( !Double.isNaN(newval) ) {

                    if ( newval < min ) {
                        newval = min;
                    } else if ( newval > max ) {
                        newval = max;
                    }

                    jfx_node.setValue(newval);

                } else {
//  TODO: CR: do something!!!
                }

            } finally {
                updatingValue.set(false);
            }
        }

    }

    protected final boolean areZonesVisible ( ) {
        return model_widget.propShowLoLo().getValue()
            || model_widget.propShowLow().getValue()
            || model_widget.propShowHigh().getValue()
            || model_widget.propShowHiHi().getValue();
    }

    /**
     * Change the skin type, resetting some of the gauge parameters.
     *
     * @param skinType The new skin to be set.
     */
    protected void changeSkin ( final Gauge.SkinType skinType ) {

        jfx_node.setSkinType(skinType);

        jfx_node.setPrefWidth(model_widget.propWidth().getValue());
        jfx_node.setPrefHeight(model_widget.propHeight().getValue());

        Color fgColor = JFXUtil.convert(model_widget.propForegroundColor().getValue());

        jfx_node.setAnimated(false);
        jfx_node.setAutoScale(model_widget.propAutoScale().getValue());
        jfx_node.setBackgroundPaint(model_widget.propTransparent().getValue() ? Color.TRANSPARENT : JFXUtil.convert(model_widget.propBackgroundColor().getValue()));
        jfx_node.setCheckAreasForValue(false);
        jfx_node.setCheckSectionsForValue(false);
        jfx_node.setCheckThreshold(false);
        jfx_node.setDecimals(FormatOptionHandler.actualPrecision(model_widget.runtimePropValue().getValue(), model_widget.propPrecision().getValue()));
        jfx_node.setHighlightAreas(false);
        jfx_node.setInnerShadowEnabled(false);
        jfx_node.setInteractive(false);
        jfx_node.setLedVisible(false);
        jfx_node.setMajorTickMarkColor(fgColor);
        jfx_node.setMajorTickSpace(model_widget.propMajorTickSpace().getValue());
        jfx_node.setMediumTickMarkColor(fgColor);
        jfx_node.setMinorTickMarkColor(fgColor);
        jfx_node.setMinorTickSpace(model_widget.propMinorTickSpace().getValue());
        jfx_node.setReturnToZero(false);
        jfx_node.setSectionIconsVisible(false);
        jfx_node.setSectionTextVisible(false);
        jfx_node.setTickLabelColor(fgColor);
        jfx_node.setTickMarkColor(fgColor);
        jfx_node.setTitle(model_widget.propTitle().getValue());
        jfx_node.setTitleColor(fgColor);
        jfx_node.setUnit(model_widget.propUnit().getValue());
        jfx_node.setUnitColor(fgColor);
        jfx_node.setValueColor(fgColor);
        jfx_node.setValueVisible(model_widget.propValueVisible().getValue());
        jfx_node.setZeroColor(fgColor);

    }

    @Override
    protected Gauge createJFXNode ( ) throws Exception {

        updateLimits(false);

        Gauge gauge = GaugeBuilder.create().skinType(getSkin()).build();

        gauge.setPrefHeight(model_widget.propHeight().getValue());
        gauge.setPrefWidth(model_widget.propWidth().getValue());
        //--------------------------------------------------------
        //  Previous properties must be set first.
        //--------------------------------------------------------
        gauge.setAnimated(false);
        gauge.setCheckAreasForValue(false);
        gauge.setCheckSectionsForValue(false);
        gauge.setCheckThreshold(false);
        gauge.setHighlightAreas(false);
        gauge.setInnerShadowEnabled(false);
        gauge.setInteractive(false);
        gauge.setLedVisible(false);
        gauge.setReturnToZero(false);
        gauge.setSectionIconsVisible(false);
        gauge.setSectionTextVisible(false);

        dirtyContent.mark();
        dirtyGeometry.mark();
        dirtyLimits.mark();
        dirtyLook.mark();
        dirtyStyle.mark();
        dirtyUnit.mark();
        dirtyValue.mark();

        toolkit.schedule( ( ) -> {
            if ( jfx_node != null ) {
                changeSkin(getSkin());
            }
            lookChanged(null, null, null);
        }, 77 + (long) ( 34.0 * Math.random() ), TimeUnit.MILLISECONDS);


        clipper.setWidth(model_widget.propWidth().getValue() + 10);
        clipper.setHeight(model_widget.propHeight().getValue() + 10);
        gauge.setClip(clipper);


        //  Terminal classes must call
        //toolkit.schedule( ( ) -> {
        //if ( jfx_node != null ) {
        //    //  The next 2 lines necessary because of a Medusa problem.
        //    jfx_node.setAutoScale(!jfx_node.isAutoScale());
        //    jfx_node.setAutoScale(!jfx_node.isAutoScale());
        //}
        //    valueChanged(null, null, null);
        //}, 77 + (long) ( 34.0 * Math.random() ), TimeUnit.MILLISECONDS);

        return gauge;

    }

    final private Rectangle clipper = new Rectangle(-5, -5, 10, 10);

    /**
     * Creates a new zone with the given parameters.
     *
     * @param start The zone's starting value.
     * @param end   The zone's ending value.
     * @param name  The zone's name.
     * @param color The zone's color.
     * @return A {@link Section} representing the created zone.
     */
    protected Section createZone ( double start, double end, String name, Color color ) {
        return new Section(start, end, name, color);
    }

    /**
     * Creates the zones.
     *
     * @return An array of {@link Section}s.
     */
    protected final List<Section> createZones ( ) {

        boolean loloNaN = Double.isNaN(lolo);
        boolean hihiNaN = Double.isNaN(hihi);
        List<Section> sections = new ArrayList<>(4);

        if ( !loloNaN ) {
            sections.add(createZone(min, lolo, "LoLo", ALARM_MAJOR_COLOR));
        }

        if ( !Double.isNaN(low) ) {
            sections.add(createZone(loloNaN ? min : lolo, low, "Low", ALARM_MINOR_COLOR));
        }

        if ( !Double.isNaN(high) ) {
            sections.add(createZone(high, hihiNaN ? max : hihi, "High", ALARM_MINOR_COLOR));
        }

        if ( !hihiNaN ) {
            sections.add(createZone(hihi, max, "HiHi", ALARM_MAJOR_COLOR));
        }

        return sections;

    }

    protected abstract Gauge.SkinType getSkin();

    /**
     * @return The unit string to be displayed.
     */
    protected String getUnit ( ) {

        //  Model's values.
        String newUnit = model_widget.propUnit().getValue();

        if ( model_widget.propUnitFromPV().getValue() ) {

            //  Try to get engineering unit from PV.
            final Display display_info = ValueUtil.displayOf(model_widget.runtimePropValue().getValue());

            if ( display_info != null ) {
                newUnit = display_info.getUnits();
            }

        }

        return newUnit;

    }

    @Override
    protected void registerListeners ( ) {

        super.registerListeners();

        model_widget.propPrecision().addUntypedPropertyListener(contentChangedListener);
        model_widget.propPVName().addUntypedPropertyListener(contentChangedListener);

        model_widget.propVisible().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propX().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propY().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propWidth().addUntypedPropertyListener(geometryChangedListener);
        model_widget.propHeight().addUntypedPropertyListener(geometryChangedListener);

        model_widget.propAutoScale().addUntypedPropertyListener(lookChangedListener);
        model_widget.propBackgroundColor().addUntypedPropertyListener(lookChangedListener);
        model_widget.propForegroundColor().addUntypedPropertyListener(lookChangedListener);
        model_widget.propMajorTickSpace().addUntypedPropertyListener(lookChangedListener);
        model_widget.propMinorTickSpace().addUntypedPropertyListener(lookChangedListener);
        model_widget.propTitle().addUntypedPropertyListener(lookChangedListener);
        model_widget.propTransparent().addUntypedPropertyListener(lookChangedListener);
        model_widget.propValueVisible().addUntypedPropertyListener(lookChangedListener);

        model_widget.propLevelHiHi().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propLevelHigh().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propLevelLoLo().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propLevelLow().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propLimitsFromPV().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propShowHiHi().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propShowHigh().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propShowLoLo().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propShowLow().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propMaximum().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propMinimum().addUntypedPropertyListener(limitsChangedListener);

        model_widget.propUnit().addUntypedPropertyListener(unitChangedListener);
        model_widget.propUnitFromPV().addUntypedPropertyListener(unitChangedListener);

        model_widget.propEnabled().addUntypedPropertyListener(styleChangedListener);

        if ( toolkit.isEditMode() ) {
            dirtyValue.checkAndClear();
        } else {
            model_widget.runtimePropValue().addPropertyListener(valueChangedListener);
            valueChanged(null, null, null);
        }

    }

    @Override
    protected void unregisterListeners ( ) {

        model_widget.propPrecision().removePropertyListener(contentChangedListener);
        model_widget.propPVName().removePropertyListener(contentChangedListener);

        model_widget.propVisible().removePropertyListener(geometryChangedListener);
        model_widget.propX().removePropertyListener(geometryChangedListener);
        model_widget.propY().removePropertyListener(geometryChangedListener);
        model_widget.propWidth().removePropertyListener(geometryChangedListener);
        model_widget.propHeight().removePropertyListener(geometryChangedListener);

        model_widget.propAutoScale().removePropertyListener(lookChangedListener);
        model_widget.propBackgroundColor().removePropertyListener(lookChangedListener);
        model_widget.propForegroundColor().removePropertyListener(lookChangedListener);
        model_widget.propMajorTickSpace().removePropertyListener(lookChangedListener);
        model_widget.propMinorTickSpace().removePropertyListener(lookChangedListener);
        model_widget.propTitle().removePropertyListener(lookChangedListener);
        model_widget.propTransparent().removePropertyListener(lookChangedListener);
        model_widget.propValueVisible().removePropertyListener(lookChangedListener);

        model_widget.propLevelHiHi().removePropertyListener(limitsChangedListener);
        model_widget.propLevelHigh().removePropertyListener(limitsChangedListener);
        model_widget.propLevelLoLo().removePropertyListener(limitsChangedListener);
        model_widget.propLevelLow().removePropertyListener(limitsChangedListener);
        model_widget.propLimitsFromPV().removePropertyListener(limitsChangedListener);
        model_widget.propShowHiHi().removePropertyListener(limitsChangedListener);
        model_widget.propShowHigh().removePropertyListener(limitsChangedListener);
        model_widget.propShowLoLo().removePropertyListener(limitsChangedListener);
        model_widget.propShowLow().removePropertyListener(limitsChangedListener);
        model_widget.propMaximum().removePropertyListener(limitsChangedListener);
        model_widget.propMinimum().removePropertyListener(limitsChangedListener);

        model_widget.propUnit().removePropertyListener(unitChangedListener);
        model_widget.propUnitFromPV().removePropertyListener(unitChangedListener);

        model_widget.propEnabled().removePropertyListener(styleChangedListener);

        if ( !toolkit.isEditMode() ) {
            model_widget.runtimePropValue().removePropertyListener(valueChangedListener);
        }

        super.unregisterListeners();

    }

    /**
     * Updates, if required, the limits and zones.
     *
     * @return {@code true} is something changed and and UI update is required.
     */
    protected boolean updateLimits ( boolean limitsFromPV ) {

        boolean somethingChanged = false;

        //  Model's values.
        double newMin = model_widget.propMinimum().getValue();
        double newMax = model_widget.propMaximum().getValue();

        //  If invalid limits, fall back to 0..100 range.
        if ( !Double.isFinite(newMin) || !Double.isFinite(newMax) || newMin > newMax ) {
            newMin = 0.0;
            newMax = 100.0;
        }

        double newLoLo = model_widget.propLevelLoLo().getValue();
        double newLow = model_widget.propLevelLow().getValue();
        double newHigh = model_widget.propLevelHigh().getValue();
        double newHiHi = model_widget.propLevelHiHi().getValue();

        if ( limitsFromPV ) {

            //  Try to get display range from PV.
            final Display display_info = ValueUtil.displayOf(model_widget.runtimePropValue().getValue());

            if ( display_info != null ) {

                double infoMin = display_info.getLowerCtrlLimit();
                double infoMax = display_info.getUpperCtrlLimit();

                if ( Double.isFinite(infoMin) && Double.isFinite(infoMax) && infoMin < infoMax ) {
                    newMin = infoMin;
                    newMax = infoMax;
                }

                newLoLo = display_info.getLowerAlarmLimit();
                newLow = display_info.getLowerWarningLimit();
                newHigh = display_info.getUpperWarningLimit();
                newHiHi = display_info.getUpperAlarmLimit();

            }

        }

        if ( !model_widget.propShowLoLo().getValue() ) {
            newLoLo = Double.NaN;
        }
        if ( !model_widget.propShowLow().getValue() ) {
            newLow = Double.NaN;
        }
        if ( !model_widget.propShowHigh().getValue() ) {
            newHigh = Double.NaN;
        }
        if ( !model_widget.propShowHiHi().getValue() ) {
            newHiHi = Double.NaN;
        }

        if ( min != newMin ) {
            min = newMin;
            somethingChanged = true;
        }
        if ( max != newMax ) {
            max = newMax;
            somethingChanged = true;
        }
        if ( lolo != newLoLo ) {
            lolo = newLoLo;
            somethingChanged = true;
        }
        if ( low != newLow ) {
            low = newLow;
            somethingChanged = true;
        }
        if ( high != newHigh) {
            high = newHigh;
            somethingChanged = true;
        }
        if ( hihi != newHiHi ) {
            hihi = newHiHi;
            somethingChanged = true;
        }

        return somethingChanged;

    }

    protected final void valueChanged ( final WidgetProperty<? extends VType> property, final VType old_value, final VType new_value ) {

        if ( model_widget.propLimitsFromPV().getValue() ) {
            updateLimits(model_widget.propLimitsFromPV().getValue());
            dirtyLimits.mark();
        }

        if ( model_widget.propPrecision().getValue() == -1 ) {
            dirtyContent.mark();
        }

        if ( model_widget.propUnitFromPV().getValue() ) {
            dirtyUnit.mark();
        }

        dirtyValue.mark();
        toolkit.scheduleUpdate(this);

    }

    private void contentChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        dirtyContent.mark();
        toolkit.scheduleUpdate(this);
    }

    private void geometryChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        dirtyGeometry.mark();
        toolkit.scheduleUpdate(this);
    }

    private void limitsChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        if ( updateLimits(model_widget.propLimitsFromPV().getValue()) ) {
            dirtyLimits.mark();
            toolkit.scheduleUpdate(this);
        }
    }

    private void lookChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        dirtyLook.mark();
        toolkit.scheduleUpdate(this);
    }

    private void styleChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        dirtyStyle.mark();
        toolkit.scheduleUpdate(this);
    }

    private void unitChanged ( final WidgetProperty<?> property, final Object old_value, final Object new_value ) {
        dirtyUnit.mark();
        toolkit.scheduleUpdate(this);
    }

}
