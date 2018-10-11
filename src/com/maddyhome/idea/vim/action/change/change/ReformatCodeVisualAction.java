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

package com.maddyhome.idea.vim.action.change.change;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.actions.CleanupAllIntention;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.action.VimCommandAction;
import com.maddyhome.idea.vim.command.Command;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.handler.EditorActionHandlerBase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.maddyhome.idea.vim.command.MappingMode.NVO;

/**
 * @author vlan
 */
public class ReformatCodeVisualAction extends VimCommandAction {

    public static final String DA_KUO_HAO_EXPECTED = "'}' expected";
    public static final String FEN_HAO_EXPECTED    = "';' expected";

    public ReformatCodeVisualAction() {
        super(new EditorActionHandlerBase() {
            @Override
            protected boolean execute(@NotNull Editor editor,
                                      @NotNull DataContext context,
                                      @NotNull Command cmd
            ) {
                PsiFile psiFile = context.getData(CommonDataKeys.PSI_FILE);
                Project project = context.getData(CommonDataKeys.PROJECT);
                if (project == null) {
                    return true;
                }
                CleanupAllIntention.INSTANCE.invoke(project, editor, psiFile);
                VimPlugin.getChange().reformatCode(context);
                List<HighlightInfo> highlightInfos = new ArrayList<>();
                DaemonCodeAnalyzerEx.processHighlights(editor.getDocument(), project, HighlightSeverity.ERROR, 0,
                                                       editor.getDocument().getTextLength(), highlightInfo -> {
                            if (FEN_HAO_EXPECTED.equals(highlightInfo.getDescription())) {
                                highlightInfos.add(highlightInfo);
                            }
                            if (DA_KUO_HAO_EXPECTED.equals(highlightInfo.getDescription())) {
                                highlightInfos.add(highlightInfo);
                            }
                            return true;
                        });
                for (HighlightInfo highlightInfo : highlightInfos) {
                    if (FEN_HAO_EXPECTED.equals(highlightInfo.getDescription())) {
                        editor.getDocument().insertString(highlightInfo.getStartOffset(), ";");
                    } else if (DA_KUO_HAO_EXPECTED.equals(highlightInfo.getDescription())) {
                        editor.getDocument().insertString(highlightInfo.getStartOffset(), "}");

                    }
                }
                return true;
            }
        });
    }

    @NotNull
    @Override
    public Set<MappingMode> getMappingModes() {
        return NVO;
    }

    @NotNull
    @Override
    public Set<List<KeyStroke>> getKeyStrokesSet() {
        return parseKeysSet("<space>l");
    }

    @NotNull
    @Override
    public Command.Type getType() {
        return Command.Type.CHANGE;
    }

}
