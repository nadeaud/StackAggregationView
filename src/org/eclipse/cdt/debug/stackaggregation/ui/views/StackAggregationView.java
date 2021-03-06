package org.eclipse.cdt.debug.stackaggregation.ui.views;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.cdt.debug.stackaggregation.ui.dsf.StackAggregationDebug;
import org.eclipse.cdt.debug.stackaggregation.ui.model.StackNodeDM;
import org.eclipse.cdt.debug.stackaggregation.ui.model.ThreadNodeDM;
import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.ImmediateDataRequestMonitor;
import org.eclipse.cdt.dsf.datamodel.IDMContext;
import org.eclipse.cdt.dsf.debug.service.IStack.IFrameDMData;
import org.eclipse.cdt.dsf.mi.service.IMIExecutionDMContext;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.ui.CDTSharedImages;
import org.eclipse.core.internal.resources.ContentDescriptionManager;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;


/**
 * This sample class demonstrates how to plug-in a new
 * workbench view. The view shows data obtained from the
 * model. The sample creates a dummy model on the fly,
 * but a real implementation would connect to the model
 * available either in this or another plug-in (e.g. the workspace).
 * The view is connected to the model using a content provider.
 * <p>
 * The view uses a label provider to define how model
 * objects should be presented in the view. Each
 * view can present the same model objects using
 * different labels and icons, if needed. Alternatively,
 * a single label provider can be shared between views
 * in order to ensure that objects of the same type are
 * presented in the same way everywhere.
 * <p>
 */

public class StackAggregationView extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.eclipse.cdt.debug.stackaggregation.ui.views.StackAggregationView";

	private TreeViewer viewer;
	private DrillDownAdapter drillDownAdapter;
	private Action action1;
	private Action action2;
	private Action doubleClickAction;
	
	private StackAggregationDebug fDebugHandler;
	private ViewContentProvider fViewContentProvider;	

	class ViewContentProvider implements ITreeContentProvider {
		private StackNodeDM fRoot;
		private StackAggregationView fView;
		
		public ViewContentProvider(StackAggregationView view) {
			fView = view;
		}

		public Object[] getElements(Object parent) {
			if (parent.equals(getViewSite())) {
				if (fRoot==null) initialize();
				return getChildren(fRoot);
			}
			return getChildren(parent);
		}
		public Object getParent(Object child) {
			if (child instanceof StackNodeDM) {
				return ((StackNodeDM)child).getParent();
			}
			return null;
		}
		public Object [] getChildren(Object parent) {
			if (parent instanceof StackNodeDM) {
				return ((StackNodeDM)parent).getChildren();
			}
			return new Object[0];
		}
		public boolean hasChildren(Object parent) {
			if(parent instanceof StackNodeDM)
				return ((StackNodeDM)parent).hasChildren();
			return false;
		}
/*
 * We will set up a dummy model to initialize tree heararchy.
 * In a real code, you will connect to a real model and
 * expose its hierarchy.
 */
		private void initialize() { 
			if(DsfSession.getActiveSessions().length == 0) 
				return;
			
			ImmediateDataRequestMonitor<StackNodeDM> rm = new ImmediateDataRequestMonitor<StackNodeDM>() {
				@Override
				public void handleCompleted() {
					if( !isSuccess()) {
						return;
					}
					StackNodeDM root = getData();
					fRoot = root;
					Display.getDefault().asyncExec(new Runnable() {
						
						@Override
						public void run() {
							fView.viewer.refresh();
							viewer.expandAll();
						}
					});
				}
			};
			fView.fDebugHandler.updateRoot(rm);
		}
	}

	class ViewLabelProvider extends LabelProvider {

		public String getText(Object obj) {
			if (obj instanceof StackNodeDM) {
				IFrameDMData data = ((StackNodeDM)obj).getData();
				return data.getFunction() + "()";
			}
			else if (obj instanceof ThreadNodeDM && 
					((ThreadNodeDM)obj).getContext() instanceof IMIExecutionDMContext) {
				IMIExecutionDMContext ctx = (IMIExecutionDMContext)((ThreadNodeDM)obj).getContext();
				return "Thread #" + ctx.getThreadId();
			}
			return obj.toString();
		}
		public Image getImage(Object obj) {
			if (obj instanceof ThreadNodeDM) {
				String imagekey = CDTSharedImages.IMG_THREAD_SUSPENDED_B_PINNED;
				return CDTSharedImages.getImage(imagekey);
			}
			else if (obj instanceof StackNodeDM) {
				String imageKey = IDebugUIConstants.IMG_OBJS_STACKFRAME;
				return DebugUITools.getImage(imageKey);				
			}
			String imageKey = IDebugUIConstants.IMG_OVR_ERROR;
			return PlatformUI.getWorkbench().getSharedImages().getImage(imageKey);
			}
	}

