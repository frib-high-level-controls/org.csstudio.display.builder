/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.properties;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csstudio.display.builder.model.BaseWidgetPropertyListener;
import org.csstudio.display.builder.model.UntypedWidgetPropertyListener;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.WidgetPropertyListener;

/** Handler for {@link WidgetProperty} changes.
 *
 *  @author Kay Kasemir
 *
 *  @param <T> Type of the property's value
 */
// Property Handling:
//
// Who is tracking listeners?
// 1) Each property tracks its listeners
//    ++ PropertyChangeSupport can simply forward changes to all listeners,
//       no need to check which property changed and which listener to invoke (**)
//    ++ Can be type-safe, because property is typed (**)
//    -- Many properties don't have listeners, but would still use mem for the (empty) PropertyChangeSupport
//    -- Listeners need to subscribe to each property. Cannot listen to "all widget changes"
//
// 2) Widget tracks listeners for all its properties
//    ++ Listeners can subscribe to specific or "all" property changes
//    ++ Fewer PropertyChangeSupport instances (**)
//    -- PropertyChangeSupport needs to dispatch changes to listener based on name of changed property (**)
//    -- Widget needs to know about elements of StructuredWidgetProperty, ArrayWidgetProperty to support
//       subscription and events for "trace[4].x_value" (**)
//
// 3) Mix: Widget tracks listeners for its 'plain' properties,
//    but StructuredWidgetProperty and ArrayWidgetProperty track listeners to their elements
//    ++ Minimizes number of PropertyChangeSupport instances
//    -- Awkward to use (**)
//
// How to do the subscription and notification?
// A) Use PropertyChangeSupport
//    ++ Already there
//    ++ Suppresses updates where old_value.equals(new_value)
//    ++ Supports subscription to "all" or selected property
//    -- Reports all values as Object newValue, not typed
//    -- String lookup of property name
// B) Use javafx.bean.* listener support
//    ++ Typed
//    -- Adds dependency to a specific UI library
// C) Do your own (this class)
//    ++ Typed
//    ++ No dependency
//    ++ Optimized for use case: No string comparisons, check old_value.equals(new_value)
//
// Design decision:
//
// Legacy BOY used 1A, PropertyChangeSupport on each property, flattening array and structure properties
// into "trace_0_x_pv".
// Display.builder started with 2A, PropertyChangeSupport on widget, which caused problems when trying
// to properly support array and structure properties.
// ==> 1, support on each property is best.
//     But based on A, it leaves most PropertyChangeSupport features unused, and is not type safe.
//     C is actually easy to implement for 1, so 1C.

@SuppressWarnings("nls")
public abstract class PropertyChangeHandler<T extends Object>
{
    /** Lazily initialized list of listeners.
     *  Read-only access must make thread safe copy.
     */
    private volatile List<BaseWidgetPropertyListener> listeners = null;

    /** @return Valid listener list, lazily created */
    private synchronized List<BaseWidgetPropertyListener> getListeners()
    {
        if (listeners == null)
            listeners = new CopyOnWriteArrayList<>();
        return listeners;
    }

    /** Subscribe to property changes
     *  @param listener Listener to invoke
     */
    public void addPropertyListener(final WidgetPropertyListener<T> listener)
    {
        getListeners().add(listener);
    }

    /** Subscribe to property changes
     *  @param listener Listener to invoke
     */
    public void addUntypedPropertyListener(final UntypedWidgetPropertyListener listener)
    {
        getListeners().add(listener);
    }

    /** Unsubscribe from property changes
     *  @param listener Listener to remove
     */
    public void removePropertyListener(final BaseWidgetPropertyListener listener)
    {
        getListeners().remove(listener);
    }

    /** Notify listeners of property change.
    *
    *  <p>New value usually matches <code>property.getValue()</code>,
    *  but in multi-threaded context value might already have changed
    *  _again_ by the time this executes.
    *
    *  <p>Suppresses notifications where old_value equals new_value,
    *  unless the values are null, treating that as a "notify anyway"
    *  case.
    *
    *  @param property Property that changed, or <code>null</code> for "many"
    *  @param old_value Original value
    *  @param new_value New value
    */
   @SuppressWarnings("unchecked")
   protected void firePropertyChange(final WidgetProperty<T> property,
                                     final T old_value, final T new_value)
   {
       // Does anybody care?
       final List<BaseWidgetPropertyListener> safe_copy = listeners;
       if (safe_copy == null)
           return;

       // Any change at all?
       if (Objects.equals(old_value, new_value))
           return;

       // Notify listeners
       for (BaseWidgetPropertyListener listener : safe_copy)
       {
           try
           {
               if (listener instanceof WidgetPropertyListener)
                   ((WidgetPropertyListener<T>)listener).propertyChanged(property, old_value, new_value);
               else
                   ((UntypedWidgetPropertyListener)listener).propertyChanged(property, old_value, new_value);
           }
           catch (Throwable ex)
           {   // Log error, then continue with next listener
               Logger.getLogger(getClass().getName())
                     .log(Level.WARNING, "Property update error for " +  property, ex);
           }
       }
   }
}
