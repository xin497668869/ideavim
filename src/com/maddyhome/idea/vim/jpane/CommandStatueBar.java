package com.maddyhome.idea.vim.jpane;

import com.intellij.codeInsight.template.TemplateManagerListener;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBus;
import com.maddyhome.idea.vim.action.change.insert.VimUndoableAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.TemplateManager.TEMPLATE_STARTED_TOPIC;

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
        EditorFactory.getInstance().addEditorFactoryListener(widget, widget);

        MessageBus messageBus = project.getMessageBus();
        CommandProcessor.getInstance().addCommandListener(new CommandListener() {
            @Override
            public void commandStarted(CommandEvent event) {
                if (event.getCommandName().startsWith("Renaming")) {

                    Editor selectedEditors = FileEditorManager.getInstance(event.getProject()).getSelectedTextEditor();
                    UndoableAction vimUndoableAction = new VimUndoableAction(
                            new DocumentReference[]{DocumentReferenceManager.getInstance().create(selectedEditors.getDocument())}, "templateStarted");

                    if (event.getProject() != null) {
                        UndoManager.getInstance(event.getProject()).undoableActionPerformed(vimUndoableAction);
                    }
                }
            }
        });
        messageBus.connect().subscribe(TEMPLATE_STARTED_TOPIC, new TemplateManagerListener() {
            @Override
            public void templateStarted(@NotNull TemplateState state) {
            }
        });
    }
}
