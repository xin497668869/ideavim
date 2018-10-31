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
package com.xin.bookmarks.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.xin.bookmarks.Bookmark;
import com.xin.bookmarks.BookmarkManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class ToggleNumberedBookmarkActionBase extends AnAction implements DumbAware {
    private final char    number;
    private final boolean isMessage;

    public ToggleNumberedBookmarkActionBase(char number, boolean isMessage) {
        setEnabledInModalContext(true);
        this.number = number;
        this.isMessage = isMessage;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();

//        Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);
//        Alarm alarm = new Alarm();

        if (project == null) return;

        BookmarksAction.BookmarkInContextInfo info = new BookmarksAction.BookmarkInContextInfo(e.getDataContext(), project).invoke();
        if (info.getFile() == null) return;

        Bookmark oldBookmark = info.getBookmarkAtPlace();
        BookmarkManager manager = BookmarkManager.getInstance(project);

        if (oldBookmark != null) {
            manager.removeBookmark(oldBookmark);
        }

        char mnemonic = number;
        if (oldBookmark == null || oldBookmark.getMnemonic() != mnemonic) {
            if(isMessage) {
                String inputDialog = Messages.showInputDialog(project, "标签描述", "标签描述", null);
                manager.addTextBookmark(info.getFile(), info.getPosition(), inputDialog);
            }else {
                Bookmark bookmark = manager.addTextBookmark(info.getFile(), info.getPosition(), "");
                manager.setMnemonic(bookmark, mnemonic);
            }
        }
    }

    public static class ToggleBookmarkAAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkAAction() {
            super('a', false);
        }
    }

    public static class ToggleBookmarkDAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkDAction() {
            super('d', false);
        }
    }

    public static class ToggleBookmarkEAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkEAction() {
            super('e', false);
        }
    }

    public static class ToggleBookmarkFAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkFAction() {
            super('f', false);
        }
    }


    public static class ToggleBookmarkQAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkQAction() {
            super('q', false);
        }
    }

    public static class ToggleBookmarkWAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkWAction() {
            super('w', false);
        }
    }

    public static class ToggleBookmarkRAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkRAction() {
            super('r', false);
        }
    }

    public static class ToggleBookmarkMAction extends ToggleNumberedBookmarkActionBase {
        public ToggleBookmarkMAction() {
            super(' ', true);
        }
    }

}
