/*******************************************************************************
 * Copyright (c) 2015-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation.javafx.widgets;

import java.time.Instant;
import java.util.Objects;

import org.csstudio.display.builder.model.DirtyFlag;
import org.csstudio.display.builder.model.UntypedWidgetPropertyListener;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.util.VTypeUtil;
import org.csstudio.display.builder.model.widgets.ScrollBarWidget;
import org.csstudio.javafx.Styles;
import org.diirt.vtype.Display;
import org.diirt.vtype.VType;
import org.diirt.vtype.ValueUtil;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/** Creates JavaFX item for model widget
 *  @author Amanda Carpenter
 */
public class ScrollBarRepresentation extends RegionBaseRepresentation<ScrollBar, ScrollBarWidget>
{
    private final DirtyFlag dirty_size = new DirtyFlag();
    private final DirtyFlag dirty_enablement = new DirtyFlag();
    private final DirtyFlag dirty_value = new DirtyFlag();
    private final UntypedWidgetPropertyListener limitsChangedListener = this::limitsChanged;
    private final UntypedWidgetPropertyListener sizeChangedListener = this::sizeChanged;
    private final WidgetPropertyListener<Boolean> enablementChangedListener = this::enablementChanged;
    private final WidgetPropertyListener<Boolean> orientationChangedListener = this::orientationChanged;
    private final WidgetPropertyListener<VType> valueChangedListener = this::valueChanged;
    private final WidgetPropertyListener<Instant> runtimeConfChangedListener = (p, o, n) -> openConfigurationPanel();

    private volatile double min = 0.0;
    private volatile double max = 100.0;
    private volatile double block = ( max - min ) / 10.0;
    private volatile double step = ( max - min ) / 100.0;
    private volatile boolean active = false; //is updating UI to match PV?
    private volatile boolean isValueChanging = false; //is user interacting with UI (need to suppress UI updates)?
    private volatile boolean enabled = false;

    private Node pressedButton = null;

    @Override
    protected ScrollBar createJFXNode() throws Exception
    {
        final ScrollBar scrollbar = new ScrollBar();
        scrollbar.setOrientation(model_widget.propHorizontal().getValue() ? Orientation.VERTICAL : Orientation.HORIZONTAL);
        scrollbar.setFocusTraversable(true);
        scrollbar.setOnKeyPressed((final KeyEvent event) ->
        {
            switch (event.getCode())
            {
            case DOWN: jfx_node.decrement();
                break;
            case UP: jfx_node.increment();
                break;
            case PAGE_UP:
                //In theory, this may be unsafe; i.e. if max/min are changed
                //after node creation.
                jfx_node.adjustValue(max);
                break;
            case PAGE_DOWN:
                jfx_node.adjustValue(min);
                break;
            default: break;
            }
        });
        if (! toolkit.isEditMode())
        {

            // While context menu is handled by SWT, there is a problem
            // when the primary button is held down to increment/decrement the spinner,
            // and _then_ the secondary button is used to open the context menu.
            // Releasing the primary button will in that case NOT stop the value changes
            // because SWT has the focus.
            // Fix is to trace the pressedButton,
            // and suppress context menu while button is held down,
            // and stopping incrementing/decrementing when exiting the scrollbar.
            scrollbar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {

                // Prevent UI value update while actively changing
                isValueChanging = true;

                if ( e.getButton() == MouseButton.PRIMARY ) {

                    Node node = e.getPickResult().getIntersectedNode();

                    if ( node.getStyleClass().contains("increment-arrow")
                      || node.getStyleClass().contains("decrement-arrow") ) {
                        pressedButton = node;
                    }

                } else if ( e.getButton() == MouseButton.SECONDARY ) {
                    // Disable the contemporary triggering of a value change and of the
                    // opening of contextual menu when right-clicking on the scrollbar's
                    // buttons.
                    e.consume();
                }

            });

            scrollbar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {

                if ( e.getButton() == MouseButton.PRIMARY ) {
                    pressedButton = null;
                }

                // Prevent UI value update while actively changing
                isValueChanging = false;

            });