/**
	 * The constructor.
	 */
	public StackAggregationView() {
	}
	
	public void triggerRedraw(StackNodeDM root) {
		fViewContentProvider.fRoot = root;
		Display.getDefault().asyncExec(new Runnable() {
			
			@Override
			public void run() {
				viewer.refresh();
				viewer.expandAll();
			}
		});
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		drillDownAdapter = new DrillDownAdapter(viewer);
		
		GridLayout layout = new GridLayout(2,false);
		parent.setLayout(layout);
		
		GridData gridData = new GridData();
		gridData.verticalAlignment = SWT.FILL;
		gridData.horizontalSpan = 2;
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = SWT.FILL;
		
		fDebugHandler = new StackAggregationDebug(this);
		fViewContentProvider = new ViewContentProvider(this);
		viewer.setContentProvider(fViewContentProvider);
		viewer.setInput(getViewSite());
		viewer.setLabelProvider(new ViewLabelProvider());
		viewer.getControl().setLayoutData(gridData);

		/* Add the buttont. */
		Button b = new Button(parent, SWT.PUSH);
		b.setText("Set Selection");
		b.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection select = viewer.getSelection();
				showMessage("Double-click detected on "+ e.toString());
				if(select instanceof TreeSelection) {
					TreeSelection treeSelect = (TreeSelection)select;
					List<IDMContext> ctxs = new ArrayList<IDMContext>();
					for(Object s : treeSelect.toList()) {
						if(s instanceof ThreadNodeDM) {
							IDMContext c = ((ThreadNodeDM)s).getContext();
							ctxs.add(c);
						}
					}
					fDebugHandler.add_thread_to_filter(ctxs.toArray(new IDMContext[0]));
				}
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});
		
		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), "org.eclipse.cdt.debug.stackaggregation.ui.viewer");
		getSite().setSelectionProvider(viewer);
		//makeActions();
		//hookContextMenu();
		//hookDoubleClickAction();
		//contributeToActionBars();
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				StackAggregationView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(action1);
		manager.add(new Separator());
		manager.add(action2);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(action1);
		manager.add(action2);
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(action1);
		manager.add(action2);
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
	}

	private void makeActions() {
		action1 = new Action() {
			public void run() {
				showMessage("Action 1 executed");
			}
		};
		action1.setText("Action 1");
		action1.setToolTipText("Action 1 tooltip");
		action1.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
			getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		
		action2 = new Action() {
			public void run() {
				showMessage("Action 2 executed");
			}
		};
		action2.setText("Action 2");
		action2.setToolTipText("Action 2 tooltip");
		action2.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
				getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK));
		doubleClickAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection)selection).getFirstElement();
				if(obj instanceof ThreadNodeDM) {
					ThreadNodeDM th = (ThreadNodeDM) obj;
					//fDebugHandler.add_thread_to_filter(th.getContext());
				}
				showMessage("Double-click detected on "+obj.toString());
			}
		};
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});
	}
	private void showMessage(String message) {
		MessageDialog.openInformation(
			viewer.getControl().getShell(),
			"Stack View",
			message);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}
}
