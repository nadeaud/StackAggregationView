package org.eclipse.cdt.debug.stackaggregation.ui.dsf;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.cdt.debug.stackaggregation.ui.model.ITreeNode;
import org.eclipse.cdt.debug.stackaggregation.ui.model.StackNodeDM;
import org.eclipse.cdt.debug.stackaggregation.ui.model.ThreadNodeDM;
import org.eclipse.cdt.debug.stackaggregation.ui.views.StackAggregationView;
import org.eclipse.cdt.dsf.concurrent.CountingRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.DataRequestMonitor;
import org.eclipse.cdt.dsf.concurrent.DsfExecutor;
import org.eclipse.cdt.dsf.concurrent.DsfRunnable;
import org.eclipse.cdt.dsf.concurrent.IDsfStatusConstants;
import org.eclipse.cdt.dsf.datamodel.IDMContext;
import org.eclipse.cdt.dsf.debug.service.IProcesses;
import org.eclipse.cdt.dsf.debug.service.IStack;
import org.eclipse.cdt.dsf.debug.service.IRunControl.IExecutionDMContext;
import org.eclipse.cdt.dsf.debug.service.IStack.IFrameDMContext;
import org.eclipse.cdt.dsf.debug.service.IStack.IFrameDMData;
import org.eclipse.cdt.dsf.debug.service.command.ICommandControlService;
import org.eclipse.cdt.dsf.gdb.launching.GDBProcess;
import org.eclipse.cdt.dsf.gdb.launching.GdbLaunch;
import org.eclipse.cdt.dsf.internal.DsfPlugin;
import org.eclipse.cdt.dsf.internal.ui.DsfUIPlugin;
import org.eclipse.cdt.dsf.mi.service.IMIExecutionDMContext;
import org.eclipse.cdt.dsf.service.DsfServicesTracker;
import org.eclipse.cdt.dsf.service.DsfSession;
import org.eclipse.cdt.dsf.ui.viewmodel.datamodel.IDMVMContext;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.ui.DebugUITools;

