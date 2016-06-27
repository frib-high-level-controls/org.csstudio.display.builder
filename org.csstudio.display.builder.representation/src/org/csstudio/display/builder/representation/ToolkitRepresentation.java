/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csstudio.display.builder.model.ChildrenProperty;
import org.csstudio.display.builder.model.DisplayModel;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.WidgetPropertyListener;
import org.csstudio.display.builder.model.properties.ActionInfo;
import org.csstudio.display.builder.model.properties.WidgetColor;

/** Representation for a toolkit.
 *
 *  <p>Each 'window' or related display part that represents
 *  a display model holds one instance of the toolkit.
 *
 *  <p>The toolkit maintains information about the part
 *  and creates toolkit items for model widgets within the part.
 *
 *  <p>Some toolkits (SWT) require a parent for each item,
 *  while others (JavaFX) can create items which are later
 *  assigned to a parent.
 *  This API requires a parent, and created items are
 *  right away assigned to that parent.
 *
 *  @author Kay Kasemir
 *  @param <TWP> Toolkit widget parent class
 *  @param <TW> Toolkit widget base class
 */
@SuppressWarnings("nls")
abstract public class ToolkitRepresentation<TWP extends Object, TW> implements Executor
{
    /** Logger suggested for all representation logging */
    public final static Logger logger = Logger.getLogger(ToolkitRepresentation.class.getName());

    private static boolean initialized = false;

    /** Factories for representations based on widget type.
     *
     *  Holding WidgetRepresentationFactory<TWP, TW>,
     *  but static to prevent duplicates and thus loosing the type info
     */
    private final static Map<String, WidgetRepresentationFactory<?, ?>> factories = new ConcurrentHashMap<>();

    private final boolean edit_mode;

    private final RepresentationUpdateThrottle throttle = new RepresentationUpdateThrottle(this);

    /** Listener list */
    private final List<ToolkitListener> listeners = new CopyOnWriteArrayList<>();

    /** Add/remove representations for child elements as model changes */
    private final WidgetPropertyListener<List<Widget>> container_children_listener = (children, removed, added) ->
    {
        // Move to toolkit thread.
        // May already be on toolkit, for example in drag/drop,
        // but updating the representation 'later' may reduce blocking.
        if (removed != null)
            for (Widget removed_widget : removed)
                execute(() -> disposeWidget(removed_widget));
        if (added != null)
            for (Widget added_widget : added)
            {
                final Optional<Widget> parent = added_widget.getParent();
                if (! parent.isPresent())
                    throw new IllegalStateException("Cannot locate parent widget for " + added_widget);
                final TWP parent_item = parent.get().getUserData(Widget.USER_DATA_TOOLKIT_PARENT);
                execute(() -> representWidget(parent_item, added_widget));
            }
    };

    /** Update background color */
    private WidgetPropertyListener<WidgetColor> back_color_listener = (property, old_color, new_color) ->
    {
        execute(() -> setBackground(new_color));
    };

    /** Constructor
     *  @param edit_mode Edit mode?
     */
    public ToolkitRepresentation(final boolean edit_mode)
    {
        this.edit_mode = edit_mode;
        synchronized (ToolkitRepresentation.class)
        {
            if (! initialized)
            {
                initialize();
                initialized = true;
            }
        }
    }

    /** 'Edit' mode is used by the editor,
     *  while non-edit mode is used by the runtime.
     *
     *  <p>In edit mode, the widget representation may
     *  appear 'passive', not reacting to user input,
     *  and instead showing additional information like
     *  file names used as input for an image
     *  or an outline for an otherwise shapeless widget.
     *
     *  <p>In runtime mode, the widget may react to user input
     *  or at times be invisible.
     *
     *  @return <code>true</code> for edit mode
     */
    public final boolean isEditMode()
    {
        return edit_mode;
    }

    /** Register available representations */
    abstract protected void initialize();

    /** Register the toolkit's representation of a model widget
     *
     *  @param widget_type {@link Widget} type ID
     *  @param factory Factory for creating representation
     */
    protected void register(final String widget_type,
                            final WidgetRepresentationFactory<TWP, TW> factory)
    {
        factories.put(widget_type, factory);
    }

    /** Obtain the toolkit used to represent widgets
     *
     *  @param model {@link DisplayModel}
     *  @return ToolkitRepresentation
     *  @throws NullPointerException if toolkit not set
     */
    public static <TWP, TW> ToolkitRepresentation<TWP, TW> getToolkit(final DisplayModel model) throws NullPointerException
    {
        final ToolkitRepresentation<TWP, TW> toolkit = model.getUserData(DisplayModel.USER_DATA_TOOLKIT);
        return Objects.requireNonNull(toolkit, "Toolkit not set");
    }

