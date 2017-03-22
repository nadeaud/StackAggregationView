package org.eclipse.cdt.debug.stackaggregation.ui.model;

public interface ITreeNode {

	public ITreeNode getParent();
	
	public ITreeNode[] getChildren();
	
	public boolean hasChildren();
}
