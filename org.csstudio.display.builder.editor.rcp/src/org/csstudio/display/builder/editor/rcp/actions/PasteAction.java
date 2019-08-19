/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.editor.rcp.actions;

import java.util.Random;

import org.csstudio.display.builder.editor.DisplayEditor;
import org.csstudio.display.builder.editor.rcp.Messages;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;

/** Action to paste from clipboard
 *  @author Kay Kasemir
 */
public class PasteAction extends RetargetActionHandler
{
    private final Control editor_control;

    /** @param editor_control SWT parent of the editor,
     *                        used to check if the mouse pointer is still
     *                        in screen region of editor
     *  @param editor         {@link DisplayEditor}
     */
    public PasteAction(final Control editor_control, final DisplayEditor editor)
    {
        super(editor, ActionFactory.PASTE.getCommandId());
        setText(Messages.Paste);
        setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_TOOL_PASTE));
        this.editor_control = editor_control;
    }

    @Override
    public void run()
    {
        // Determine where to place the pasted content
        final Point paste_point;
        final Control active_control = Display.getCurrent().getCursorControl();
        if (active_control == editor_control)
        {   // Paste where the mouse is
            final Point mouse_location = Display.getCurrent().getCursorLocation();
            paste_point = active_control.toControl(mouse_location);
        }
        else
        {   // Mouse is outside of target editor.
            // Place somewhat left of center, with some randomness
            // so that 'paste', 'paste', ... of same content
            // doesn't end up exactly on top of each other
            final Point size = editor_control.getSize();
            final Random random = new Random();
            paste_point = new Point(size.x / 3 + random.nextInt(50),
                                    size.y / 2 + random.nextInt(50));
        }
        editor.pasteFromClipboard(paste_point.x, paste_point.y);
    }
}