public class StackAggregationDebug 
	implements IViewUpdateTrigger {

	
	private DsfSession fSession;
	private DsfServicesTracker fServiceTracker;
	private StackAggregationView fView;
	private StackAggregationEventListener fEventListener;
	
	public StackAggregationDebug(StackAggregationView view) {
		fView = view;
		updateDebugContext();
		if(fSession != null) {
			fServiceTracker = new DsfServicesTracker(DsfUIPlugin.getBundleContext(), fSession.getId());
			fEventListener = new StackAggregationEventListener(this);
			addEventListener(fEventListener);
		}
	}
	
	@Override
	public void updateView() {
		DataRequestMonitor<StackNodeDM> _rm = 
				new DataRequestMonitor<StackNodeDM>(getExecutor(), null) {
			@Override
			protected void handleCompleted() {
				if(! isSuccess()) {
					return;
				}
				StackNodeDM node = getData();
				if(node == null) {
					return;
				}
				fView.triggerRedraw(node);
			}
		};
		getProcesses(_rm);
	}
	
	public void updateRoot(DataRequestMonitor<StackNodeDM> rm) {
		DataRequestMonitor<StackNodeDM> _rm = 
				new DataRequestMonitor<StackNodeDM>(getExecutor(), rm) {
			@Override
			protected void handleCompleted() {
				if(! isSuccess()) {
					return;
				}
				rm.setData(getData());
				rm.done();
			}
		};
		getProcesses(_rm);
	}
	
	private void addEventListener(StackAggregationEventListener listener) {
		if (fSession == null)
			return;
		
		try {
			fSession.getExecutor().execute(new DsfRunnable() {
				
				@Override
				public void run() {
					fSession.addServiceEventListener(listener, null);
				}
			});
		} catch (RejectedExecutionException e) {
			
		}
	}
	
	private void removeEventListener () {
		if (fSession == null || fEventListener == null)
			return;
		
		try {
			fSession.getExecutor().execute(new DsfRunnable() {
				
				@Override
				public void run() {
					fSession.removeServiceEventListener(fEventListener);
					fEventListener = null;
				}
			});
		} catch (RejectedExecutionException e) {
			
		}
	}
	
	protected DsfExecutor getExecutor() {
		if(fSession != null) {
			return fSession.getExecutor();
		}
		return null;
	}
	
	protected <V> V getService(Class<V> serviceClass) {
		return fServiceTracker.getService(serviceClass);
	}
	
	/* Function copied from MulticoreVisualizer.java. */
	protected boolean updateDebugContext() {
		String sessionId = null;
		IAdaptable debugContext = DebugUITools.getDebugContext();
		if (debugContext instanceof IDMVMContext) {
			sessionId = ((IDMVMContext)debugContext).getDMContext().getSessionId();
		} else if (debugContext instanceof GdbLaunch) {
			GdbLaunch gdbLaunch = (GdbLaunch)debugContext;
			if (gdbLaunch.isTerminated() == false) {
				sessionId = gdbLaunch.getSession().getId();
			}
		} else if (debugContext instanceof GDBProcess) {
			ILaunch launch = ((GDBProcess)debugContext).getLaunch();
			if (launch.isTerminated() == false &&
					launch instanceof GdbLaunch) {
				sessionId = ((GdbLaunch)launch).getSession().getId();
			}
		}
		return setDebugSession(sessionId);
	}
	
	/** Copied from MulticoreVisualizer.java 
	 *  Sets debug context being displayed by canvas.
	 *  Returns true if canvas context actually changes, false if not.
	 */
	protected boolean setDebugSession(String sessionId) {
		boolean changed = false;

		if (fSession != null &&
			! fSession.getId().equals(sessionId))
		{
			removeEventListener();
			fSession = null;
			changed = true;
		}
		
		if (fSession == null &&
			sessionId != null)
		{
			fSession = DsfSession.getSession(sessionId);
			changed = true;
		}
		
		return changed;
	}

	/** Functions to build the tree data structure. */
	
	/* Function for debugging. */
	private void print_debug_tree(StackNodeDM root, int level) {
		if(level == 0)
			System.out.println("-----------------------------------");
		String tab = "";
		for(int i = 0; i < level; i++) {
			tab += "\t";
		}
		String threads = "";
		for(ThreadNodeDM thread : root.getThreads()) {
			IDMContext context = thread.getContext();
			if(context instanceof IMIExecutionDMContext) {
				threads += ((IMIExecutionDMContext)context).getThreadId() + ", ";
			}
		}
		System.out.println(tab + root.getId() + ", threads : " + threads);
		for(ITreeNode node : root.getChildren()) {
			if (node instanceof StackNodeDM) {
				print_debug_tree((StackNodeDM)node, level+1);
			}
		}
	}
	
	private StackNodeDM mergeTrees(StackNodeDM first, StackNodeDM second) {
		if(first == null && second == null)
			return null;
		if(first == null)
			return second;
		if(second == null)
			return first;
		if(first.isLeaf() && second.isLeaf())
			return first.addThreads(second);
		if(first.isLeaf())
			return second.addThreads(first);
		if(second.isLeaf())
			return first.addThreads(second);
		
		StackNodeDM node = new StackNodeDM(first.getId(), first.getParent());
		node.addAll(first);
		node.addAll(second);
		node.addThreads(first).addThreads(second);
		for(Map.Entry<String, StackNodeDM> e : node.getMap().entrySet()) {
			e.setValue(mergeTrees(first.getChild(e.getKey()), second.getChild(e.getKey())));
		}
		return node;
	}
	
	private StackNodeDM mergeTreeArray(StackNodeDM[] roots) {
		StackNodeDM root = new StackNodeDM(null, null);
		if( roots.length <= 0 ) 
			return root;
		
		root = roots[0];
		for(int i = 1; i < roots.length; i++) {
			root = mergeTrees(root, roots[i]);
		}
		return root;
	}
	
	/** 
	 * Retrieve the data (function name) for each frame of the callstack.
	 * @param contexts : the context for the frames
	 * @param rm : the datarequestmonitor to be filled with the function names.
	 */
	private void getFrameData(IFrameDMContext[] contexts, 
			DataRequestMonitor<String[]>rm) {
		IStack service = fServiceTracker.getService(IStack.class);

		CountingRequestMonitor crm = new CountingRequestMonitor(
				fSession.getExecutor(), rm);
		crm.setDoneCount(contexts.length);
		
		String[] array = new String[contexts.length];
		rm.setData(array);
		
		for (int i = 0; i < contexts.length; ++i) {
			int index = i;
			IFrameDMContext context = contexts[i];
			service.getFrameData(context, 
					new DataRequestMonitor<IFrameDMData>(fSession.getExecutor(), 
							crm) {
				@Override
				protected void handleSuccess() {
					if(!isSuccess()) {
						crm.done();
						return;
					}
					array[index] =  getData().getFunction();
					crm.done();
					return;
				}
			});
		}
	}
	
	/**
	 * Retrieve the callstack for each thread.
	 * @param contexts Array of IDMContext where each item represents a thread.
	 * @param rm RequestMonitor to fill with an array of StackNodeDM, each
	 * 			representing the root for a thread.
	 */
	private void getStackForThreads(IDMContext[] contexts, DataRequestMonitor<StackNodeDM[]> rm) {
		
		CountingRequestMonitor crm = new CountingRequestMonitor(fSession.getExecutor(), rm);
		crm.setDoneCount(contexts.length);
		
		/* Replace by a CountingRequestMonitor with data ? */
		StackNodeDM[] nodes = new StackNodeDM[contexts.length];
		rm.setData(nodes);
		
		for (int i = 0; i < contexts.length; ++i) {
			IDMContext context = contexts[i];
			int index = i;
			if (! (context instanceof IExecutionDMContext))
				continue;
			
			fSession.getExecutor().execute(new DsfRunnable() {
				
				@Override
				public void run() {
					IStack stackService = fServiceTracker.getService(IStack.class);
					stackService.getFrames(
							context,
							new DataRequestMonitor<IFrameDMContext []>(fSession.getExecutor(), crm) {
								@Override
								public void handleCompleted() {
									if(!isSuccess()) {
										crm.done();
										return;
									}
									DataRequestMonitor<String[]> _rm = 
											new DataRequestMonitor<String[]>(getExecutor(), crm){
										@Override
										protected void handleCompleted() {
											if(! isSuccess()) {
												crm.done();
												return;
											}
											StackNodeDM root = new StackNodeDM(null, null);
											StackNodeDM child = root;
											String[] result = getData();
											String temp = "";
											for(int i = result.length - 1; i >= 0; --i) {
												temp += result[i] + ",";
												child = child.add(result[i]);
											}
											System.out.println(temp);
											child.addThread(context);
											nodes[index] = root;
											crm.done();
										}
									};
									getFrameData(getData(), _rm);
									return;
								}
							});
				}
			});			
		}
	}
	
	/**
	 * Retrieve threads for each process
	 * @param contexts Array of IDMContext representing processes.
	 * @param rm
	 */
	private void getAllThreads(IDMContext[] contexts, DataRequestMonitor<StackNodeDM[]> rm) {
		DsfExecutor exec = getExecutor();
		CountingRequestMonitor crm = new CountingRequestMonitor(exec, rm);
		crm.setDoneCount(contexts.length);
		
		StackNodeDM[] array = new StackNodeDM[contexts.length];
		rm.setData(array);		
		
		for(int i = 0; i < contexts.length; ++i) {
			int index = i;
			IDMContext context = contexts[i];
			getExecutor().execute(new DsfRunnable() {
				@Override
				public void run() {
					IProcesses procService = getService(IProcesses.class);

					procService.getProcessesBeingDebugged(context, 
							new DataRequestMonitor<IDMContext[]>(exec, crm) {
						@Override
						public void handleCompleted() {
							if(! isSuccess()) {
								crm.done();
								return;
							}
							
							DataRequestMonitor<StackNodeDM[]> _rm =
									new DataRequestMonitor<StackNodeDM[]>(getExecutor(),
											crm) {
										@Override
										protected void handleCompleted() {
											if(! isSuccess()) {
												crm.done();
												return;
											}
											StackNodeDM root = mergeTreeArray(getData());
											array[index] = root;
											crm.done();											
										}
										
									};
							
							getStackForThreads(getData(), _rm);
							return;
						}
					});

				}
			});
		}
	}
	
	/**
	 * Retrieve each processes and return the tree in the requestmonitor.
	 * @param rm
	 */
	private void getProcesses( DataRequestMonitor<StackNodeDM> rm) {
		if (fSession == null && updateDebugContext() == false) {
			//rm.cancel();
			rm.done(new StackNodeDM(null, null));
			IStatus status = new Status(IStatus.ERROR, DsfPlugin.PLUGIN_ID, IDsfStatusConstants.INTERNAL_ERROR, 
					"No Dsf Session : cannot retrieve processes.", null);//$NON-NLS-1$ //$NON-NLS-2$
			//rm.setStatus(status);
			//rm.done(status);
			return;			
		}
		getExecutor().execute(new DsfRunnable() {
			@Override
			public void run() {
				IProcesses procService = getService(IProcesses.class);
				ICommandControlService controlService = getService(ICommandControlService.class);
				
				if(controlService == null || procService == null) {
					rm.done();
					return;
				}
				
				procService.getProcessesBeingDebugged(controlService.getContext(), 
						new DataRequestMonitor<IDMContext[]>(getExecutor(), 
								rm){
							@Override
							public void handleCompleted() {
								if(! isSuccess()) {
									rm.done();
									return;
								}
								DataRequestMonitor<StackNodeDM[]> _rm = 
										new DataRequestMonitor<StackNodeDM[]>(getExecutor(),
												rm) {
									@Override
									protected void handleCompleted() {
										if(! isSuccess()) {
											rm.done();
											return;
										}
										StackNodeDM root = mergeTreeArray(getData());
										rm.setData(root);
										rm.done();
									}
								};
								getAllThreads(getData(), _rm);
								return;
							}
				});
			}
		});
	}	

}
