package com.maddyhome.idea.vim.extension.around;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.extension.VimExtensionFacade;
import com.maddyhome.idea.vim.extension.VimExtensionHandler;
import org.jetbrains.annotations.NotNull;

import static com.maddyhome.idea.vim.helper.StringHelper.parseKeys;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class AroundHandler implements VimExtensionHandler {
  @Override
  public void execute(@NotNull Editor editor, @NotNull DataContext context) {
    if (CommandState.getInstance(editor).getMode() != CommandState.Mode.VISUAL) {
      if (CommandState.getInstance(editor).getMode() == CommandState.Mode.INSERT) {
        VimExtensionFacade.executeNormal(parseKeys("<Esc>"), editor);
      }
    }
    EditorAction editorSelectWord = (EditorAction)ActionManager.getInstance().getAction("EditorSelectWord");
    editorSelectWord.getHandler().execute(editor, context);
    if(CommandState.getInstance(editor).getMode() != CommandState.Mode.VISUAL) {
      VimPlugin.getMotion().setVisualMode(editor, CommandState.SubMode.VISUAL_CHARACTER);
    }
  }

}
