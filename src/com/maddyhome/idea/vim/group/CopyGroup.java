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

package com.maddyhome.idea.vim.group;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.codeInsight.editorActions.TextBlockTransferable;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.command.Argument;
import com.maddyhome.idea.vim.command.Command;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.command.SelectionType;
import com.maddyhome.idea.vim.common.Register;
import com.maddyhome.idea.vim.common.TextRange;
import com.maddyhome.idea.vim.helper.EditorHelper;
import com.maddyhome.idea.vim.utils.EditorModificationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * This group works with command associated with copying and pasting text
 */
public class CopyGroup {

  /**
   * Creates the group
   */
  public CopyGroup() {
  }

  /**
   * This yanks the text moved over by the motion command argument.
   *
   * @param editor   The editor to yank from
   * @param context  The data context
   * @param count    The number of times to yank
   * @param rawCount The actual count entered by the user
   * @param argument The motion command argument
   * @return true if able to yank the text, false if not
   */
  public boolean yankMotion(@NotNull Editor editor,
                            DataContext context,
                            int count,
                            int rawCount,
                            @NotNull Argument argument) {
    TextRange range = MotionGroup.getMotionRange(editor, context, count, rawCount, argument, true);
    final Command motion = argument.getMotion();
    return motion != null && yankRange(editor, range, SelectionType.fromCommandFlags(motion.getFlags()), true);
  }

  /**
   * This yanks count lines of text
   *
   * @param editor The editor to yank from
   * @param count  The number of lines to yank
   * @return true if able to yank the lines, false if not
   */
  public boolean yankLine(@NotNull Editor editor, int count) {
    int start = VimPlugin.getMotion().moveCaretToLineStart(editor);
    int offset = Math.min(VimPlugin.getMotion().moveCaretToLineEndOffset(editor, count - 1, true) + 1,
                          EditorHelper.getFileSize(editor));
    return offset != -1 && yankRange(editor, new TextRange(start, offset), SelectionType.LINE_WISE, false);
  }

  /**
   * This yanks a range of text
   *
   * @param editor The editor to yank from
   * @param range  The range of text to yank
   * @param type   The type of yank
   * @return true if able to yank the range, false if not
   */
  public boolean yankRange(@NotNull Editor editor,
                           @Nullable TextRange range,
                           @NotNull SelectionType type,
                           boolean moveCursor) {
    if (range != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("yanking range: " + range);
      }
      boolean res = VimPlugin.getRegister().storeText(editor, range, type, false);
      if (moveCursor) {
        MotionGroup.moveCaret(editor, range.normalize().getStartOffset());
      }

      return res;
    }

