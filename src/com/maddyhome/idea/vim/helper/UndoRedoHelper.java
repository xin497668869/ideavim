/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2016 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.helper;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.action.change.insert.VimUndoableAction;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.extension.VimExtensionFacade;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.maddyhome.idea.vim.helper.StringHelper.parseKeys;

/**
 * @author oleg
 */
public class UndoRedoHelper {

    public static boolean undo(@NotNull final DataContext context, Editor editor) {
        final Project project = PlatformDataKeys.PROJECT.getData(context);
        final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
        final com.intellij.openapi.command.undo.UndoManager undoManager = project == null ? UndoManager.getGlobalInstance() : UndoManager.getInstance(project);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {

                if (fileEditor != null && undoManager.isUndoAvailable(fileEditor)) {
                    UndoActionType actionType = getUndoActionType(undoManager, fileEditor, true, false);
                    while (UndoActionType.VimAction.equals(actionType)) {
                        undoManager.undo(fileEditor);
                        actionType = getUndoActionType(undoManager, fileEditor, true, false);
                    }
                    action(true, undoManager, fileEditor, editor);
                    if (CommandState.getInstance(editor).getMode() != CommandState.Mode.COMMAND) {
                        VimExtensionFacade.executeNormal(parseKeys("<Esc>"), editor);
                    }

                }
            }
        });
        return true;
    }

    public static void action(boolean isUndo, UndoManager undoManager, FileEditor fileEditor, Editor editor) {
        UndoActionType undoActionType = getUndoActionType(undoManager, fileEditor, isUndo, !isUndo);
        switch (undoActionType) {
            case NoAction:
                return;
            case VimAction:
                return;
            case CommandAction:
                if (isUndo) {
                    undoManager.undo(fileEditor);
                } else {
                    undoManager.redo(fileEditor);
                }
                return;
            case TypeAction:
                int stackSize = getStackSize(editor, undoManager, isUndo);
                if (isUndo) {
                    undoManager.undo(fileEditor);
                } else {
                    undoManager.redo(fileEditor);
                }
                if (stackSize > getStackSize(editor, undoManager, isUndo) + 1) {
                    break;
                }
                action(isUndo, undoManager, fileEditor, editor);
                return;
        }
    }

    public static boolean redo(@NotNull final DataContext context) {
        final Project project = PlatformDataKeys.PROJECT.getData(context);
        final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
        Editor editor = PlatformDataKeys.EDITOR.getData(context);
        final com.intellij.openapi.command.undo.UndoManager undoManager =
                com.intellij.openapi.command.undo.UndoManager.getInstance(project);
        if (fileEditor != null && undoManager.isRedoAvailable(fileEditor)) {

            UndoActionType actionType = getUndoActionType(undoManager, fileEditor, false, true);
            while (UndoActionType.VimAction.equals(actionType)) {
                undoManager.redo(fileEditor);
                actionType = getUndoActionType(undoManager, fileEditor, false, true);
            }
            action(false, undoManager, fileEditor, editor);
            if (CommandState.getInstance(editor).getMode() != CommandState.Mode.COMMAND) {
                VimExtensionFacade.executeNormal(parseKeys("<Esc>"), editor);
            }
        }
        return true;

    }

    private static int getStackSize(Editor editor, UndoManager undoManager, boolean isUndo) {
        try {

            Object getUndoStacksHolder = UndoUtils.invokeMethod(UndoManagerImpl.class
                    , "getStackHolder"
                    , Collections.singletonList(boolean.class)
                    , undoManager
                    , isUndo);


            LinkedList getStack = (LinkedList) UndoUtils.invokeMethod(getUndoStacksHolder.getClass()
                    , "getStack"
                    , Collections.singletonList(DocumentReference.class)
                    , getUndoStacksHolder
                    , DocumentReferenceManager.getInstance().create(editor.getDocument()));

            return getStack.size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static UndoActionType getUndoActionType(UndoManager undoManager, FileEditor fileEditor, boolean isUndo, boolean finishMark) {
        Object getUndoStacksHolder = UndoUtils.invokeMethod(UndoManagerImpl.class
                , "getStackHolder"
                , Collections.singletonList(boolean.class)
                , undoManager
                , isUndo);

        Object getDocumentReferences = UndoUtils.invokeMethod(UndoManagerImpl.class
                , "getDocumentReferences"
                , Collections.singletonList(FileEditor.class)
                , undoManager
                , fileEditor);


        Object invoke = UndoUtils.invokeMethod(getUndoStacksHolder.getClass()
                , "getLastAction"
                , Collections.singletonList(Collection.class)
                , getUndoStacksHolder
                , getDocumentReferences);


        if (invoke == null) {
            return UndoActionType.NoAction;
        }

        String commandName = (String) UndoUtils.invokeMethod(invoke.getClass()
                , "getCommandName"
                , Collections.emptyList()
                , invoke
        );

        if (!commandName.equals("Typing")
                && !commandName.equals("Vim Backspace")
                && !commandName.equals("Choose Lookup Item")
                && StringUtils.isNotEmpty(commandName)
        ) {
            return UndoActionType.CommandAction;
        }


        List<UndoableAction> undoableActions = (List<UndoableAction>) UndoUtils.invokeMethod(invoke.getClass()
                , "getActions"
                , Collections.emptyList()
                , invoke
        );
        if (undoableActions == null || undoableActions.isEmpty()) {
            return UndoActionType.VimAction;
        }
        if (finishMark) {
            for (UndoableAction undoableAction : undoableActions) {
                if (undoableAction instanceof FinishMarkAction || undoableAction instanceof StartMarkAction) {
                    return UndoActionType.CommandAction;
                }
            }
        }
        return undoableActions.get(0) instanceof VimUndoableAction ? UndoActionType.VimAction : UndoActionType.TypeAction;

    }

    public enum UndoActionType {
        NoAction,
        VimAction,
        CommandAction,
        TypeAction
    }


}
