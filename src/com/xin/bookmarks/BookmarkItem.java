/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xin.bookmarks;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author zajac
 * @since 6.05.2012
 */
public class BookmarkItem extends ItemWrapper implements Comparable<BookmarkItem> {
    public static final int      MAX_VALUE = Integer.MAX_VALUE;
    private final       Bookmark myBookmark;

    public BookmarkItem(Bookmark bookmark) {
        myBookmark = bookmark;
    }

    public static void setupRenderer(SimpleColoredComponent renderer, Project project, Bookmark bookmark, boolean selected) {
        VirtualFile file = bookmark.getFile();
        if (!file.isValid()) {
            return;
        }

        PsiManager psiManager = PsiManager.getInstance(project);

        PsiElement fileOrDir = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file);
        if (fileOrDir != null) {
            renderer.setIcon(fileOrDir.getIcon(0));
        }

        String description = bookmark.getDescription();
        if (description != null) {
            SpeedSearch speedSearch = (SpeedSearch) renderer.getClientProperty("speedSearch");
            if (speedSearch.getMatcher() != null) {
                final Iterable<TextRange> iterable = ((MinusculeMatcher) speedSearch.getMatcher()).matchingFragments(description);
                SpeedSearchUtil.appendColoredFragments(renderer, description, iterable, SimpleTextAttributes.REGULAR_ATTRIBUTES, new SimpleTextAttributes(null, null, null, SimpleTextAttributes.STYLE_SEARCH_MATCH));
            }else {
                renderer.append(description, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            if (!description.isEmpty()) {
                renderer.append(" - ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }

        FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
        TextAttributes attributes = new TextAttributes(fileStatus.getColor(), null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN);
        renderer.append(file.getName(), SimpleTextAttributes.fromTextAttributes(attributes));
        if (bookmark.getLine() >= 0) {
            renderer.append(":", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            renderer.append(String.valueOf(bookmark.getLine() + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        renderer.append(" (" + VfsUtilCore.getRelativeLocation(file, project.getBaseDir()) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);

        if (!selected) {
            FileColorManager colorManager = FileColorManager.getInstance(project);
            if (fileOrDir instanceof PsiFile) {
                Color color = colorManager.getRendererBackground((PsiFile) fileOrDir);
                if (color != null) {
                    renderer.setBackground(color);
                }
            }
        }
    }

    public Bookmark getBookmark() {
        return myBookmark;
    }

    @Override
    public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
        setupRenderer(renderer, project, myBookmark, selected);
    }

    @Override
    public void setupRenderer(ColoredTreeCellRenderer renderer, Project project, boolean selected) {
        setupRenderer(renderer, project, myBookmark, selected);
    }

    @Override
    public void updateAccessoryView(JComponent component) {
        JLabel label = (JLabel) component;
        final char mnemonic = myBookmark.getMnemonic();
        if (mnemonic != 0) {
            label.setText(Character.toString(mnemonic) + '.');
        } else {
            label.setText("");
        }
    }

    @Override
    public String speedSearchText() {
        return myBookmark.getFile().getName() + " " + myBookmark.getDescription();
    }

    @Override
    public String footerText() {
        return myBookmark.getFile().getPresentableUrl();
    }

    @Override
    protected void doUpdateDetailView(DetailView panel, boolean editorOnly) {
        panel.navigateInPreviewEditor(DetailView.PreviewEditorState.create(myBookmark.getFile(), myBookmark.getLine()));
        Alarm alarm = new Alarm(((EditorImpl) panel.getEditor()).getDisposable());
        startBlinkingHighlights((EditorEx) panel.getEditor(), alarm, MAX_VALUE);
    }

    private void startBlinkingHighlights(final EditorEx editor,
                                         final Alarm alarm,
                                         final int count) {
        if (count == 0) {
            return;
        }
        UIUtil.invokeAndWaitIfNeeded((Runnable) () -> {
            if (count % 2 > 0) {
                editor.getMarkupModel().removeAllHighlighters();
                return;
            }
//            try {
//                 IDEA-53203: add ERASE_MARKER for manually defined attributes
            int startOffset = 3;
            int endOffset = 20;
            TextAttributes textAttributes = new TextAttributes(JBColor.RED, JBColor.WHITE, JBColor.RED, EffectType.ROUNDED_BOX, Font.PLAIN);

            editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.ADDITIONAL_SYNTAX,
                                                        TextAttributes.ERASE_MARKER, HighlighterTargetArea.EXACT_RANGE);
            int offset = editor.logicalPositionToOffset(new LogicalPosition(getBookmark().getLine(), getBookmark().getColumn()));

            RangeHighlighter highlighter = editor.getMarkupModel()
                    .addRangeHighlighter(offset, offset + 1, HighlighterLayer.ADDITIONAL_SYNTAX, textAttributes,
                                         HighlighterTargetArea.EXACT_RANGE);
//            final Color errorStripeColor = textAttributes.getErrorStripeColor();
//            highlighter.setErrorStripeMarkColor(errorStripeColor);
//            highlighter.setErrorStripeTooltip("hahatip");
            if (highlighter instanceof RangeHighlighterEx) ((RangeHighlighterEx) highlighter).setVisibleIfFolded(true);
        });
        alarm.addRequest(() -> startBlinkingHighlights(editor, alarm, count - 1), 400);
    }

    @Override
    public boolean allowedToRemove() {
        return true;
    }

    @Override
    public void removed(Project project) {
        BookmarkManager.getInstance(project).removeBookmark(getBookmark());
    }

    @Override
    public int compareTo(BookmarkItem o) {
        return myBookmark.compareTo(o.myBookmark);
    }
}
