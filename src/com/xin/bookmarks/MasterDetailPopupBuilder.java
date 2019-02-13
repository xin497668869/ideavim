// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.xin.bookmarks;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ActiveComponent;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.util.DetailController;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.ui.popup.util.DetailViewImpl;
import com.intellij.ui.popup.util.ItemWrapper;
import com.intellij.ui.popup.util.ItemWrapperListRenderer;
import com.intellij.ui.popup.util.MasterController;
import com.intellij.ui.popup.util.SplitterItem;
import com.intellij.ui.speedSearch.FilteringListModel;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.speedSearch.SpeedSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class MasterDetailPopupBuilder implements MasterController {

    private static final Color BORDER_COLOR = Gray._135;

    private final Project          myProject;
    private final DetailController myDetailController = new DetailController(this);

    private JComponent myChooserComponent;
    private Delegate   myDelegate;
    private DetailView myDetailView;
    private JLabel     myPathLabel;
    private JBPopup    myPopup;

    private String                                 myDimensionServiceKey = null;
    private boolean                                myAddDetailViewToEast = true;
    private ActionGroup                            myActions             = null;
    private Consumer<? super IPopupChooserBuilder> myPopupTuner          = null;
    private Runnable                               myDoneRunnable        = null;

    public MasterDetailPopupBuilder(Project project) {
        myProject = project;
    }

    @NotNull
    public MasterDetailPopupBuilder setList(@NotNull JBList list) {
        myChooserComponent = list;
        myDetailController.setList(list);

//        list.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyPressed(KeyEvent e) {
//                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
//                    removeSelectedItems();
//                }
//                else if (e.getModifiersEx() == 0) {
//                    myDelegate.handleMnemonic(e, myProject, myPopup);
//                }
//            }
//        });
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                chooseItems(true);
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, list);
        new AnAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                chooseItems(true);
            }
        }.registerCustomShortcutSet(CommonShortcuts.DOUBLE_CLICK_1, list);

        list.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                myDetailController.updateDetailView();
            }
        });

        return this;
    }

    private void removeSelectedItems() {
        if (myChooserComponent instanceof JList) {
            JList list = (JList) myChooserComponent;
            ListModel listModel = list.getModel();

            int index = list.getSelectedIndex();
            if (index == -1 || index >= listModel.getSize()) {
                return;
            }

            @SuppressWarnings("deprecation") Object[] values = list.getSelectedValues();
            for (Object value : values) {
                ItemWrapper item = (ItemWrapper) value;
                if (item.allowedToRemove()) {
                    DefaultListModel model = listModel instanceof DefaultListModel ?
                            (DefaultListModel) listModel :
                            (DefaultListModel) ((FilteringListModel) listModel).getOriginalModel();

                    model.removeElement(item);

                    if (model.getSize() > 0) {
                        if (model.getSize() == index) {
                            list.setSelectedIndex(model.getSize() - 1);
                        } else if (model.getSize() > index) {
                            list.setSelectedIndex(index);
                        }
                    } else {
                        list.clearSelection();
                    }

                    item.removed(myProject);
                }
            }
        } else {
            myDelegate.removeSelectedItemsInTree();
        }
    }

    private void chooseItems(boolean withEnterOrDoubleClick) {
        for (Object item : getSelectedItems()) {
            if (item instanceof ItemWrapper) {
                myDelegate.itemChosen((ItemWrapper) item, myProject, myPopup, withEnterOrDoubleClick);
            }
        }
    }

    @NotNull
    public MasterDetailPopupBuilder setDelegate(@NotNull Delegate delegate) {
        myDelegate = delegate;
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setDetailView(@NotNull DetailView detailView) {
        myDetailView = detailView;
        myDetailController.setDetailView(myDetailView);
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setDimensionServiceKey(@Nullable String dimensionServiceKey) {
        myDimensionServiceKey = dimensionServiceKey;
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setAddDetailViewToEast(boolean addDetailViewToEast) {
        myAddDetailViewToEast = addDetailViewToEast;
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setActionsGroup(@Nullable ActionGroup actions) {
        myActions = actions;
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setPopupTuner(@Nullable Consumer<? super IPopupChooserBuilder> tuner) {
        myPopupTuner = tuner;
        return this;
    }

    @NotNull
    public MasterDetailPopupBuilder setDoneRunnable(@Nullable Runnable doneRunnable) {
        myDoneRunnable = doneRunnable;
        return this;
    }

    @NotNull
    public JBPopup createMasterDetailPopup() {

        if (myDetailView == null) {
            setDetailView(new DetailViewImpl(myProject));
        }

        myPathLabel = new JLabel(" ");
        myPathLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        myPathLabel.setFont(myPathLabel.getFont().deriveFont((float) 10));

        JPanel footerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(BORDER_COLOR);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        footerPanel.setBorder(JBUI.Borders.empty(4, 4, 4, SystemInfo.isMac ? 20 : 4));
        footerPanel.add(myPathLabel);

        Runnable itemCallback = () -> IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> chooseItems(false));

        JComponent toolBar = null;
        if (myActions != null) {
            ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("MasterDetailPopup", myActions, true);
            actionToolbar.setReservePlaceAutoPopupIcon(false);
            actionToolbar.setMinimumButtonSize(new Dimension(20, 20));
            toolBar = actionToolbar.getComponent();
            toolBar.setBorder(JBUI.Borders.merge(toolBar.getBorder(), JBUI.Borders.emptyLeft(12), true));
            toolBar.setOpaque(false);
        }

        MyPopupChooserBuilder builder = (MyPopupChooserBuilder) createInnerBuilder().
                setMovable(true).
                setFilteringEnabled(new Function<Object, String>() {
                    @Override
                    public String fun(Object o) {
                        return ((BookmarkItem) o).getBookmark().getDescription();
                    }
                }).
                setResizable(true).
                setAutoselectOnMouseMove(false).
                setMayBeParent(true).
                setDimensionServiceKey(myDimensionServiceKey).
                setUseDimensionServiceForXYLocation(myDimensionServiceKey != null).
                setSettingButton(toolBar).
                setSouthComponent(footerPanel).
                setItemChoosenCallback(itemCallback);
        //setFilteringEnabled(o -> ((ItemWrapper)o).speedSearchText());

        if (myPopupTuner != null) {
            myPopupTuner.consume(builder);
        }

        if (myDoneRunnable != null) {
            ActionListener actionListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    myDoneRunnable.run();
                }
            };

            if ((SystemInfo.isMacOSLion || SystemInfo.isMacOSMountainLion) && !UIUtil.isUnderDarcula()) {
                final JButton done = new JButton("Done");
                done.setOpaque(false);
                done.setMnemonic('o');
                done.addActionListener(actionListener);
                builder.setCommandButton(new ActiveComponent.Adapter() {
                    @Override
                    public JComponent getComponent() {
                        return done;
                    }
                });
            } else {
                IconButton close = new IconButton("Close!!!!", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
                builder.setCommandButton(new InplaceButton(close, actionListener));
            }
        }

        String title = myDelegate.getTitle();
        if (title != null) {
            builder.setTitle(title);
        }

        myPopup = builder.createPopup();
        builder.getScrollPane().setBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT));

        myPopup.addListener(new JBPopupListener() {
            @Override
            public void onClosed(@NotNull LightweightWindowEvent event) {
                myDetailView.clearEditor();
            }
        });

        if (myDoneRunnable != null) {
            new AnAction("Done") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    myDoneRunnable.run();
                }
            }.registerCustomShortcutSet(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK, myPopup.getContent());
        }

        if (myChooserComponent instanceof JList) {
            //noinspection unchecked
            ((JList) myChooserComponent).setCellRenderer(new ListItemRenderer(myProject, myDelegate, builder.getListWithFilter()));
        }
        return myPopup;
    }

    private MyPopupChooserBuilder createInnerBuilder() {
        if (myChooserComponent instanceof JList) {
            return new MyPopupChooserBuilder((JList) myChooserComponent);
        } else if (myChooserComponent instanceof JTree) {
            return new MyPopupChooserBuilder((JTree) myChooserComponent);
        }
        throw new IllegalStateException("Incorrect chooser component: " + myChooserComponent);
    }

    @Override
    public ItemWrapper[] getSelectedItems() {
        Object[] values = ArrayUtil.EMPTY_OBJECT_ARRAY;
        if (myChooserComponent instanceof JList) {
            //noinspection deprecation
            values = ((JList) myChooserComponent).getSelectedValues();
        } else if (myChooserComponent instanceof JTree) {
            values = myDelegate.getSelectedItemsInTree();
        }
        ItemWrapper[] items = new ItemWrapper[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = (ItemWrapper) values[i];
        }
        return items;
    }

    @Override
    public JLabel getPathLabel() {
        return myPathLabel;
    }

    private String getSplitterProportionKey() {
        return myDimensionServiceKey != null ? myDimensionServiceKey + ".splitter" : null;
    }

    public interface Delegate {
        @Nullable
        String getTitle();

        void handleMnemonic(KeyEvent e, Project project, JBPopup popup);

        @Nullable
        JComponent createAccessoryView(Project project);

        Object[] getSelectedItemsInTree();

        void itemChosen(ItemWrapper item, Project project, JBPopup popup, boolean withEnterOrDoubleClick);

        void removeSelectedItemsInTree();
    }

    private static class ListItemRenderer extends JPanel implements ListCellRenderer {
        private final Project                 myProject;
        private final ColoredListCellRenderer myRenderer;
        private final Delegate                myDelegate;
        private final SpeedSearch             speedSearch;

        private ListItemRenderer(Project project, Delegate delegate, ListWithFilter listWithFilter) {
            super(new BorderLayout());
            myProject = project;
            myDelegate = delegate;
            speedSearch = listWithFilter.getSpeedSearch();
            setBackground(UIUtil.getListBackground());

            JComponent accessory = myDelegate.createAccessoryView(project);
            if (accessory != null) {
                add(accessory, BorderLayout.WEST);
            }

            myRenderer = new ItemWrapperListRenderer(myProject, accessory);
            add(myRenderer, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof SplitterItem) {
                String label = ((SplitterItem) value).getText();
                TitledSeparator separator = new TitledSeparator(label);
                separator.setBackground(UIUtil.getListBackground());
                separator.setForeground(UIUtil.getListForeground());
                return separator;
            } else {
                myRenderer.putClientProperty("speedSearch", speedSearch);
                myRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                myRenderer.revalidate();
                return this;
            }
        }
    }

    private class MyPopupChooserBuilder extends PopupChooserBuilder {
        private ListWithFilter listWithFilter;

        MyPopupChooserBuilder(@NotNull JList list) {
            super(list);
        }

        private MyPopupChooserBuilder(@NotNull JTree tree) {
            super(tree);
        }

        public ListWithFilter getListWithFilter() {
            return listWithFilter;
        }

        @Override
        protected void addCenterComponentToContentPane(JPanel contentPane, JComponent component) {
            if (myAddDetailViewToEast) {
                listWithFilter = (ListWithFilter) component;
                JBSplitter splitPane = new JBSplitter(0.3f);
                splitPane.setSplitterProportionKey(getSplitterProportionKey());
                JPanel jPanel = new JPanel(new BorderLayout());
                jPanel.add(component, BorderLayout.CENTER);
                splitPane.setFirstComponent(jPanel);
                splitPane.setSecondComponent((JComponent) myDetailView);
                contentPane.add(splitPane, BorderLayout.CENTER);
            } else {
                super.addCenterComponentToContentPane(contentPane, component);
            }
        }
    }
}