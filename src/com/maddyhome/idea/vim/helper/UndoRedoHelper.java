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
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 * @author oleg
 */
public class UndoRedoHelper {

  public static boolean undo(@NotNull final DataContext context, Editor editor) {
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
    final com.intellij.openapi.command.undo.UndoManager undoManager =
      com.intellij.openapi.command.undo.UndoManager.getInstance(project);
    if (fileEditor != null && undoManager.isUndoAvailable(fileEditor)) {
      Object lastUndoStack1 = getLastUndoStack(editor, undoManager,fileEditor);
      undoManager.undo(fileEditor);
      Object lastUndoStack2 = getLastUndoStack(editor, undoManager, fileEditor);
      if (lastUndoStack1 == lastUndoStack2) {
        //undoManager.undo(fileEditor);
      }
      editor.getCaretModel().moveToOffset(editor.getSelectionModel().getSelectionStart());
      editor.getSelectionModel().removeSelection();
      return true;
    }
    return false;
  }

  private static Object getLastUndoStack(Editor editor, UndoManager undoManager, FileEditor fileEditor) {
    try {
      Method getStackHolder = UndoManagerImpl.class.getDeclaredMethod("getStackHolder",boolean.class);
      getStackHolder.setAccessible(true);
      Object getUndoStacksHolder = getStackHolder.invoke(undoManager, true);
      Method getLastAction = getUndoStacksHolder.getClass().getDeclaredMethod("getLastAction", Collection.class);
      getLastAction.setAccessible(true);
      Method getDocumentReferences = UndoManagerImpl.class.getDeclaredMethod("getDocumentReferences",FileEditor.class);
      getDocumentReferences.setAccessible(true);

      return getLastAction.invoke(getUndoStacksHolder, getDocumentReferences.invoke(editor,fileEditor));
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    catch (InvocationTargetException e) {
    }
    return null;
  }

  public static boolean redo(@NotNull final DataContext context) {
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    final FileEditor fileEditor = PlatformDataKeys.FILE_EDITOR.getData(context);
    final com.intellij.openapi.command.undo.UndoManager undoManager =
      com.intellij.openapi.command.undo.UndoManager.getInstance(project);
    if (fileEditor != null && undoManager.isRedoAvailable(fileEditor)) {
      undoManager.redo(fileEditor);
      return true;
    }
    return false;
  }
}
