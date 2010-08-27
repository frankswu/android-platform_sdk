/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.hierarchyviewerlib.ui;

import com.android.ddmlib.IDevice;
import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel.WindowChangeListener;

import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public class DeviceSelector extends Composite implements WindowChangeListener, SelectionListener {
    private TreeViewer treeViewer;

    private Tree tree;

    private DeviceSelectionModel model;

    private Font boldFont;

    private Image deviceImage;

    private Image emulatorImage;

    private final static int ICON_WIDTH = 16;

    private class ContentProvider implements ITreeContentProvider, ILabelProvider, IFontProvider {
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof IDevice) {
                Window[] list = model.getWindows((IDevice) parentElement);
                if (list != null) {
                    return list;
                }
            }
            return new Object[0];
        }

        public Object getParent(Object element) {
            if (element instanceof Window) {
                return ((Window) element).getDevice();
            }
            return null;
        }

        public boolean hasChildren(Object element) {
            if (element instanceof IDevice) {
                Window[] list = model.getWindows((IDevice) element);
                if (list != null) {
                    return list.length != 0;
                }
            }
            return false;
        }

        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof DeviceSelectionModel) {
                return model.getDevices();
            }
            return new Object[0];
        }

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        public Image getImage(Object element) {
            if (element instanceof IDevice) {
                if (((IDevice) element).isEmulator()) {
                    return emulatorImage;
                }
                return deviceImage;
            }
            return null;
        }

        public String getText(Object element) {
            if (element instanceof IDevice) {
                return ((IDevice) element).toString();
            } else if (element instanceof Window) {
                return ((Window) element).getTitle();
            }
            return null;
        }

        public Font getFont(Object element) {
            if (element instanceof Window) {
                int focusedWindow = model.getFocusedWindow(((Window) element).getDevice());
                if (focusedWindow == ((Window) element).getHashCode()) {
                    return boldFont;
                }
            }
            return null;
        }

        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

    public DeviceSelector(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        treeViewer = new TreeViewer(this, SWT.SINGLE);
        treeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);

        tree = treeViewer.getTree();
        tree.setLinesVisible(true);
        tree.addSelectionListener(this);

        addDisposeListener(disposeListener);

        loadResources();

        model = DeviceSelectionModel.getModel();
        ContentProvider contentProvider = new ContentProvider();
        treeViewer.setContentProvider(contentProvider);
        treeViewer.setLabelProvider(contentProvider);
        model.addWindowChangeListener(this);
        treeViewer.setInput(model);

        addControlListener(controlListener);
    }

    public void loadResources() {
        Display display = Display.getDefault();
        Font systemFont = display.getSystemFont();
        FontData[] fontData = systemFont.getFontData();
        FontData[] newFontData = new FontData[fontData.length];
        for (int i = 0; i < fontData.length; i++) {
            newFontData[i] =
                    new FontData(fontData[i].getName(), fontData[i].getHeight(), fontData[i]
                            .getStyle()
                            | SWT.BOLD);
        }
        boldFont = new Font(Display.getDefault(), newFontData);

        ImageLoader loader = ImageLoader.getDdmUiLibLoader();
        deviceImage =
                loader.loadImage(display, "device.png", ICON_WIDTH, ICON_WIDTH, display
                        .getSystemColor(SWT.COLOR_RED));

        emulatorImage =
                loader.loadImage(display, "emulator.png", ICON_WIDTH, ICON_WIDTH, display
                        .getSystemColor(SWT.COLOR_BLUE));
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeWindowChangeListener(DeviceSelector.this);
            boldFont.dispose();
        }
    };

    // If the window gets too small, hide the data, otherwise SWT throws an
    // ERROR.

    private ControlListener controlListener = new ControlAdapter() {
        private boolean noInput = false;

        @Override
        public void controlResized(ControlEvent e) {
            if (getBounds().height <= 38) {
                treeViewer.setInput(null);
                noInput = true;
            } else if (noInput) {
                treeViewer.setInput(model);
                noInput = false;
            }
        }
    };

    @Override
    public boolean setFocus() {
        return tree.setFocus();
    }

    public void deviceConnected(final IDevice device) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                treeViewer.refresh();
                treeViewer.setExpandedState(device, true);
            }
        });
    }

    public void deviceChanged(final IDevice device) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                TreeSelection selection = (TreeSelection) treeViewer.getSelection();
                treeViewer.refresh(device);
                if (selection.getFirstElement() instanceof Window
                        && ((Window) selection.getFirstElement()).getDevice() == device) {
                    treeViewer.setSelection(selection, true);
                }
            }
        });
    }

    public void deviceDisconnected(final IDevice device) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                treeViewer.refresh();
            }
        });
    }

    public void focusChanged(final IDevice device) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                TreeSelection selection = (TreeSelection) treeViewer.getSelection();
                treeViewer.refresh(device);
                if (selection.getFirstElement() instanceof Window
                        && ((Window) selection.getFirstElement()).getDevice() == device) {
                    treeViewer.setSelection(selection, true);
                }
            }
        });
    }

    public void selectionChanged(IDevice device, Window window) {
        // pass
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        Object selection = ((TreeItem) e.item).getData();
        if (selection instanceof IDevice) {
            HierarchyViewerDirector.getDirector().loadPixelPerfectData((IDevice) selection);
        } else if (selection instanceof Window) {
            HierarchyViewerDirector.getDirector().loadViewTreeData((Window) selection);
        }
    }

    public void widgetSelected(SelectionEvent e) {
        Object selection = ((TreeItem) e.item).getData();
        if (selection instanceof IDevice) {
            model.setSelection((IDevice) selection, null);
        } else if (selection instanceof Window) {
            model.setSelection(((Window) selection).getDevice(), (Window) selection);
        }
    }
}