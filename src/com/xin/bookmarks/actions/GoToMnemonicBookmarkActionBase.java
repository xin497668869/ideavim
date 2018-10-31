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
import com.xin.bookmarks.Bookmark;
import com.xin.bookmarks.BookmarkManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class GoToMnemonicBookmarkActionBase extends AnAction implements DumbAware {
    private final char myNumber;

    public GoToMnemonicBookmarkActionBase(char n) {
        myNumber = n;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            Bookmark bookmark = BookmarkManager.getInstance(project).findBookmarkForMnemonic(myNumber);
            if (bookmark != null) {
                bookmark.navigate(true);
            }
        }
    }

    public static class GotoBookmarkAAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkAAction() {
            super('a');
        }
    }

    public static class GotoBookmarkDAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkDAction() {
            super('d');
        }
    }

    public static class GotoBookmarkFAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkFAction() {
            super('f');
        }
    }

    public static class GotoBookmarkQAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkQAction() {
            super('q');
        }
    }

    public static class GotoBookmarkWAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkWAction() {
            super('w');
        }
    }

    public static class GotoBookmarkEAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkEAction() {
            super('e');
        }
    }

    public static class GotoBookmarkRAction extends GoToMnemonicBookmarkActionBase {
        public GotoBookmarkRAction() {
            super('r');
        }
    }

}
