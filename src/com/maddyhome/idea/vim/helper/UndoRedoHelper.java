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

import com.intellij.openapi.actionSystem.CommonDataKeys;
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
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.action.change.insert.VimUndoableAction;
import com.maddyhome.idea.vim.command.CommandState;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author oleg
 */
public class UndoRedoHelper {
    private static List<String> commandNames = Arrays.asList("Vim Insert After Cursor", "Vim Insert Before Cursor");

    public static boolean undo(@NotNull final DataContext context, Editor editor) {
        final Project project = PlatformDataKeys.PROJECT.getData(context);
        final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
        final com.intellij.openapi.command.undo.UndoManager undoManager =
                com.intellij.openapi.command.undo.UndoManager.getInstance(project);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {

                if (fileEditor != null && undoManager.isUndoAvailable(fileEditor)) {
                    while (Boolean.TRUE.equals(isTargetStack(editor, undoManager, fileEditor, true, false))) {
                        int stackSize = getStackSize(editor, undoManager, fileEditor, true);
                        undoManager.undo(fileEditor);
                        if (stackSize > getStackSize(editor, undoManager, fileEditor, true) + 1) {
                            break;
                        }
                    }
                    while (Boolean.FALSE.equals(isTargetStack(editor, undoManager, fileEditor, true, false))) {
                        int stackSize = getStackSize(editor, undoManager, fileEditor, true);
                        undoManager.undo(fileEditor);
                        if (stackSize > getStackSize(editor, undoManager, fileEditor, true) + 1) {
                            break;
                        }
                    }
//                    editor.getSelectionModel().removeSelection();
                    if (CommandState.getInstance(editor).getMode() == CommandState.Mode.VISUAL) {
                        VimPlugin.getMotion().exitVisual(editor);
                    }
                }
            }
        });
        return true;
    }


    public static boolean redo(@NotNull final DataContext context) {
        final Project project = PlatformDataKeys.PROJECT.getData(context);
        final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
        Editor editorData = PlatformDataKeys.EDITOR.getData(context);
        final com.intellij.openapi.command.undo.UndoManager undoManager =
                com.intellij.openapi.command.undo.UndoManager.getInstance(project);
        while (Boolean.TRUE.equals(isTargetStack(editorData, undoManager, fileEditor, false, true))) {
            int stackSize = getStackSize(editorData, undoManager, fileEditor, false);
            undoManager.redo(fileEditor);
            if (stackSize > getStackSize(editorData, undoManager, fileEditor, false) + 1) {
                break;
            }
        }
        if (fileEditor != null && undoManager.isRedoAvailable(fileEditor)) {
            while (Boolean.FALSE.equals(isTargetStack(editorData, undoManager, fileEditor, false, true))) {
                int stackSize = getStackSize(editorData, undoManager, fileEditor, false);
                undoManager.redo(fileEditor);
                if (stackSize > getStackSize(editorData, undoManager, fileEditor, false) + 1) {
                    break;
                }
            }
            Editor editor = context.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                if (CommandState.getInstance(editor).getMode() == CommandState.Mode.VISUAL) {
                    VimPlugin.getMotion().exitVisual(editor);
                }
            }
            return true;
        }

        Editor editor = context.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            if (CommandState.getInstance(editor).getMode() == CommandState.Mode.VISUAL) {
                VimPlugin.getMotion().exitVisual(editor);
            }
        }
        return false;
    }

    private static int getStackSize(Editor editor, UndoManager undoManager, FileEditor fileEditor, boolean isUndo) {
        try {
            Method getStackHolder = UndoManagerImpl.class.getDeclaredMethod("getStackHolder", boolean.class);
            getStackHolder.setAccessible(true);
            Object getUndoStacksHolder = getStackHolder.invoke(undoManager, isUndo);
            Method getStack = getUndoStacksHolder.getClass().getDeclaredMethod("getStack", DocumentReference.class);
            getStack.setAccessible(true);
            DocumentReference documentReference = DocumentReferenceManager.getInstance().create(editor.getDocument());

            LinkedList invoke = (LinkedList) getStack.invoke(getUndoStacksHolder, documentReference);
            return invoke.size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static Boolean isTargetStack(Editor editor, UndoManager undoManager, FileEditor fileEditor, boolean isUndo, boolean finishMark) {
        try {
            Method getStackHolder = UndoManagerImpl.class.getDeclaredMethod("getStackHolder", boolean.class);
            getStackHolder.setAccessible(true);
            Object getUndoStacksHolder = getStackHolder.invoke(undoManager, isUndo);
            Method getLastAction = getUndoStacksHolder.getClass().getDeclaredMethod("getLastAction", Collection.class);
            getLastAction.setAccessible(true);
            Method getDocumentReferences = UndoManagerImpl.class.getDeclaredMethod("getDocumentReferences", FileEditor.class);
            getDocumentReferences.setAccessible(true);

            Object invoke = getLastAction.invoke(getUndoStacksHolder, getDocumentReferences.invoke(editor, fileEditor));
            if (invoke == null) {
                return false;
            }
            Method getActions = invoke.getClass().getMethod("getActions");
            getActions.setAccessible(true);

            List<UndoableAction> invoke1 = (List<UndoableAction>) getActions.invoke(invoke);
            if (invoke1 == null || invoke1.isEmpty()) {
                return false;
            }
            if (finishMark) {
                for (UndoableAction undoableAction : invoke1) {
                    if (undoableAction instanceof FinishMarkAction || undoableAction instanceof StartMarkAction) {
                        return true;
                    }
                }
            }
            return invoke1.get(0) instanceof VimUndoableAction;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {

        }
        return null;
    }
}
