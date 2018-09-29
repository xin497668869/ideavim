/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.maddyhome.idea.vim.utils;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Producer;
import com.maddyhome.idea.vim.command.SelectionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class EditorModificationUtil {
  public static final Key<String> READ_ONLY_VIEW_MESSAGE_KEY = Key.create("READ_ONLY_VIEW_MESSAGE_KEY");

  private EditorModificationUtil() {
  }

  public static void deleteSelectedText(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) return;

    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();

    VisualPosition selectionStartPosition = selectionModel.getSelectionStartPosition();
    if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets() && selectionStartPosition != null) {
      editor.getCaretModel().moveToVisualPosition(selectionStartPosition);
    }
    else {
      editor.getCaretModel().moveToOffset(selectionStart);
    }
    selectionModel.removeSelection();
    editor.getDocument().deleteString(selectionStart, selectionEnd);
    scrollToCaret(editor);
  }

  public static void deleteSelectedTextForAllCarets(@NotNull final Editor editor) {
    editor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        deleteSelectedText(editor);
      }
    });
  }


  public static int insertStringAtCaret(Editor editor,
                                        @NotNull String s,
                                        boolean toProcessOverwriteMode,
                                        boolean toMoveCaret,
                                        SelectionType type) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, toMoveCaret, s.length(), type);
  }


  public static int insertStringAtCaret(Editor editor,
                                        @NotNull String s,
                                        boolean toProcessOverwriteMode,
                                        boolean toMoveCaret,
                                        int caretShift,
                                        SelectionType type) {
    return insertStringAtCaretNoScrolling(editor, s, toProcessOverwriteMode, toMoveCaret, caretShift, type);
  }

  private static int insertStringAtCaretNoScrolling(Editor editor,
                                                    @NotNull String s,
                                                    boolean toProcessOverwriteMode,
                                                    boolean toMoveCaret,
                                                    int caretShift,
                                                    SelectionType type) {
    // There is a possible case that particular soft wraps become hard wraps if the caret is located at soft wrap-introduced virtual
    // space, hence, we need to give editor a chance to react accordingly.
    editor.getSoftWrapModel().beforeDocumentChangeAtCaret();
    int oldOffset = editor.getSelectionModel().getSelectionStart();

    String filler = editor.getSelectionModel().hasSelection() ? "" : calcStringToFillVirtualSpace(editor);
    if (filler.length() > 0) {
      s = filler + s;
    }

    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (editor.isInsertMode() || !toProcessOverwriteMode) {
      if (selectionModel.hasSelection()) {
        document.replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), s);
      }
      else {
        document.insertString(oldOffset, s);
      }
    }
    else {
      deleteSelectedText(editor);
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      if (lineNumber >= document.getLineCount()) {
        return insertStringAtCaretNoScrolling(editor, s, false, toMoveCaret, s.length(), type);
      }

      int endOffset = document.getLineEndOffset(lineNumber);
      document.replaceString(oldOffset, Math.min(endOffset, oldOffset + s.length()), s);
    }

    int offset = oldOffset + filler.length() + caretShift;
    if (toMoveCaret) {
      editor.getCaretModel().moveToVisualPosition(editor.offsetToVisualPosition(offset, false, true));
      selectionModel.removeSelection();
    }
    else if (editor.getCaretModel().getOffset() !=
             oldOffset) { // handling the case when caret model tracks document changes
      editor.getCaretModel().moveToOffset(oldOffset);
    }

    return offset;
  }

  @Nullable
  public static Transferable getContentsToPasteToEditor(@Nullable Producer<Transferable> producer) {
    if (producer == null) {
      CopyPasteManager manager = CopyPasteManager.getInstance();
      return manager.areDataFlavorsAvailable(DataFlavor.stringFlavor) ? manager.getContents() : null;
    }
    else {
      return producer.produce();
    }
  }

  /**
   * Calculates difference in columns between current editor caret position and end of the logical line fragment displayed
   * on a current visual line.
   *
   * @param editor target editor
   * @return difference in columns between current editor caret position and end of the logical line fragment displayed
   * on a current visual line
   */
  public static int calcAfterLineEnd(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition logicalPosition = caretModel.getLogicalPosition();
    int lineNumber = logicalPosition.line;
    int columnNumber = logicalPosition.column;
    if (lineNumber >= document.getLineCount()) {
      return columnNumber;
    }

    int caretOffset = caretModel.getOffset();
    int anchorLineEndOffset = document.getLineEndOffset(lineNumber);
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logicalPosition.line);
    for (SoftWrap softWrap : softWraps) {
      if (!editor.getSoftWrapModel().isVisible(softWrap)) {
        continue;
      }

      int softWrapOffset = softWrap.getStart();
      if (softWrapOffset == caretOffset) {
        // There are two possible situations:
        //     *) caret is located on a visual line before soft wrap-introduced line feed;
        //     *) caret is located on a visual line after soft wrap-introduced line feed;
        VisualPosition position = editor.offsetToVisualPosition(caretOffset - 1);
        VisualPosition visualCaret = caretModel.getVisualPosition();
        if (position.line == visualCaret.line) {
          return visualCaret.column - position.column - 1;
        }
      }
      if (softWrapOffset > caretOffset) {
        anchorLineEndOffset = softWrapOffset;
        break;
      }

      // Same offset corresponds to all soft wrap-introduced symbols, however, current method should behave differently in
      // situations when the caret is located just before the soft wrap and at the next visual line.
      if (softWrapOffset == caretOffset) {
        boolean visuallyBeforeSoftWrap =
          caretModel.getVisualPosition().line < editor.offsetToVisualPosition(caretOffset).line;
        if (visuallyBeforeSoftWrap) {
          anchorLineEndOffset = softWrapOffset;
          break;
        }
      }
    }

    int lineEndColumnNumber = editor.offsetToLogicalPosition(anchorLineEndOffset).column;
    return columnNumber - lineEndColumnNumber;
  }

  public static String calcStringToFillVirtualSpace(Editor editor) {
    int afterLineEnd = calcAfterLineEnd(editor);
    if (afterLineEnd > 0) {
      return calcStringToFillVirtualSpace(editor, afterLineEnd);
    }

    return "";
  }

  public static String calcStringToFillVirtualSpace(Editor editor, int afterLineEnd) {
    final Project project = editor.getProject();
    StringBuilder buf = new StringBuilder();
    final Document doc = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    boolean atLineStart =
      caretOffset >= doc.getTextLength() || doc.getLineStartOffset(doc.getLineNumber(caretOffset)) == caretOffset;
    if (atLineStart && project != null) {
      int offset = editor.getCaretModel().getOffset();
      PsiDocumentManager.getInstance(project).commitDocument(doc); // Sync document and PSI before formatting.
      String properIndent =
        offset >= doc.getTextLength() ? "" : CodeStyleFacade.getInstance(project).getLineIndent(doc, offset);
      if (properIndent != null) {
        int tabSize = editor.getSettings().getTabSize(project);
        for (int i = 0; i < properIndent.length(); i++) {
          if (properIndent.charAt(i) == ' ') {
            afterLineEnd--;
          }
          else if (properIndent.charAt(i) == '\t') {
            if (afterLineEnd < tabSize) {
              break;
            }
            afterLineEnd -= tabSize;
          }
          buf.append(properIndent.charAt(i));
          if (afterLineEnd == 0) break;
        }
      }
    }

    for (int i = 0; i < afterLineEnd; i++) {
      buf.append(' ');
    }

    return buf.toString();
  }


  public static void moveAllCaretsRelatively(@NotNull Editor editor, final int caretShift) {
    editor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        caret.moveToOffset(caret.getOffset() + caretShift);
      }
    });
  }

  public static void moveCaretRelatively(@NotNull Editor editor, final int caretShift) {
    CaretModel caretModel = editor.getCaretModel();
    caretModel.moveToOffset(caretModel.getOffset() + caretShift);
  }

  /**
   * This method is safe to run both in and out of {@link CaretModel#runForEachCaret(CaretAction)} context.
   * It scrolls to primary caret in both cases, and, in the former case, avoids performing excessive scrolling in case of large number
   * of carets.
   */
  public static void scrollToCaret(@NotNull Editor editor) {
    if (editor.getCaretModel().getCurrentCaret() == editor.getCaretModel().getPrimaryCaret()) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  @NotNull
  public static List<CaretState> calcBlockSelectionState(@NotNull Editor editor,
                                                         @NotNull LogicalPosition blockStart,
                                                         @NotNull LogicalPosition blockEnd) {
    int startLine = Math.max(Math.min(blockStart.line, editor.getDocument().getLineCount() - 1), 0);
    int endLine = Math.max(Math.min(blockEnd.line, editor.getDocument().getLineCount() - 1), 0);
    int step = endLine < startLine ? -1 : 1;
    int count = 1 + Math.abs(endLine - startLine);
    List<CaretState> caretStates = new LinkedList<>();
    boolean hasSelection = false;
    for (int line = startLine, i = 0; i < count; i++, line += step) {
      int startColumn = blockStart.column;
      int endColumn = blockEnd.column;
      int lineEndOffset = editor.getDocument().getLineEndOffset(line);
      LogicalPosition lineEndPosition = editor.offsetToLogicalPosition(lineEndOffset);
      int lineWidth = lineEndPosition.column;
      if (startColumn > lineWidth && endColumn > lineWidth && !editor.isColumnMode()) {
        LogicalPosition caretPos = new LogicalPosition(line, Math.min(startColumn, endColumn));
        caretStates.add(new CaretState(caretPos, lineEndPosition, lineEndPosition));
      }
      else {
        LogicalPosition startPos =
          new LogicalPosition(line, editor.isColumnMode() ? startColumn : Math.min(startColumn, lineWidth));
        LogicalPosition endPos =
          new LogicalPosition(line, editor.isColumnMode() ? endColumn : Math.min(endColumn, lineWidth));
        int startOffset = editor.logicalPositionToOffset(startPos);
        int endOffset = editor.logicalPositionToOffset(endPos);
        caretStates.add(new CaretState(endPos, startPos, endPos));
        hasSelection |= startOffset != endOffset;
      }
    }
    if (hasSelection && !editor.isColumnMode()) { // filtering out lines without selection
      Iterator<CaretState> caretStateIterator = caretStates.iterator();
      while (caretStateIterator.hasNext()) {
        CaretState state = caretStateIterator.next();
        //noinspection ConstantConditions
        if (state.getSelectionStart().equals(state.getSelectionEnd())) {
          caretStateIterator.remove();
        }
      }
    }
    return caretStates;
  }

  public static boolean requestWriting(@NotNull Editor editor) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
      HintManager.getInstance().showInformationHint(editor, EditorBundle.message("editing.read.only.file.hint"));
      return false;
    }
    return true;
  }

  /**
   * @return true when not viewer
   * false otherwise, additionally information hint with warning would be shown
   */
  public static boolean checkModificationAllowed(Editor editor) {
    if (!editor.isViewer()) return true;
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || editor instanceof TextComponentEditor) {
      return false;
    }

    String data = READ_ONLY_VIEW_MESSAGE_KEY.get(editor);
    HintManager.getInstance()
      .showInformationHint(editor, data == null ? EditorBundle.message("editing.viewer.hint") : data);
    return false;
  }
}
