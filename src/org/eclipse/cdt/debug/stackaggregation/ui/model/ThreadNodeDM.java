package org.eclipse.cdt.debug.stackaggregation.ui.model;

import org.eclipse.cdt.dsf.datamodel.IDMContext;

public class ThreadNodeDM 
	implements ITreeNode {
	
	private IDMContext fThreadContext;
	private StackNodeDM fParent;
	
	public ThreadNodeDM (StackNodeDM parent, IDMContext context) {
		fThreadContext = context;
		fParent = parent;
	}
	
	public IDMContext getContext () {
		return fThreadContext;
	}

	@Override
	public ITreeNode getParent () {
		// TODO Auto-generated method stub
		return fParent;
	}

	@Override
	public ITreeNode[] getChildren () {
		return new ITreeNode[0];
	}

	@Override
	public boolean hasChildren () {
		return false;
	}

}
