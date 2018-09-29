package com.maddyhome.idea.vim.jpane;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class CommandStatueBar implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    VimCommandStatusBarWidget widget = new VimCommandStatusBarWidget(project);
    statusBar.addWidget(widget, "before Position");
     EditorFactory.getInstance().addEditorFactoryListener(widget,widget);
  }
}