    /** Open new top-level window
     *
     *  <p>Is invoked with the _initial_ model.
     *  <code>representModel</code> is then called to create the
     *  individual widget representations.
     *
     *  <p>If the model is replaced, <code>disposeRepresentation</code>
     *  will be called with the current model, and then
     *  <code>representModel</code> with the new model.
     *
     *  @param model {@link DisplayModel} that provides name and initial size
     *  @param close_handler Will be invoked when user closes the window
     *                       with the then active model, i.e. the model
     *                       provided in last call to <code>representModel</code>.
     *                       Should stop runtime, dispose representation.
     *  @return Toolkit parent (Pane, Container, ..)
     *          for representing model items in the newly created window
     *  @throws Exception on error
     */
    abstract public TWP openNewWindow(DisplayModel model, Consumer<DisplayModel> close_handler) throws Exception;

    /** @param color Background color to use for the overall window */
    abstract public void setBackground(WidgetColor color);

    /** Create toolkit widgets for a display model.
     *
     *  @param parent Toolkit parent (Pane, Container, ..)
     *  @param model Display model
     *  @throws Exception on error
     *  @see #disposeRepresentation()
     */
    public void representModel(final TWP parent, final DisplayModel model) throws Exception
    {
        Objects.requireNonNull(parent, "Missing toolkit parent item");

        // Attach toolkit
        model.setUserData(DisplayModel.USER_DATA_TOOLKIT, this);

        setBackground(model.displayBackgroundColor().getValue());

        // DisplayModel itself is _not_ represented,
        // but all its children, recursively
        representChildren(parent, model, model.runtimeChildren());

        logger.log(Level.FINE, "Tracking changes to children of {0}", model);
        model.runtimeChildren().addPropertyListener(container_children_listener);

        // Listen to model background
        model.displayBackgroundColor().addPropertyListener(back_color_listener);
    }

    /** Create representation for each child of a ContainerWidget
     *  @param parent    Toolkit parent (Pane, Container, ..)
     *  @param container Widget that contains children (DisplayModel, GroupWidget, ..)
     *  @param children  The 'children' property of the container
     */
    private void representChildren(final TWP parent, final Widget container, final ChildrenProperty children)
    {
        container.setUserData(Widget.USER_DATA_TOOLKIT_PARENT, parent);

        for (Widget widget : children.getValue())
            representWidget(parent, widget);
    }

    /** Create a toolkit widget for a model widget.
     *
     *  <p>Will log errors, but not raise exception.
     *
     *  @param parent Toolkit parent (Group, Container, ..)
     *  @param widget Model widget to represent
     *  @return Toolkit item that represents the widget
     *  @see #disposeWidget(Object, Widget)
     */
    public void representWidget(final TWP parent, final Widget widget)
    {
        @SuppressWarnings("unchecked")
        final WidgetRepresentationFactory<TWP, TW> factory = (WidgetRepresentationFactory<TWP, TW>) factories.get(widget.getType());
        if (factory == null)
        {
            logger.log(Level.SEVERE, "Lacking representation for " + widget.getType());
            return;
        }

        final TWP re_parent;
        try
        {
            final WidgetRepresentation<TWP, TW, Widget> representation = factory.create();
            representation.initialize(this, widget);
            re_parent = representation.createComponents(parent);
            widget.setUserData(Widget.USER_DATA_REPRESENTATION, representation);
            logger.log(Level.FINE, "Representing {0} as {1}", new Object[] { widget, representation });
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Cannot represent " + widget, ex);
            return;
        }
        // Recurse into child widgets
        final ChildrenProperty children = ChildrenProperty.getChildren(widget);
        if (children != null)
        {
            representChildren(re_parent, widget, children);
            logger.log(Level.FINE, "Tracking changes to children of {0}", widget);
            children.addPropertyListener(container_children_listener);
        }
    }

    /** Remove all the toolkit items of the model
     *  @param model Display model
     *  @return Parent toolkit item (Group, Container, ..) that used to host the model items
     */
    public TWP disposeRepresentation(final DisplayModel model)
    {
        final TWP parent = disposeChildren(model, model.runtimeChildren());
        model.clearUserData(DisplayModel.USER_DATA_TOOLKIT);

        model.displayBackgroundColor().removePropertyListener(back_color_listener);

        logger.log(Level.FINE, "No longer tracking changes to children of {0}", model);
        model.runtimeChildren().removePropertyListener(container_children_listener);

        return Objects.requireNonNull(parent);
    }