    return false;
  }

  /**
   * Pastes text from the last register into the editor before the current cursor location.
   *
   * @param editor  The editor to paste into
   * @param context The data context
   * @param count   The number of times to perform the paste
   * @return true if able to paste, false if not
   */
  public boolean putTextBeforeCursor(@NotNull Editor editor,
                                     @NotNull DataContext context,
                                     int count,
                                     boolean indent,
                                     boolean cursorAfter) {
    // What register are we getting the text from?
    Register reg = VimPlugin.getRegister().getLastRegister();
    if (reg != null) {
      if (reg.getType() == SelectionType.LINE_WISE && editor.isOneLineMode()) {
        return false;
      }

      int pos;
      // If a linewise put the text is inserted before the current line.
      if (reg.getType() == SelectionType.LINE_WISE) {
        pos = VimPlugin.getMotion().moveCaretToLineStart(editor);
      }
      else {
        pos = editor.getCaretModel().getOffset();
      }

      putText(editor, context, pos, StringUtil.notNullize(reg.getText()), reg.getType(), count, indent, cursorAfter,
              CommandState.SubMode.NONE, reg);

      return true;
    }

    return false;
  }

  /**
   * Pastes text from the last register into the editor after the current cursor location.
   *
   * @param editor  The editor to paste into
   * @param context The data context
   * @param count   The number of times to perform the paste
   * @return true if able to paste, false if not
   */
  public boolean putTextAfterCursor(@NotNull Editor editor,
                                    @NotNull DataContext context,
                                    int count,
                                    boolean indent,
                                    boolean cursorAfter) {
    Register reg = VimPlugin.getRegister().getLastRegister();
    if (reg != null) {
      if (reg.getType() == SelectionType.LINE_WISE && editor.isOneLineMode()) {
        return false;
      }

      int pos;
      // If a linewise paste, the text is inserted after the current line.
      if (reg.getType() == SelectionType.LINE_WISE) {
        pos = Math.min(editor.getDocument().getTextLength(), VimPlugin.getMotion().moveCaretToLineEnd(editor) + 1);
        if (pos > 0 &&
            pos == editor.getDocument().getTextLength() &&
            editor.getDocument().getCharsSequence().charAt(pos - 1) != '\n') {
          editor.getDocument().insertString(pos, "\n");
          pos++;
        }
      }
      else {
        pos = editor.getCaretModel().getOffset();
        //if (!EditorHelper.isLineEmpty(editor, editor.getCaretModel().getLogicalPosition().line, false)) {
        //  pos++;
        //}
      }
      // In case when text is empty this can occur
      if (pos > 0 && pos > editor.getDocument().getTextLength()) {
        pos--;
      }
      putText(editor, context, pos, StringUtil.notNullize(reg.getText()), reg.getType(), count, indent, cursorAfter,
              CommandState.SubMode.NONE, reg);

      return true;
    }

    return false;
  }

  public boolean putVisualRange(@NotNull Editor editor,
                                @NotNull DataContext context,
                                @NotNull TextRange range,
                                int count,
                                boolean indent,
                                boolean cursorAfter) {
    CommandState.SubMode subMode = CommandState.getInstance(editor).getSubMode();
    Register reg = VimPlugin.getRegister().getLastRegister();
    // Without this reset, the deleted text goes into the same register we just pasted from.
    VimPlugin.getRegister().resetRegister();
    if (reg != null) {
      final SelectionType type = reg.getType();
      if (type == SelectionType.LINE_WISE && editor.isOneLineMode()) {
        return false;
      }

      int start = range.getStartOffset();
      int end = range.getEndOffset();
      int endLine = editor.offsetToLogicalPosition(end).line;
      if (LOG.isDebugEnabled()) {
        LOG.debug("start=" + start);
        LOG.debug("end=" + end);
      }

      if (subMode == CommandState.SubMode.VISUAL_LINE) {
        range =
          new TextRange(range.getStartOffset(), Math.min(range.getEndOffset() + 1, EditorHelper.getFileSize(editor)));
      }

      VimPlugin.getChange().deleteRange(editor, range, SelectionType.fromSubMode(subMode), false);

      editor.getCaretModel().moveToOffset(start);

      int pos = start;
      if (type == SelectionType.LINE_WISE) {
        if (subMode == CommandState.SubMode.VISUAL_BLOCK) {
          pos = editor.getDocument().getLineEndOffset(endLine) + 1;
        }
        else if (subMode != CommandState.SubMode.VISUAL_LINE) {
          editor.getDocument().insertString(start, "\n");
          pos = start + 1;
        }
      }
      else if (type != SelectionType.CHARACTER_WISE) {
        if (subMode == CommandState.SubMode.VISUAL_LINE) {
          editor.getDocument().insertString(start, "\n");
        }
      }

      putText(editor, context, pos, StringUtil.notNullize(reg.getText()), type, count,
              indent && type == SelectionType.LINE_WISE, cursorAfter, subMode, reg);

      return true;
    }

    return false;
  }

  /**
   * This performs the actual insert of the paste
   *
   * @param editor      The editor to paste into
   * @param context     The data context
   * @param offset      The location within the file to paste the text
   * @param text        The text to paste
   * @param type        The type of paste
   * @param count       The number of times to paste the text
   * @param indent      True if pasted lines should be autoindented, false if not
   * @param cursorAfter If true move cursor to just after pasted text
   * @param mode        The type of hightlight prior to the put.
   * @param reg
   */
  public void putText(@NotNull Editor editor,
                      @NotNull DataContext context,
                      int offset,
                      @NotNull String text,
                      @NotNull SelectionType type,
                      int count,
                      boolean indent,
                      boolean cursorAfter,
                      @NotNull CommandState.SubMode mode,
                      Register reg) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset=" + offset);
      LOG.debug("type=" + type);
      LOG.debug("mode=" + mode);
    }

    if (mode == CommandState.SubMode.VISUAL_LINE && editor.isOneLineMode()) {
      return;
    }

    // Don't indent if this there isn't anything about a linewise selection or register
    if (indent && type != SelectionType.LINE_WISE && mode != CommandState.SubMode.VISUAL_LINE) {
      indent = false;
    }

    if (type == SelectionType.LINE_WISE && text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
      text = text + '\n';
    }

    int insertCnt = 0;
    int endOffset = offset;
    if (type != SelectionType.BLOCK_WISE) {
      for (int i = 0; i < count; i++) {
        if (reg.getTransferable() != null) {
          paste(editor, reg.getType(), reg.getTransferable(), indent, cursorAfter, offset);
          return;
        }
        insertCnt += text.length();
        endOffset += text.length();
      }
    }
    else {
      LogicalPosition start = editor.offsetToLogicalPosition(offset);
      int col = mode == CommandState.SubMode.VISUAL_LINE ? 0 : start.column;
      int line = start.line;
      if (LOG.isDebugEnabled()) {
        LOG.debug("col=" + col + ", line=" + line);
      }
      int lines = 1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
          lines++;
        }
      }

      if (line + lines >= EditorHelper.getLineCount(editor)) {
        for (int i = 0; i < line + lines - EditorHelper.getLineCount(editor); i++) {
          VimPlugin.getChange().insertText(editor, EditorHelper.getFileSize(editor, true), "\n");
          insertCnt++;
        }
      }

      StringTokenizer parser = new StringTokenizer(text, "\n");
      int maxlen = 0;
      while (parser.hasMoreTokens()) {
        String segment = parser.nextToken();
        maxlen = Math.max(maxlen, segment.length());
      }

      parser = new StringTokenizer(text, "\n");
      while (parser.hasMoreTokens()) {
        String segment = parser.nextToken();
        String origSegment = segment;
        if (segment.length() < maxlen) {
          LOG.debug("short line");
          StringBuilder extra = new StringBuilder(maxlen - segment.length());
          for (int i = segment.length(); i < maxlen; i++) {
            extra.append(' ');
          }
          segment = segment + extra.toString();
          if (col != 0 && col < EditorHelper.getLineLength(editor, line)) {
            origSegment = segment;
          }
        }
        String pad = EditorHelper.pad(editor, line, col);

        int insoff = editor.logicalPositionToOffset(new LogicalPosition(line, col));
        endOffset = insoff;
        if (LOG.isDebugEnabled()) {
          LOG.debug("segment='" + segment + "'");
          LOG.debug("origSegment='" + origSegment + "'");
          LOG.debug("insoff=" + insoff);
        }
        for (int i = 0; i < count; i++) {
          String txt = i == 0 ? origSegment : segment;
          VimPlugin.getChange().insertText(editor, insoff, txt);
          insertCnt += txt.length();
          endOffset += txt.length();
        }


        if (mode == CommandState.SubMode.VISUAL_LINE) {
          VimPlugin.getChange().insertText(editor, endOffset, "\n");
          insertCnt++;
          endOffset++;
        }
        else {
          if (pad.length() > 0) {
            VimPlugin.getChange().insertText(editor, insoff, pad);
            insertCnt += pad.length();
            endOffset += pad.length();
          }
        }

        line++;
      }
    }

    LogicalPosition slp = editor.offsetToLogicalPosition(offset);
    /*
    int adjust = 0;
    if ((type & Command.FLAG_MOT_LINEWISE) != 0)
    {
        adjust = -1;
    }
    */
    LogicalPosition elp = editor.offsetToLogicalPosition(endOffset - 1);
    if (LOG.isDebugEnabled()) {
      LOG.debug("slp.line=" + slp.line);
      LOG.debug("elp.line=" + elp.line);
    }
    //if (indent) {
    //  int startOff = editor.getDocument().getLineStartOffset(slp.line);
    //  int endOff = editor.getDocument().getLineEndOffset(elp.line);
    //  VimPlugin.getChange().autoIndentRange(editor, context, new TextRange(startOff, endOff));
    //}
    /*
    boolean indented = false;
    for (int i = slp.line; indent && i <= elp.line; i++)
    {
        MotionGroup.moveCaret(editor, context, editor.logicalPositionToOffset(new LogicalPosition(i, 0)));
        KeyHandler.executeAction("OrigAutoIndentLines", context);
        indented = true;
    }
    */
    if (LOG.isDebugEnabled()) {
      LOG.debug("insertCnt=" + insertCnt);
    }
    if (indent) {
      endOffset = EditorHelper.getLineEndOffset(editor, elp.line, true);
      insertCnt = endOffset - offset;
      if (LOG.isDebugEnabled()) {
        LOG.debug("insertCnt=" + insertCnt);
      }
    }

    int cursorMode;
    if (type == SelectionType.BLOCK_WISE) {
      if (mode == CommandState.SubMode.VISUAL_LINE) {
        cursorMode = cursorAfter ? 4 : 1;
      }
      else {
        cursorMode = cursorAfter ? 5 : 1;
      }
    }
    else if (type == SelectionType.LINE_WISE) {
      if (mode == CommandState.SubMode.VISUAL_LINE) {
        cursorMode = cursorAfter ? 4 : 3;
      }
      else {
        cursorMode = cursorAfter ? 4 : 3;
      }
    }
    else /* Characterwise */ {
      if (mode == CommandState.SubMode.VISUAL_LINE) {
        cursorMode = cursorAfter ? 4 : 1;
      }
      else {
        cursorMode = cursorAfter ? 5 : 2;
      }
    }

    switch (cursorMode) {
      case 1:
        MotionGroup.moveCaret(editor, offset);
        break;
      case 2:
        MotionGroup.moveCaret(editor, endOffset - 1);
        break;
      case 3:
        editor.getCaretModel()
          .moveToOffset(CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), offset, " \t"));
        break;
      case 4:
        MotionGroup.moveCaret(editor, endOffset + 1);
        break;
      case 5:
        int pos = Math.min(endOffset, EditorHelper.getLineEndForOffset(editor, endOffset - 1) - 1);
        MotionGroup.moveCaret(editor, pos);
        break;
    }

    VimPlugin.getMark().setChangeMarks(editor, new TextRange(offset, endOffset));
  }


  public void paste(Editor editor,
                    SelectionType type,
                    Transferable transferable,
                    boolean indent,
                    boolean cursorAfter,
                    int offset) {


    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;

    final Document document = editor.getDocument();
    if (!EditorModificationUtil.requestWriting(editor)) {
      return;
    }


    final Project project = editor.getProject();
    if (project == null || editor.isColumnMode() || editor.getCaretModel().getCaretCount() > 1) {
      return;
    }

    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      return;
    }

    DumbService.getInstance(project).setAlternativeResolveEnabled(true);
    document.startGuardedBlockChecking();
    try {
      doPaste(editor, project, file, document, transferable, type, indent, cursorAfter, offset);
    }
    catch (ReadOnlyFragmentModificationException e) {
      EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
    }
    finally {
      document.stopGuardedBlockChecking();
      DumbService.getInstance(project).setAlternativeResolveEnabled(false);
    }
  }

  private static void doPaste(final Editor editor,
                              final Project project,
                              final PsiFile file,
                              final Document document,
                              @NotNull final Transferable content,
                              SelectionType type,
                              boolean indent,
                              boolean cursorAfter,
                              int offset) {
    CopyPasteManager.getInstance().stopKillRings();

    String text = null;
    try {
      text = (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (Exception e) {
      editor.getComponent().getToolkit().beep();
    }
    if (text == null) return;

    if (type == SelectionType.LINE_WISE) {
      if (editor.getSelectionModel().hasSelection()) {
        document
          .deleteString(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
      }
      editor.getSelectionModel().removeSelection();
    }

    editor.getCaretModel().moveToOffset(offset);
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    final Map<CopyPastePostProcessor, List<? extends TextBlockTransferableData>> extraData = new HashMap<>();
    final Collection<TextBlockTransferableData> allValues = new ArrayList<>();

    for (CopyPastePostProcessor<? extends TextBlockTransferableData> processor : Extensions
      .getExtensions(CopyPastePostProcessor.EP_NAME)) {
      List<? extends TextBlockTransferableData> data = processor.extractTransferableData(content);
      if (!data.isEmpty()) {
        extraData.put(processor, data);
        allValues.addAll(data);
      }
    }

    text = TextBlockTransferable.convertLineSeparators(editor, text, allValues);

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int col = caretModel.getLogicalPosition().column;

    // There is a possible case that we want to perform paste while there is an active selection at the editor and caret is located
    // inside it (e.g. Ctrl+A is pressed while caret is not at the zero column). We want to insert the text at selection start column
    // then, hence, inserted block of text should be indented according to the selection start as well.
    final int blockIndentAnchorColumn;
    final int caretOffset = caretModel.getOffset();
    if (selectionModel.hasSelection() && caretOffset >= selectionModel.getSelectionStart()) {
      blockIndentAnchorColumn = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).column;
    }
    else {
      blockIndentAnchorColumn = col;
    }

    // We assume that EditorModificationUtil.insertStringAtCaret() is smart enough to remove currently selected text (if any).

    RawText rawText = RawText.fromTransferable(content);
    String newText = text;
    for (CopyPastePreProcessor preProcessor : Extensions.getExtensions(CopyPastePreProcessor.EP_NAME)) {
      newText = preProcessor.preprocessOnPaste(project, file, editor, newText, rawText);
    }
    int indentOptions = text.equals(newText) ? settings.REFORMAT_ON_PASTE : CodeInsightSettings.REFORMAT_BLOCK;
    text = newText;

    if (LanguageFormatting.INSTANCE.forContext(file) == null && indentOptions != CodeInsightSettings.NO_REFORMAT) {
      indentOptions = CodeInsightSettings.INDENT_BLOCK;
    }

    final String _text = text;
    //EditorAction editorStartNewLine = (EditorAction)ActionManager.getInstance().getAction("EditorStartNewLine");
    //editorStartNewLine.getHandler().execute(editor,editor.getCaretModel().getCurrentCaret(),null);
    if (cursorAfter &&
        document.getCharsSequence().length() > offset + 1 &&
        document.getCharsSequence().charAt(offset) != '\n'
       && !type.equals(SelectionType.LINE_WISE)) {
      caretModel.moveToOffset(offset + 1);
    }
    EditorModificationUtil.insertStringAtCaret(editor, _text, false, true, type);
    caretModel.moveToOffset(offset);

    int length = text.length();
    if (offset < 0) {
      length += offset;
      offset = 0;
    }
    final RangeMarker bounds = document.createRangeMarker(offset, offset + length);

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    selectionModel.removeSelection();

    final Ref<Boolean> indented = new Ref<>(Boolean.FALSE);
    for (Map.Entry<CopyPastePostProcessor, List<? extends TextBlockTransferableData>> e : extraData.entrySet()) {
      //noinspection unchecked
      e.getKey().processTransferableData(project, editor, bounds, caretOffset, indented, e.getValue());
    }

    boolean pastedTextContainsWhiteSpacesOnly =
      CharArrayUtil.shiftForward(document.getCharsSequence(), bounds.getStartOffset(), " \n\t") >=
      bounds.getEndOffset();

    VirtualFile virtualFile = file.getVirtualFile()
      ;
    if (!pastedTextContainsWhiteSpacesOnly &&
        (virtualFile == null || !SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile))) {
      final int indentOptions1 = indentOptions;

      int finalOffset = offset;
      String finalText = text;
      ApplicationManager.getApplication().runWriteAction(() -> {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        switch (indentOptions1) {
          case CodeInsightSettings.INDENT_BLOCK:
            if (!indented.get()) {
              indentBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset(), blockIndentAnchorColumn);
            }
            break;

          case CodeInsightSettings.INDENT_EACH_LINE:
            if (!indented.get()) {
              if (type == SelectionType.LINE_WISE) {
                editor.getCaretModel().moveToOffset(
                  CharArrayUtil.shiftForward(editor.getDocument().getCharsSequence(), finalOffset, " \t"));
              }
              else {
                editor.getCaretModel().moveToOffset(finalOffset + finalText.length());
              }
              indentEachLine(project, editor, bounds.getStartOffset(), bounds.getEndOffset());
            }
            break;

          case CodeInsightSettings.REFORMAT_BLOCK:
            indentEachLine(project, editor, bounds.getStartOffset(),
                           bounds.getEndOffset()); // this is needed for example when inserting a comment before method
            reformatBlock(project, editor, bounds.getStartOffset(), bounds.getEndOffset());

            break;
        }
      });
    }

    if (bounds.isValid()) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      selectionModel.removeSelection();
      editor.putUserData(EditorEx.LAST_PASTED_REGION, com.intellij.openapi.util.TextRange.create(bounds));
    }
  }

  static void indentBlock(Project project,
                          Editor editor,
                          final int startOffset,
                          final int endOffset,
                          int originalCaretCol) {
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    documentManager.commitAllDocuments();
    final Document document = editor.getDocument();
    PsiFile file = documentManager.getPsiFile(document);
    if (file == null) {
      return;
    }

    if (LanguageFormatting.INSTANCE.forContext(file) != null) {
      indentBlockWithFormatter(project, document, startOffset, endOffset, file);
    }
    else {
      indentPlainTextBlock(document, startOffset, endOffset, originalCaretCol);
    }
  }

  private static void indentEachLine(Project project, Editor editor, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final CharSequence text = editor.getDocument().getCharsSequence();
    if (startOffset > 0 &&
        endOffset > startOffset + 1 &&
        text.charAt(endOffset - 1) == '\n' &&
        text.charAt(startOffset - 1) == '\n') {
      // There is a possible situation that pasted text ends by a line feed. We don't want to proceed it when a text is
      // pasted at the first line column.
      // Example:
      //    text to paste:
      //'if (true) {
      //'
      //    source:
      // if (true) {
      //     int i = 1;
      //     int j = 1;
      // }
      //
      //
      // We get the following on paste then:
      // if (true) {
      //     if (true) {
      //         int i = 1;
      //     int j = 1;
      // }
      //
      // We don't want line 'int i = 1;' to be indented here.
      endOffset--;
    }
    try {
      codeStyleManager.adjustLineIndent(file, new com.intellij.openapi.util.TextRange(startOffset, endOffset));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void reformatBlock(final Project project,
                                    final Editor editor,
                                    final int startOffset,
                                    final int endOffset) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    try {
      CodeStyleManager.getInstance(project).reformatRange(file, startOffset, endOffset, true);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @SuppressWarnings("ForLoopThatDoesntUseLoopVariable")
  private static void indentPlainTextBlock(final Document document,
                                           final int startOffset,
                                           final int endOffset,
                                           final int indentLevel) {
    CharSequence chars = document.getCharsSequence();
    int spaceEnd = CharArrayUtil.shiftForward(chars, startOffset, " \t");
    final int startLine = document.getLineNumber(startOffset);
    if (spaceEnd > endOffset ||
        indentLevel <= 0 ||
        startLine >= document.getLineCount() - 1 ||
        chars.charAt(spaceEnd) == '\n') {
      return;
    }

    int endLine = startLine + 1;
    while (endLine < document.getLineCount() && document.getLineStartOffset(endLine) < endOffset) endLine++;

    final String indentString = StringUtil.repeatSymbol(' ', indentLevel);
    indentLines(document, startLine + 1, endLine - 1, indentString);
  }

  private static void indentBlockWithFormatter(Project project,
                                               final Document document,
                                               int startOffset,
                                               int endOffset,
                                               PsiFile file) {

    // Algorithm: the main idea is to process the first line of the pasted block, adjust its indent if necessary, calculate indent
    // adjustment string and apply to each line of the pasted block starting from the second one.
    //
    // We differentiate the following possible states here:
    //   --- pasted block doesn't start new line, i.e. there are non-white space symbols before it at the first line.
    //      Example:
    //         old content [pasted line 1
    //                pasted line 2]
    //      Indent adjustment string is just the first line indent then.
    //
    //   --- pasted block starts with empty line(s)
    //      Example:
    //         old content [
    //            pasted line 1
    //            pasted line 2]
    //      We parse existing indents of the pasted block then, adjust its first non-blank line via formatter and adjust indent
    //      of subsequent pasted lines in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line and starts with white space symbols.
    //       Example:
    //          [   pasted line 1
    //                 pasted line 2]
    //       We parse existing indents of the pasted block then, adjust its first line via formatter and adjust indent of the pasted lines
    //       starting from the second one in order to preserve old indentation.
    //
    //   --- pasted block is located at the new line but doesn't start with white space symbols.
    //       Example:
    //           [pasted line 1
    //         pasted line 2]
    //       We adjust the first line via formatter then and apply first line's indent to all subsequent pasted lines.

    final CharSequence chars = document.getCharsSequence();
    final int firstLine = document.getLineNumber(startOffset);
    final int firstLineStart = document.getLineStartOffset(firstLine);

    // There is a possible case that we paste block that ends with new line that is empty or contains only white space symbols.
    // We want to preserve indent for the original document line where paste was performed.
    // Example:
    //   Original:
    //       if (test) {
    //   <caret>    }
    //
    //   Pasting: 'int i = 1;\n'
    //   Expected:
    //       if (test) {
    //           int i = 1;
    //       }
    boolean saveLastLineIndent = false;
    for (int i = endOffset - 1; i >= startOffset; i--) {
      final char c = chars.charAt(i);
      if (c == '\n') {
        saveLastLineIndent = true;
        break;
      }
      if (c != ' ' && c != '\t') {
        break;
      }
    }

    final int lastLine;
    if (saveLastLineIndent) {
      lastLine = document.getLineNumber(endOffset) - 1;
      // Remove white space symbols at the pasted text if any.
      int start = document.getLineStartOffset(lastLine + 1);
      if (start < endOffset) {
        int i = CharArrayUtil.shiftForward(chars, start, " \t");
        if (i > start) {
          i = Math.min(i, endOffset);
          document.deleteString(start, i);
        }
      }

      // Insert white space from the start line of the pasted block.
      int indentToKeepEndOffset = Math.min(startOffset, CharArrayUtil.shiftForward(chars, firstLineStart, " \t"));
      if (indentToKeepEndOffset > firstLineStart) {
        document.insertString(start, chars.subSequence(firstLineStart, indentToKeepEndOffset));
      }
    }
    else {
      lastLine = document.getLineNumber(endOffset);
    }

    final int i = CharArrayUtil.shiftBackward(chars, startOffset - 1, " \t");

    // Handle a situation when pasted block doesn't start a new line.
    if (chars.charAt(startOffset) != '\n' && i > 0 && chars.charAt(i) != '\n') {
      int firstNonWsOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (firstNonWsOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, firstNonWsOffset);
        indentLines(document, firstLine + 1, lastLine, toInsert);
      }
      return;
    }

    // Sync document and PSI for correct formatting processing.
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    if (file == null) {
      return;
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final int j = CharArrayUtil.shiftForward(chars, startOffset, " \t\n");
    if (j >= endOffset) {
      // Pasted text contains white space/line feed symbols only, do nothing.
      return;
    }

    final int anchorLine = document.getLineNumber(j);
    final int anchorLineStart = document.getLineStartOffset(anchorLine);
    codeStyleManager.adjustLineIndent(file, j);

    // Handle situation when pasted block starts with non-white space symbols.
    if (anchorLine == firstLine && j == startOffset) {
      int indentOffset = CharArrayUtil.shiftForward(chars, firstLineStart, " \t");
      if (indentOffset > firstLineStart) {
        CharSequence toInsert = chars.subSequence(firstLineStart, indentOffset);
        indentLines(document, firstLine + 1, lastLine, toInsert);
      }
      return;
    }

    // Handle situation when pasted block starts from white space symbols. Assume that the pasted text started at the line start,
    // i.e. correct indentation level is stored at the blocks structure.
    final int firstNonWsOffset = CharArrayUtil.shiftForward(chars, anchorLineStart, " \t");
    final int diff = firstNonWsOffset - j;
    if (diff == 0) {
      return;
    }
    if (diff > 0) {
      CharSequence toInsert = chars.subSequence(anchorLineStart, anchorLineStart + diff);
      indentLines(document, anchorLine + 1, lastLine, toInsert);
      return;
    }

    // We've pasted text to the non-first column and exact white space between the line start and caret position on the moment of paste
    // has been removed by formatter during 'adjust line indent'
    // Example:
    //       copied text:
    //                 '   line1
    //                       line2'
    //       after paste:
    //          line start -> '   I   line1
    //                              line2' (I - caret position during 'paste')
    //       formatter removed white space between the line start and caret position, so, current document state is:
    //                        '   line1
    //                              line2'
    if (anchorLine == firstLine && -diff == startOffset - firstLineStart) {
      return;
    }
    if (anchorLine != firstLine || -diff > startOffset - firstLineStart) {
      final int desiredSymbolsToRemove;
      if (anchorLine == firstLine) {
        desiredSymbolsToRemove = -diff - (startOffset - firstLineStart);
      }
      else {
        desiredSymbolsToRemove = -diff;
      }

      Runnable deindentTask = () -> {
        for (int line = anchorLine + 1; line <= lastLine; line++) {
          int currentLineStart = document.getLineStartOffset(line);
          int currentLineIndentOffset = CharArrayUtil.shiftForward(chars, currentLineStart, " \t");
          int symbolsToRemove = Math.min(currentLineIndentOffset - currentLineStart, desiredSymbolsToRemove);
          if (symbolsToRemove > 0) {
            document.deleteString(currentLineStart, currentLineStart + symbolsToRemove);
          }
        }
      };
      DocumentUtil.executeInBulk(document, lastLine - anchorLine > LINE_LIMIT_FOR_BULK_CHANGE, deindentTask);
    }
    else {
      CharSequence toInsert = chars.subSequence(anchorLineStart, diff + startOffset);
      indentLines(document, anchorLine + 1, lastLine, toInsert);
    }
  }

  /**
   * Inserts specified string at the beginning of lines from {@code startLine} to {@code endLine} inclusive.
   */
  private static void indentLines(final @NotNull Document document,
                                  final int startLine,
                                  final int endLine,
                                  final @NotNull CharSequence indentString) {
    Runnable indentTask = () -> {
      for (int line = startLine; line <= endLine; line++) {
        int lineStartOffset = document.getLineStartOffset(line);
        document.insertString(lineStartOffset, indentString);
      }
    };
    DocumentUtil.executeInBulk(document, endLine - startLine > LINE_LIMIT_FOR_BULK_CHANGE, indentTask);
  }

  private static final int LINE_LIMIT_FOR_BULK_CHANGE = 5000;
  private static final Logger LOG = Logger.getInstance(CopyGroup.class.getName());
}

