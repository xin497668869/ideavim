// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.xin.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@State(
        name = "myBookmarkManager",
        storages = {
                @Storage(StoragePathMacros.WORKSPACE_FILE)
        }
)
public class BookmarkManager implements PersistentStateComponent<Element> {
    private static final int                 MAX_AUTO_DESCRIPTION_SIZE = 50;
    private static final Key<List<Bookmark>> BOOKMARKS_KEY             = Key.create("com/xin/bookmarks");

    private final List<Bookmark>                                          myBookmarks                = new CopyOnWriteArrayList<>();
    private final Map<Trinity<VirtualFile, Integer, String>, Bookmark>    myDeletedDocumentBookmarks =
            new HashMap<>();
    private final Map<Document, List<Trinity<Bookmark, Integer, String>>> myBeforeChangeData         = new HashMap<>();

    private final MessageBus myBus;
    private final Project    myProject;

    private boolean mySortedState;

    public BookmarkManager(Project project,
                           PsiDocumentManager documentManager,
                           EditorColorsManager colorsManager,
                           EditorFactory editorFactory) {
        myProject = project;
        myBus = project.getMessageBus();
        MessageBusConnection connection = project.getMessageBus().connect();
        connection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
            @Override
            public void globalSchemeChange(EditorColorsScheme scheme) {
                colorsChanged();
            }
        });
        EditorEventMulticaster multicaster = editorFactory.getEventMulticaster();
        multicaster.addDocumentListener(new MyDocumentListener(), myProject);
        multicaster.addEditorMouseListener(new MyEditorMouseListener(), myProject);

        documentManager.addListener(new PsiDocumentManager.Listener() {
            @Override
            public void documentCreated(@NotNull final Document document, PsiFile psiFile) {
                final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
                if (file == null) return;
                for (final Bookmark bookmark : myBookmarks) {
                    if (Comparing.equal(bookmark.getFile(), file)) {
                        UIUtil.invokeLaterIfNeeded(() -> {
                            if (myProject.isDisposed()) return;
                            bookmark.createHighlighter((MarkupModelEx) DocumentMarkupModel.forDocument(document, myProject, true));
                            map(document, bookmark);
                        });
                    }
                }
            }

            @Override
            public void fileCreated(@NotNull PsiFile file, @NotNull Document document) {
            }
        });
        mySortedState = UISettings.getInstance().getSortBookmarks();
        connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
            if (mySortedState != uiSettings.getSortBookmarks()) {
                mySortedState = uiSettings.getSortBookmarks();
                EventQueue.invokeLater(() -> myBus.syncPublisher(BookmarksListener.TOPIC).bookmarksOrderChanged());
            }
        });
    }

    public static BookmarkManager getInstance(Project project) {
        return ServiceManager.getService(project, BookmarkManager.class);
    }

    private static void map(Document document, Bookmark bookmark) {
        if (document == null || bookmark == null) return;
        ApplicationManager.getApplication().assertIsDispatchThread();
        List<Bookmark> list = document.getUserData(BOOKMARKS_KEY);
        if (list == null) {
            document.putUserData(BOOKMARKS_KEY, list = new ArrayList<>());
        }
        list.add(bookmark);
    }

    private static void unmap(Document document, Bookmark bookmark) {
        if (document == null || bookmark == null) return;
        List<Bookmark> list = document.getUserData(BOOKMARKS_KEY);
        if (list != null && list.remove(bookmark) && list.isEmpty()) {
            document.putUserData(BOOKMARKS_KEY, null);
        }
    }

    @NotNull
    public static String getAutoDescription(@NotNull final Editor editor, final int lineIndex) {
        String autoDescription = editor.getSelectionModel().getSelectedText();
        if (autoDescription == null) {
            Document document = editor.getDocument();
            autoDescription = document.getCharsSequence()
                    .subSequence(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex)).toString().trim();
        }
        if (autoDescription.length() > MAX_AUTO_DESCRIPTION_SIZE) {
            return autoDescription.substring(0, MAX_AUTO_DESCRIPTION_SIZE) + "...";
        }
        return autoDescription;
    }

    public void editDescription(@NotNull Bookmark bookmark, JComponent popup, Project project) {
        String description = Messages
                .showInputDialog(project, IdeBundle.message("action.bookmark.edit.description.dialog.message"),
                                 IdeBundle.message("action.bookmark.edit.description.dialog.title"), Messages.getQuestionIcon(),
                                 bookmark.getDescription(), new InputValidator() {
                            @Override
                            public boolean checkInput(String inputString) {
                                return true;
                            }

                            @Override
                            public boolean canClose(String inputString) {
                                return true;
                            }
                        });
        if (description != null) {
            setDescription(bookmark, description);
        }
    }

    public void addEditorBookmark(@NotNull Editor editor, LogicalPosition lineIndex) {
        Document document = editor.getDocument();
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (psiFile == null) return;

        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) return;

        addTextBookmark(virtualFile, lineIndex, getAutoDescription(editor, lineIndex.line));
    }

    @NotNull
    public Bookmark addTextBookmark(@NotNull VirtualFile file, LogicalPosition lineIndex, @NotNull String description) {
        Bookmark b = new Bookmark(myProject, file, lineIndex, description, true);
        myBookmarks.add(0, b);
        map(b.getDocument(), b);
        myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
        return b;
    }

    @Nullable
    public Bookmark addFileBookmark(@Nullable VirtualFile file, @NotNull String description) {
        if (file == null) return null;
        if (findFileBookmark(file) != null) return null;

        Bookmark b = new Bookmark(myProject, file, new LogicalPosition(0, 0), description, true);
        myBookmarks.add(0, b);
        myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
        return b;
    }


    @NotNull
    public List<Bookmark> getValidBookmarks() {
        List<Bookmark> answer = new ArrayList<>();
        for (Bookmark bookmark : myBookmarks) {
            if (bookmark.isValid()) answer.add(bookmark);
        }
        if (UISettings.getInstance().getSortBookmarks()) {
            Collections.sort(answer);
        }
        return answer;
    }


    @Nullable
    public Bookmark findEditorBookmark(@NotNull Document document, LogicalPosition position) {
        List<Bookmark> bookmarks = document.getUserData(BOOKMARKS_KEY);
        if (bookmarks != null) {
            for (Bookmark bookmark : bookmarks) {
                if (bookmark.getLine() == position.line && bookmark.getColumn() == position.column) {
                    return bookmark;
                }
            }
        }

        return null;
    }

    @Nullable
    public Bookmark findFileBookmark(@NotNull VirtualFile file) {
        for (Bookmark bookmark : myBookmarks) {
            if (Comparing.equal(bookmark.getFile(), file) && bookmark.getLine() == -1) return bookmark;
        }

        return null;
    }

    @Nullable
    public Bookmark findBookmarkForMnemonic(char m) {
        final char mm = Character.toUpperCase(m);
        for (Bookmark bookmark : myBookmarks) {
            if (mm == bookmark.getMnemonic()) return bookmark;
        }
        return null;
    }

    public boolean hasBookmarksWithMnemonics() {
        for (Bookmark bookmark : myBookmarks) {
            if (bookmark.getMnemonic() != 0) return true;
        }

        return false;
    }

    public void removeBookmark(@NotNull Bookmark bookmark) {
        if (myBookmarks.remove(bookmark)) {
            unmap(bookmark.getDocument(), bookmark);
            bookmark.release();
            myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkRemoved(bookmark);
        }
    }

    @Override
    public Element getState() {
        Element container = new Element("BookmarkManager");
        writeExternal(container);
        return container;
    }

    @Override
    public void loadState(@NotNull final Element state) {
        StartupManager.getInstance(myProject).runWhenProjectIsInitialized((DumbAwareRunnable) () -> {
            for (Bookmark bookmark : myBookmarks) {
                bookmark.release();
                unmap(bookmark.getDocument(), bookmark);
            }
            myBookmarks.clear();

            readExternal(state);
        });
    }

    private void readExternal(Element element) {
        for (final Object o : element.getChildren()) {
            Element bookmarkElement = (Element) o;

            if ("bookmark".equals(bookmarkElement.getName())) {
                String url = bookmarkElement.getAttributeValue("url");
                String line = bookmarkElement.getAttributeValue("line");
                String column = bookmarkElement.getAttributeValue("column");
                if (column == null) {
                    column = "0";
                }
                String description = StringUtil.notNullize(bookmarkElement.getAttributeValue("description"));
                boolean simple = Boolean.parseBoolean(StringUtil.notNullize(bookmarkElement.getAttributeValue("isSimple"),"false"));

                String mnemonic = bookmarkElement.getAttributeValue("mnemonic");

                Bookmark b = null;
                VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
                if (file != null) {
                    if (line != null) {
                        try {
                            b = addTextBookmark(file, new LogicalPosition(Integer.parseInt(line), Integer.parseInt(column)), description);
                            b.setSimple(simple);
                        } catch (NumberFormatException e) {
                            // Ignore. Will miss bookmark if line number cannot be parsed
                        }
                    } else {
                        b = addFileBookmark(file, description);
                    }
                }

                if (b != null && mnemonic != null && mnemonic.length() == 1) {
                    setMnemonic(b, mnemonic.charAt(0));
                }
            }
        }
    }

    private void writeExternal(Element element) {
        List<Bookmark> reversed = new ArrayList<>(myBookmarks);
        Collections.reverse(reversed);

        for (Bookmark bookmark : reversed) {
            if (!bookmark.isValid()) continue;
            Element bookmarkElement = new Element("bookmark");

            bookmarkElement.setAttribute("url", bookmark.getFile().getUrl());

            String description = bookmark.getNotEmptyDescription();
            if (description != null) {
                bookmarkElement.setAttribute("description", description);
            }
            boolean simple = bookmark.isSimple();
            bookmarkElement.setAttribute("isSimple", String.valueOf(simple));

            int line = bookmark.getLine();
            if (line >= 0) {
                bookmarkElement.setAttribute("line", String.valueOf(line));
            }
            int column = bookmark.getColumn();
            if (line >= 0) {
                bookmarkElement.setAttribute("column", String.valueOf(column));
            }
            char mnemonic = bookmark.getMnemonic();
            if (mnemonic != 0) {
                bookmarkElement.setAttribute("mnemonic", String.valueOf(mnemonic));
            }

            element.addContent(bookmarkElement);
        }
    }

    /**
     * Try to move bookmark one position up in the list
     *
     * @return bookmark list after moving
     */
    @NotNull
    public List<Bookmark> moveBookmarkUp(@NotNull Bookmark bookmark) {
        final int index = myBookmarks.indexOf(bookmark);
        if (index > 0) {
            Collections.swap(myBookmarks, index, index - 1);
            EventQueue.invokeLater(() -> {
                myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(myBookmarks.get(index));
                myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(myBookmarks.get(index - 1));
            });
        }
        return myBookmarks;
    }


    /**
     * Try to move bookmark one position down in the list
     *
     * @return bookmark list after moving
     */
    @NotNull
    public List<Bookmark> moveBookmarkDown(@NotNull Bookmark bookmark) {
        final int index = myBookmarks.indexOf(bookmark);
        if (index < myBookmarks.size() - 1) {
            Collections.swap(myBookmarks, index, index + 1);
            EventQueue.invokeLater(() -> {
                myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(myBookmarks.get(index));
                myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(myBookmarks.get(index + 1));
            });
        }

        return myBookmarks;
    }

    @Nullable
    public Bookmark findLineBookmark(@NotNull Editor editor, boolean isWrapped, boolean next) {
        List<Bookmark> bookmarksForDocument = editor.getDocument().getUserData(BOOKMARKS_KEY);
        if (bookmarksForDocument == null) return null;
        int sign = next ? 1 : -1;
        Collections.sort(bookmarksForDocument, (o1, o2) -> sign * (o1.getLine() - o2.getLine()));
        int caretLine = editor.getCaretModel().getLogicalPosition().line;
        for (Bookmark bookmark : bookmarksForDocument) {
            if (next && bookmark.getLine() > caretLine) return bookmark;
            if (!next && bookmark.getLine() < caretLine) return bookmark;
        }
        return isWrapped && !bookmarksForDocument.isEmpty() ? bookmarksForDocument.get(0) : null;
    }

    public void setMnemonic(@NotNull Bookmark bookmark, char c) {
        final Bookmark old = findBookmarkForMnemonic(c);
        if (old != null) removeBookmark(old);

        bookmark.setMnemonic(c);
        myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
    }

    public void setDescription(@NotNull Bookmark bookmark, String description) {
        bookmark.setDescription(description);
        myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
    }

    public void colorsChanged() {
        for (Bookmark bookmark : myBookmarks) {
            bookmark.updateHighlighter();
        }
    }


    private class MyEditorMouseListener extends EditorMouseAdapter {
        @Override
        public void mouseClicked(final EditorMouseEvent e) {
            if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) return;
            if (e.getMouseEvent().isPopupTrigger()) return;
            if ((e.getMouseEvent().getModifiers() & (SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)) == 0)
                return;

            Editor editor = e.getEditor();
            LogicalPosition line = editor.xyToLogicalPosition(new Point(e.getMouseEvent().getX(), e.getMouseEvent().getY()));

            Document document = editor.getDocument();

            Bookmark bookmark = findEditorBookmark(document, line);
            if (bookmark == null) {
                addEditorBookmark(editor, line);
            } else {
                removeBookmark(bookmark);
            }
            e.consume();
        }
    }

    private class MyDocumentListener implements DocumentListener {
        @Override
        public void beforeDocumentChange(DocumentEvent e) {
            for (Bookmark bookmark : myBookmarks) {
                Document doc = bookmark.getDocument();
                if (doc == null || doc != e.getDocument()) continue;
                if (bookmark.getLine() == -1) continue;
                List<Trinity<Bookmark, Integer, String>> list = myBeforeChangeData.get(doc);
                if (list == null) {
                    myBeforeChangeData.put(doc, list = new ArrayList<>());
                }
                list.add(new Trinity<>(bookmark,
                                       bookmark.getLine(),
                                       doc.getText(new TextRange(doc.getLineStartOffset(bookmark.getLine()),
                                                                 doc.getLineEndOffset(bookmark.getLine())))));
            }
        }

        private boolean isDuplicate(Bookmark bookmark, @Nullable List<Bookmark> toRemove) {
            for (Bookmark b : myBookmarks) {
                if (b == bookmark) continue;
                if (!b.isValid()) continue;
                if (Comparing.equal(b.getFile(), bookmark.getFile()) && b.getLine() == bookmark.getLine()) {
                    if (toRemove == null || !toRemove.contains(b)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void moveToDeleted(Bookmark bookmark) {
            List<Trinity<Bookmark, Integer, String>> list = myBeforeChangeData.get(bookmark.getDocument());

            if (list != null) {
                for (Trinity<Bookmark, Integer, String> trinity : list) {
                    if (trinity.first == bookmark) {
                        removeBookmark(bookmark);
                        myDeletedDocumentBookmarks.put(new Trinity<>(bookmark.getFile(), trinity.second, trinity.third), bookmark);
                        break;
                    }
                }
            }
        }

        @Override
        public void documentChanged(DocumentEvent e) {
            if (!ApplicationManager.getApplication().isDispatchThread()) {
                return;// Changes in lightweight documents are irrelevant to bookmarks and have to be ignored
            }
            List<Bookmark> bookmarksToRemove = null;
            for (Bookmark bookmark : myBookmarks) {
                if (!bookmark.isValid() || isDuplicate(bookmark, bookmarksToRemove)) {
                    if (bookmarksToRemove == null) {
                        bookmarksToRemove = new ArrayList<>();
                    }
                    bookmarksToRemove.add(bookmark);
                }
            }

            if (bookmarksToRemove != null) {
                for (Bookmark bookmark : bookmarksToRemove) {
                    if (bookmark.getDocument() == e.getDocument()) {
                        moveToDeleted(bookmark);
                    } else {
                        removeBookmark(bookmark);
                    }
                }
            }

            myBeforeChangeData.remove(e.getDocument());

            for (Iterator<Map.Entry<Trinity<VirtualFile, Integer, String>, Bookmark>> iterator = myDeletedDocumentBookmarks.entrySet().iterator();
                 iterator.hasNext(); ) {
                Map.Entry<Trinity<VirtualFile, Integer, String>, Bookmark> entry = iterator.next();

                if (!entry.getKey().first.isValid()) {
                    iterator.remove();
                    continue;
                }

                Bookmark bookmark = entry.getValue();
                Document document = bookmark.getDocument();
                if (document == null || !bookmark.getFile().equals(entry.getKey().first)) {
                    continue;
                }
                Integer line = entry.getKey().second;
                if (document.getLineCount() <= line) {
                    continue;
                }

                String lineContent = getLineContent(document, line);

                String bookmarkedText = entry.getKey().third;
                //'move statement up' action kills line bookmark: fix for single line movement up/down
                if (!bookmarkedText.equals(lineContent)
                        && line > 1
                        && (bookmarkedText.equals(StringUtil.trimEnd(e.getNewFragment().toString(), "\n"))
                        ||
                        bookmarkedText.equals(StringUtil.trimEnd(e.getOldFragment().toString(), "\n")))) {
                    line -= 2;
                    lineContent = getLineContent(document, line);
                }
                if (bookmarkedText.equals(lineContent) && findEditorBookmark(document, new LogicalPosition(line, 0)) == null) {
                    Bookmark restored = addTextBookmark(bookmark.getFile(), new LogicalPosition(line, 0), bookmark.getDescription());
                    if (bookmark.getMnemonic() != 0) {
                        setMnemonic(restored, bookmark.getMnemonic());
                    }
                    iterator.remove();
                }
            }
        }

        private String getLineContent(Document document, int line) {
            int start = document.getLineStartOffset(line);
            int end = document.getLineEndOffset(line);
            return document.getText(new TextRange(start, end));
        }
    }
}