    /** Remove toolkit widgets for container
     *  @param container Container which should no longer be represented, recursing into children
     *  @return Parent toolkit item (Group, Container, ..) that used to host the container items
     */
    private TWP disposeChildren(final Widget container, final ChildrenProperty children)
    {
        for (Widget widget : children.getValue())
        {   // First dispose child widgets, then the container
            final ChildrenProperty grandkids = ChildrenProperty.getChildren(widget);
            if (grandkids != null)
                disposeChildren(widget, grandkids);
            disposeWidget(widget);
        }

        return container.clearUserData(Widget.USER_DATA_TOOLKIT_PARENT);
    }

    /** Remove toolkit widget for model widget
     *  @param widget Model widget that should no longer be represented
     */
    public void disposeWidget(final Widget widget)
    {
        final ChildrenProperty children = ChildrenProperty.getChildren(widget);
        if (children != null)
        {
            logger.log(Level.FINE, "No longer tracking changes to children of {0}", widget);
            children.removePropertyListener(container_children_listener);
        }
        final WidgetRepresentation<TWP, TW, ? extends Widget> representation =
            widget.clearUserData(Widget.USER_DATA_REPRESENTATION);
        if (representation != null)
        {
            logger.log(Level.FINE, "Disposing {0} for {1}", new Object[] { representation, widget });
            representation.dispose();
        }
        // else: Widget has no representation because not implemented for this toolkit
    }

    /** Called by toolkit representation to request an update.
     *
     *  <p>That representation's <code>updateChanges()</code> will be called
     *
     *  @param representation Toolkit representation that requests update
     */
    public void scheduleUpdate(final WidgetRepresentation<TWP, TW, ? extends Widget> representation)
    {
        throttle.scheduleUpdate(representation);
    }

    /** Execute command in toolkit's UI thread.
     *  @param command Command to execute
     */
    @Override
    abstract public void execute(final Runnable command);

    /** Show message dialog.
     *
     *  <p>Calling thread is blocked until user closes the dialog
     *  by pressing "OK".
     *
     *  @param is_warning Whether to style dialog as warning or information
     *  @param message Message to display on dialog
     */
    abstract public void showMessageDialog(boolean is_warning, String message);

    /** Show confirmation dialog.
     *
     *  <p>Calling thread is blocked until user closes the dialog
     *  by selecting either "Yes" or "No"
     *  ("Confirm", "Cancel", depending on implementation).
     *
     *  @param mesquestionsage Message to display on dialog
     *  @return <code>true</code> if user selected "Yes" ("Confirm")
     */
    abstract public boolean showConfirmationDialog(String question);

    /** Execute callable in toolkit's UI thread.
     *  @param <T> Type to return
     *  @param callable Callable to execute
     *  @return {@link Future} to wait for completion,
     *          fetch result or learn about exception
     */
    public <T> Future<T> submit(final Callable<T> callable)
    {
        final FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    /** @param listener Listener to add */
    public void addListener(final ToolkitListener listener)
    {
        listeners.add(listener);
    }

    /** @param listener Listener to remove */
    public void removeListener(final ToolkitListener listener)
    {
        listeners.remove(listener);
    }

    /** Notify listeners that action has been invoked
     *  @param widget Widget that invoked the action
     *  @param action Action to perform
     */
    public void fireAction(final Widget widget, final ActionInfo action)
    {
        for (final ToolkitListener listener : listeners)
        {
            try
            {
                listener.handleAction(widget, action);
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Action failure when invoking " + action + " for " + widget, ex);
            }
        }
    }

    /** Notify listeners that a widget has been clicked
     *  @param widget Widget
     *  @param with_control Is 'control' key held?
     */
    public void fireClick(final Widget widget, final boolean with_control)
    {
        for (final ToolkitListener listener : listeners)
        {
            try
            {
                listener.handleClick(widget, with_control);
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Click failure for " + widget, ex);
            }
        }
    }

    /** Notify listeners that context menu has been invoked
     *  @param widget Widget
     */
    public void fireContextMenu(final Widget widget)
    {
        for (final ToolkitListener listener : listeners)
        {
            try
            {
                listener.handleContextMenu(widget);
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Context menu failure for " + widget, ex);
            }
        }
    }

    /** Notify listeners that a widget requests writing a value
     *  @param widget Widget
     *  @param value Value
     */
    public void fireWrite(final Widget widget, final Object value)
    {
        for (final ToolkitListener listener : listeners)
        {
            try
            {
                listener.handleWrite(widget, value);
            }
            catch (final Throwable ex)
            {
                logger.log(Level.WARNING, "Failure when writing " + value + " for " + widget, ex);
            }
        }
    }

    /** Orderly shutdown */
    public void shutdown()
    {
        throttle.shutdown();
    }
}
