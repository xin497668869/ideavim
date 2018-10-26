package com.maddyhome.idea.vim.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.maddyhome.idea.vim.extension.VimExtensionFacade.executeNormal;
import static com.maddyhome.idea.vim.helper.StringHelper.parseKeys;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class MethodCommentGenerator extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getDataContext().getData(EDITOR);
        executeNormal(parseKeys("a/**<enter>"), editor);

    }

}