            scrollbar.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, e -> {

                Node node = e.getPickResult().getIntersectedNode();

                // Contextual menu is forbidden inside the scrollbar's buttons and track,
                // otherwise if a button has focus (i.e. previously clicked), right-clicking
                // on it and, after the context menu id shown, double-clicking outside the
                // the menu and the scrollbar's boundary will close the menu and press
                // the button (probably because SWT remember the JFX cursor's position
                // when the menu was triggered). The same happens on scrollbar's track.
                // This implementation will limit the context menu only inside the thumb.
                if ( !node.getStyleClass().contains("thumb") ) {
                    e.consume();
                }

            });

            scrollbar.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
                if ( pressedButton != null ) {
                    pressedButton.fireEvent(e.copyFor(e.getSource(), e.getTarget(), MouseEvent.MOUSE_RELEASED));
                    pressedButton = null;
                }
            });

        }
        enablementChanged(null, null, null);
        limitsChanged(null, null, null);


        // This code manages layout,
        // because otherwise for example border changes would trigger
        // expensive Node.notifyParentOfBoundsChange() recursing up the scene graph
        scrollbar.setManaged(false);

        return scrollbar;
    }

    @Override
    protected boolean isFilteringEditModeClicks()
    {
        return true;
    }

    @Override
    protected void registerListeners()
    {
        super.registerListeners();
        model_widget.propWidth().addUntypedPropertyListener(sizeChangedListener);
        model_widget.propHeight().addUntypedPropertyListener(sizeChangedListener);
        model_widget.propLimitsFromPV().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propMinimum().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propMaximum().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propBarLength().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propIncrement().addUntypedPropertyListener(limitsChangedListener);
        model_widget.propHorizontal().addPropertyListener(orientationChangedListener);

        model_widget.propEnabled().addPropertyListener(enablementChangedListener);

        //Since both the widget's PV value and the ScrollBar node's value property might be
        //written to independently during runtime, both must be listened to.
        model_widget.runtimePropValue().addPropertyListener(valueChangedListener);
        jfx_node.valueProperty().addListener(this::nodeValueChanged);
        model_widget.runtimePropConfigure().addPropertyListener(runtimeConfChangedListener);
        valueChanged(null, null, null);
    }

    @Override
    protected void unregisterListeners()
    {
        model_widget.propWidth().removePropertyListener(sizeChangedListener);
        model_widget.propHeight().removePropertyListener(sizeChangedListener);
        model_widget.propLimitsFromPV().removePropertyListener(limitsChangedListener);
        model_widget.propMinimum().removePropertyListener(limitsChangedListener);
        model_widget.propMaximum().removePropertyListener(limitsChangedListener);
        model_widget.propBarLength().removePropertyListener(limitsChangedListener);
        model_widget.propIncrement().removePropertyListener(limitsChangedListener);
        model_widget.propHorizontal().removePropertyListener(orientationChangedListener);
        model_widget.propEnabled().removePropertyListener(enablementChangedListener);
        model_widget.runtimePropValue().removePropertyListener(valueChangedListener);
        model_widget.runtimePropConfigure().removePropertyListener(runtimeConfChangedListener);
        super.unregisterListeners();
    }

    private void orientationChanged(final WidgetProperty<Boolean> prop, final Boolean old, final Boolean horizontal)
    {
        // When interactively changing orientation, swap width <-> height.
        // This will only affect interactive changes once the widget is represented on the screen.
        // Initially, when the widget is loaded from XML, the representation
        // doesn't exist and the original width, height and orientation are applied
        // without triggering a swap.
        if (toolkit.isEditMode())
        {
            final int w = model_widget.propWidth().getValue();
            final int h = model_widget.propHeight().getValue();
            model_widget.propWidth().setValue(h);
            model_widget.propHeight().setValue(w);
        }
        sizeChanged(prop, old, horizontal);
    }

    private void sizeChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_size.mark();
        toolkit.scheduleUpdate(this);
    }

    private void enablementChanged(final WidgetProperty<Boolean> property, final Boolean old_value, final Boolean new_value)
    {
        enabled = model_widget.propEnabled().getValue()  &&
                  model_widget.runtimePropPVWritable().getValue();
        dirty_enablement.mark();
        toolkit.scheduleUpdate(this);
    }

    private void limitsChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        if ( updateLimits(model_widget.propLimitsFromPV().getValue()) ) {
            dirty_size.mark();
            toolkit.scheduleUpdate(this);
        }
    }

    private void nodeValueChanged(final ObservableValue<? extends Number> property, final Number old_value, final Number new_value)
    {
        if (active)
            return;
        final Tooltip tip = jfx_node.getTooltip();
        if (model_widget.propShowValueTip().getValue())
        {
            final String text = Objects.toString(new_value);
            if (tip != null)
                tip.setText(text);
            else
                jfx_node.setTooltip(new Tooltip(text));
        }
        else if (tip != null)
            jfx_node.setTooltip(null);
        toolkit.fireWrite(model_widget, new_value);
    }

    private void valueChanged(final WidgetProperty<? extends VType> property, final VType old_value, final VType new_value)
    {

        if ( model_widget.propLimitsFromPV().getValue() ) {
            updateLimits(model_widget.propLimitsFromPV().getValue());
            dirty_size.mark();
        }

        dirty_value.mark();
        toolkit.scheduleUpdate(this);

    }

    /**
     * Updates, if required, the limits and zones.
     *
     * @return {@code true} is something changed and and UI update is required.
     */
    private boolean updateLimits ( boolean limitsFromPV ) {

        boolean somethingChanged = false;

        //  Model's values.
        double min_val = model_widget.propMinimum().getValue();
        double max_val = model_widget.propMaximum().getValue();
        double block_val = model_widget.propBarLength().getValue();
        double step_val = model_widget.propIncrement().getValue();

        if ( limitsFromPV ) {

            // Try to get display range from PV
            final Display display_info = ValueUtil.displayOf(model_widget.runtimePropValue().getValue());

            if ( display_info != null ) {

                min_val = display_info.getLowerDisplayLimit();
                max_val = display_info.getUpperDisplayLimit();

                double delta = ( max_val - min_val );

                block_val = delta / 10.0;
                step_val = delta / 100.0;
            }

        }

        // If invalid limits, fall back to 0..100 range
        if ( min_val >= max_val ) {
            min_val = 0.0;
            max_val = 100.0;
        }

        if ( min != min_val ) {
            min = min_val;
            somethingChanged = true;
        }
        if ( max != max_val ) {
            max = max_val;
            somethingChanged = true;
        }
        if ( block != block_val ) {
            block = block_val;
            somethingChanged = true;
        }
        if ( step != step_val ) {
            step = step_val;
            somethingChanged = true;
        }

        return somethingChanged;

    }

    @Override
    public void updateChanges()
    {
        super.updateChanges();
        if (dirty_enablement.checkAndClear())
        {
            jfx_node.setDisable(! enabled);
            Styles.update(jfx_node, Styles.NOT_ENABLED, !enabled);
        }
        if (dirty_size.checkAndClear())
        {
            jfx_node.resize(model_widget.propWidth().getValue(), model_widget.propHeight().getValue());
            jfx_node.setMin(min);
            jfx_node.setMax(max);
            jfx_node.setOrientation(model_widget.propHorizontal().getValue() ? Orientation.HORIZONTAL : Orientation.VERTICAL);
            jfx_node.setUnitIncrement(step);
            jfx_node.setBlockIncrement(block);
            jfx_node.setVisibleAmount(block);
        }
        if (dirty_value.checkAndClear())
        {
            active = true;
            try
            {
                double newval = VTypeUtil.getValueNumber( model_widget.runtimePropValue().getValue() ).doubleValue();
                if (newval < min) newval = min;
                else if (newval > max) newval = max;
                if (!isValueChanging)
                    jfx_node.setValue(newval);
            }
            finally
            {
                if (active)
                    active = false;
            }
        }
    }

    private SliderConfigPopOver config_popover = null;

    private void openConfigurationPanel()
    {
        if (config_popover == null)
            config_popover = new SliderConfigPopOver(model_widget.propIncrement());
        if (config_popover.isShowing())
            config_popover.hide();
        else
            config_popover.show(jfx_node);
    }
}
