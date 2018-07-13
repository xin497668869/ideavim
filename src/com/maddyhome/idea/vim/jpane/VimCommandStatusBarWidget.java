package com.maddyhome.idea.vim.jpane;

import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

import static java.awt.Component.CENTER_ALIGNMENT;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class VimCommandStatusBarWidget extends EditorBasedWidget
  implements StatusBarWidget.TextPresentation, StatusBarWidget.WidgetPresentation, EditorFactoryListener {

  public static final String VIM_COMMAND = "VIM_COMMAND";
  private String text = "";

  public static void cleanVimCommandStatusBar(Project project) {

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    VimCommandStatusBarWidget statusBarWidget = (VimCommandStatusBarWidget)statusBar.getWidget(VIM_COMMAND);
    statusBarWidget.setText("");
    statusBar.updateWidget(VIM_COMMAND);
  }

  public static void updateVimCommandStatusBar(Project project, char c) {

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    VimCommandStatusBarWidget statusBarWidget = (VimCommandStatusBarWidget)statusBar.getWidget(VIM_COMMAND);
    statusBarWidget.setText(statusBarWidget.getText() + c);
    statusBar.updateWidget(VIM_COMMAND);
  }

  protected VimCommandStatusBarWidget(@NotNull Project project) {
    super(project);
  }


  public void setText(String text) {
    this.text = text;
  }

  @NotNull
  @Override
  public String ID() {
    return VIM_COMMAND;
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Nullable
  @Override
  public String getTooltipText() {
    return "VIM Command";
  }

  @Nullable
  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return null;
  }

  @NotNull
  @Override
  public String getText() {
    return text;
  }

  @NotNull
  @Override
  public String getMaxPossibleText() {
    return "eee";
  }

  @Override
  public float getAlignment() {
    return CENTER_ALIGNMENT;
  }

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    final SelectionModel selectionModel = event.getEditor().getSelectionModel();
    selectionModel.addSelectionListener(new SelectionListener() {
      @Override
      public void selectionChanged(SelectionEvent e) {
        selectionModel.removeSelection();
        selectionModel.removeSelectionListener(this);
      }
    });
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {

  }
}
