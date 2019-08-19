/*******************************************************************************
 * Copyright (c) 2015-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.runtime;

import static org.csstudio.display.builder.runtime.RuntimePlugin.logger;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import org.csstudio.display.builder.model.DisplayModel;
import org.csstudio.display.builder.model.Widget;
import org.csstudio.display.builder.model.macros.MacroHandler;
import org.csstudio.display.builder.model.macros.MacroValueProvider;
import org.csstudio.display.builder.model.macros.Macros;
import org.csstudio.display.builder.model.persist.ModelLoader;
import org.csstudio.display.builder.model.properties.ActionInfo;
import org.csstudio.display.builder.model.properties.ExecuteCommandActionInfo;
import org.csstudio.display.builder.model.properties.ExecuteScriptActionInfo;
import org.csstudio.display.builder.model.properties.OpenDisplayActionInfo;
import org.csstudio.display.builder.model.properties.OpenFileActionInfo;
import org.csstudio.display.builder.model.properties.OpenWebpageActionInfo;
import org.csstudio.display.builder.model.properties.WritePVActionInfo;
import org.csstudio.display.builder.model.util.ModelResourceUtil;
import org.csstudio.display.builder.representation.ToolkitRepresentation;
import org.csstudio.display.builder.runtime.script.ScriptUtil;

/** Action Helper
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class ActionUtil
{
    /** Handler for opening a (display_path, macros) in the BOY runtime.
     *
     *  <p>Support is installed by org.csstudio.display.builder.rcp.Plugin
     *  and only available when BOY is included in product.
     */
    public static BiConsumer<String, String> openBOYRuntime = null;

    /** Partial file names that should be opened in BOY Runtime */
    private static final List<String> use_boy = Preferences.getPartialNamesForUsingBOY();

    /** Handle an action
     *  @param source_widget Widget from which the action is invoked.
     *  @param action Information about the action to perform
     */
    public static void handleAction(final Widget source_widget, final ActionInfo action)
    {
        // Move off the UI Thread
        if (action instanceof OpenDisplayActionInfo)
            RuntimeUtil.getExecutor().execute(() -> openDisplay(source_widget, (OpenDisplayActionInfo) action));
        else if (action instanceof WritePVActionInfo)
            RuntimeUtil.getExecutor().execute(() -> writePV(source_widget, (WritePVActionInfo) action));
        else if (action instanceof ExecuteScriptActionInfo)
            RuntimeUtil.getExecutor().execute(() -> executeScript(source_widget, (ExecuteScriptActionInfo) action));
        else if (action instanceof ExecuteCommandActionInfo)
            RuntimeUtil.getExecutor().execute(() -> executeCommand(source_widget, (ExecuteCommandActionInfo) action));
        else if (action instanceof OpenFileActionInfo)
            RuntimeUtil.getExecutor().execute(() -> openFile(source_widget, (OpenFileActionInfo) action));
        else if (action instanceof OpenWebpageActionInfo)
            RuntimeUtil.getExecutor().execute(() -> openWebpage(source_widget, (OpenWebpageActionInfo) action));
        else
            logger.log(Level.SEVERE, "Cannot handle unknown " + action);
    }

    /** @param expanded_path Display path
     *  @return Should this display use BOY?
     */
    private static boolean shouldUseBOY(final String expanded_path)
    {
        if (! expanded_path.endsWith(".opi"))
            return false;
        for (String partial : use_boy)
            if (expanded_path.contains(partial))
                return true;
        return false;
    }

    /** Open a display
     *
     *  <p>Depending on the target of the action,
     *  this will open a new display or replace
     *  an existing display
     *
     *  @param source_widget Widget from which the action is invoked
     *                       Used to resolve the potentially relative path of the
     *                       display specified in the action
     *  @param action        Information on which display to open and how
     */
    private static void openDisplay(final Widget source_widget,
                                    final OpenDisplayActionInfo action)
    {
        if (action.getFile().isEmpty())
        {
            logger.log(Level.WARNING, "Action without file: {0}", action);
            return;
        }
        try
        {
            // Path to resolve, after expanding macros of source widget and action
            final Macros expanded = new Macros(action.getMacros());
            expanded.expandValues(source_widget.getEffectiveMacros());
            final Macros macros = Macros.merge(source_widget.getEffectiveMacros(), expanded);
            final String expanded_path = MacroHandler.replace(macros, action.getFile());
            logger.log(Level.FINER, "{0}, effective macros {1} ({2})", new Object[] { action, macros, expanded_path });

            // Resolve new display file relative to the source widget model (not 'top'!)
            final DisplayModel widget_model = source_widget.getDisplayModel();
            final String parent_file = widget_model.getUserData(DisplayModel.USER_DATA_INPUT_FILE);

            // Should this display opened in BOY runtime?
            // Resolve the path.
            // If there is a *.bob version, this will resolve to the display builder after all
            final String resolved_path = ModelResourceUtil.resolveResource(parent_file, expanded_path);
            if (shouldUseBOY(resolved_path))
            {
                if (openBOYRuntime == null)
                    throw new Exception("No support for opening BOY runtime");
                final StringBuilder macro_spec = new StringBuilder();
                for (String name : macros.getNames())
                {
                    if (macro_spec.length() > 0)
                        macro_spec.append(',');
                    macro_spec.append(name).append('=').append(macros.getValue(name));
                }
                openBOYRuntime.accept(resolved_path, macro_spec.toString());
                return;
            }

            // Load new model. If that fails, no reason to continue.
            final DisplayModel new_model = ModelLoader.resolveAndLoadModel(parent_file, expanded_path);

            // Model is standalone; source_widget (Action button, ..) is _not_ the parent,
            // but it does add macros to those already defined in the display file.
            final Macros combined_macros = Macros.merge(macros, new_model.propMacros().getValue());
            new_model.propMacros().setValue(combined_macros);

            // Schedule representation on UI thread...
            final DisplayModel top_model = source_widget.getTopDisplayModel();
            final ToolkitRepresentation<Object, Object> toolkit = ToolkitRepresentation.getToolkit(top_model);
            final Future<Object> wait_for_ui;
            if (action.getTarget() == OpenDisplayActionInfo.Target.TAB)
            {
                wait_for_ui = toolkit.submit(() ->
                {   // Create new panel
                    final ToolkitRepresentation<Object, Object> new_toolkit =
                        toolkit.openPanel(new_model, ActionUtil::handleClose);
                    RuntimeUtil.hookRepresentationListener(new_toolkit);
                    return null;
                });
            }
            else if (action.getTarget() == OpenDisplayActionInfo.Target.WINDOW)
            {
                wait_for_ui = toolkit.submit(() ->
                {   // Create new top-level window
                    final ToolkitRepresentation<Object, Object> new_toolkit =
                        toolkit.openNewWindow(new_model, ActionUtil::handleClose);
                    RuntimeUtil.hookRepresentationListener(new_toolkit);
                    return null;
                });
            }
            else if (action.getTarget() == OpenDisplayActionInfo.Target.STANDALONE)
            {
                wait_for_ui = toolkit.submit(() ->
                {
                    final ToolkitRepresentation<Object, Object> new_toolkit =
                        toolkit.openStandaloneWindow(new_model, ActionUtil::handleClose);
                    RuntimeUtil.hookRepresentationListener(new_toolkit);
                    return null;
                });
            }
            else
            {   // Default to OpenDisplayActionInfo.Target.REPLACE
                // Stop old runtime.
                RuntimeUtil.stopRuntime(top_model);
                wait_for_ui = toolkit.submit(() ->
                {   // Close old representation
                    final Object parent = toolkit.disposeRepresentation(top_model);
                    // Tell toolkit about new model to represent
                    toolkit.representModel(parent, new_model);
                    return null;
                });
            }
            wait_for_ui.get();
            // Back in background thread, create new runtime
            RuntimeUtil.startRuntime(new_model);
        }
        catch (final Exception ex)
        {
            logger.log(Level.WARNING, "Error handling " + action, ex);
            ScriptUtil.showErrorDialog(source_widget, "Cannot open " + action.getFile() + ".\n\nSee log for details.");
        }
    }

    /** Passed to newly opened windows to handle runtime shutdown
     *  when window is closed
     *  @param model Model for which runtime needs to be closed
     */
    public static void handleClose(final DisplayModel model)
    {
        final ToolkitRepresentation<Object, Object> toolkit = ToolkitRepresentation.getToolkit(model);

        // Called on UI thread
        // Stop runtime in background thread
        RuntimeUtil.getExecutor().submit(() ->
        {
            RuntimeUtil.stopRuntime(model);

            // After runtime stopped and is no longer accessing the model,
            // dispose representation.
            toolkit.execute(() ->
            {
                toolkit.disposeRepresentation(model);
                
                // Finally, dispose model
                model.dispose();
            });
        });        
    }

    /** Write a PV
     *  @param source_widget Widget from which the action is invoked
     *  @param action        What to write to which PV
     */
    private static void writePV(final Widget source_widget, final WritePVActionInfo action)
    {
        final WidgetRuntime<Widget> runtime = RuntimeUtil.getRuntime(source_widget);
        // System.out.println(action.getDescription() + ": Set " + action.getPV() + " = " + action.getValue());
        final MacroValueProvider macros = source_widget.getMacrosOrProperties();
        String pv_name = action.getPV(), value = action.getValue();
        try
        {
            pv_name = MacroHandler.replace(macros, pv_name);
        }
        catch (Exception ignore)
        {
            // NOP
        }
        try
        {
            value = MacroHandler.replace(macros, value);
        }
        catch (Exception ignore)
        {
            // NOP
        }
        try
        {
            runtime.writePV(pv_name,value);
        }
        catch (final Exception ex)
        {
            final String message = "Cannot write " + pv_name + " = " + value;
            logger.log(Level.WARNING, message, ex);
            ScriptUtil.showErrorDialog(source_widget, message + ".\n\nSee log for details.");
        }
    }

    /** Execute script
     *  @param source_widget Widget from which the action is invoked
     *  @param action        Script action to execute
     */
    private static void executeScript(final Widget source_widget, final ExecuteScriptActionInfo action)
    {
        final WidgetRuntime<Widget> runtime = RuntimeUtil.getRuntime(source_widget);
        try
        {
            runtime.executeScriptAction(action);
        }
        catch (final Throwable ex)
        {
            logger.log(Level.WARNING, action + " failed", ex);
            ScriptUtil.showErrorDialog(source_widget, "Cannot execute " + action.getInfo().getPath() + ".\n\nSee log for details.");
        }
    }

    /** Execute external command
     *  @param source_widget Widget from which the action is invoked
     *  @param action        Command action to execute
     */
    private static void executeCommand(final Widget source_widget, final ExecuteCommandActionInfo action)
    {
        try
        {
            // Path to resolve, after expanding macros
            final Macros macros = source_widget.getEffectiveMacros();
            final String command = MacroHandler.replace(macros, action.getCommand());
            logger.log(Level.FINER, "{0}, effective macros {1} ({2})", new Object[] { action, macros, command });

            // Resolve command relative to the source widget model (not 'top'!)
            final DisplayModel widget_model = source_widget.getDisplayModel();
            final String parent_file = widget_model.getUserData(DisplayModel.USER_DATA_INPUT_FILE);
            final String parent_dir = ModelResourceUtil.getDirectory(ModelResourceUtil.getLocalPath(parent_file));

            // Execute (this is already running on background thread)
            logger.log(Level.FINE, "Executing command {0} in {1}", new Object[] { command, parent_dir });
            final CommandExecutor executor = new CommandExecutor(command, new File(parent_dir));
            executor.call();
        }
        catch (final Throwable ex)
        {
            logger.log(Level.WARNING, action + " failed", ex);
            ScriptUtil.showErrorDialog(source_widget, "Cannot execute " + action + ".\n\nSee log for details.");
        }
    }

    /** Open a file
     *  @param source_widget Widget from which the action is invoked.
     *                       Used to resolve the potentially relative path of the
     *                       file specified in the action
     *  @param action        Information on which file to open
     */
    private static void openFile(final Widget source_widget, final OpenFileActionInfo action)
    {
        if (action.getFile().isEmpty())
        {
            logger.log(Level.WARNING, "Action without file: {0}", action);
            return;
        }
        try
        {
            final String resolved_name = resolve(source_widget, action.getFile());
            final DisplayModel top_model = source_widget.getTopDisplayModel();
            final ToolkitRepresentation<Object, Object> toolkit = ToolkitRepresentation.getToolkit(top_model);
            toolkit.execute(() ->
            {
                try
                {
                    toolkit.openFile(resolved_name);
                }
                catch (Exception ex)
                {
                    logger.log(Level.WARNING, "Cannot open " + action, ex);
                    toolkit.showErrorDialog(source_widget, "Cannot open " + resolved_name);
                }
            });
        }
        catch (final Exception ex)
        {
            logger.log(Level.WARNING, "Error handling " + action, ex);
            ScriptUtil.showErrorDialog(source_widget, "Cannot open " + action.getFile() + ".\n\nSee log for details.");
        }
    }

    /** Open a web page
     *  @param source_widget Widget from which the action is invoked.
     *                       Used to resolve the potentially relative path of the
     *                       file specified in the action
     *  @param action        Information on which URL to open
     */
    private static void openWebpage(final Widget source_widget, final OpenWebpageActionInfo action)
    {
        if (action.getURL().isEmpty())
        {
            logger.log(Level.WARNING, "Action without URL: {0}", action);
            return;
        }
        try
        {
            final String resolved_name = resolve(source_widget, action.getURL());
            final DisplayModel top_model = source_widget.getTopDisplayModel();
            final ToolkitRepresentation<Object, Object> toolkit = ToolkitRepresentation.getToolkit(top_model);
            toolkit.execute(() ->
            {
                try
                {
                    toolkit.openWebBrowser(resolved_name);
                }
                catch (Exception ex)
                {
                    logger.log(Level.WARNING, "Cannot open " + action, ex);
                    toolkit.showErrorDialog(source_widget, "Cannot open " + resolved_name);
                }
            });
        }
        catch (final Exception ex)
        {
            logger.log(Level.WARNING, "Error handling " + action, ex);
            ScriptUtil.showErrorDialog(source_widget, "Cannot open " + action.getURL() + ".\n\nSee log for details.");
        }
    }

    private static String resolve(final Widget source_widget, final String path) throws Exception
    {
        // Path to resolve, after expanding macros
        final Macros macros = source_widget.getEffectiveMacros();
        final String expanded_path = MacroHandler.replace(macros, path);

        // Resolve file relative to the source widget model (not 'top'!)
        final DisplayModel widget_model = source_widget.getDisplayModel();
        final String parent_file = widget_model.getUserData(DisplayModel.USER_DATA_INPUT_FILE);
        return ModelResourceUtil.resolveResource(parent_file, expanded_path);
    }
}
