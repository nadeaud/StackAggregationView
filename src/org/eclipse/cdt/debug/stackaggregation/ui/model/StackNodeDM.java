package org.eclipse.cdt.debug.stackaggregation.ui.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.eclipse.cdt.dsf.datamodel.IDMContext;
import org.eclipse.core.runtime.IAdaptable;


public class StackNodeDM 
	implements IAdaptable, ITreeNode {

	
	private String fId;
	private StackNodeDM fParent;
	private List<ThreadNodeDM> fThreads;
	private HashMap<String, StackNodeDM> fMap;
	
	public StackNodeDM(String id, StackNodeDM parent) {
		this.fId = id;
		fThreads = new ArrayList<>();
		fMap = new HashMap<>();
		this.fParent = parent;
	}

	public StackNodeDM getChild(String key) { return fMap.get(key); }
	public String getId() { return fId; }
	//public Collection<StackNodeDM> getChildren() { return fMap.values(); }
	public IDMContext getFirstThread() { return fThreads.size() > 0 ? fThreads.get(0).getContext() : null; }
	public List<ThreadNodeDM> getThreads() { return fThreads; }
	public HashMap<String, StackNodeDM> getMap() { return fMap; }
	
	/* ITreeNode interface. */
	public StackNodeDM getParent() { 
		return fParent; 
		}
	
	public ITreeNode[] getChildren() {
		List<ITreeNode> list = new ArrayList<ITreeNode>(fMap.size() + fThreads.size());
		list.addAll(fThreads);
		list.addAll(fMap.values());
		return list.toArray(new ITreeNode[0]);
	}
	
	public boolean hasChildren() {
		return (fMap.size() + fThreads.size()) >0;
	}
	
	public StackNodeDM add(String key) {
		StackNodeDM child = new StackNodeDM(key, this);
		fMap.put(key, child);
		return child;
	}
	
	public void addAll(StackNodeDM node) {
		fMap.putAll(node.fMap);
	}
	
	public void addThread(IDMContext thread) {
		fThreads.add(new ThreadNodeDM(this, thread));
	}
	
	public void addThreads(List<ThreadNodeDM> threads) {
		fThreads.addAll(threads);
	}
	
	public StackNodeDM addThreads(StackNodeDM node) {
		fThreads.addAll(node.fThreads);
		return this;
	}
	
	@Override
	public String toString() {
		return "StackNodeDM " + fId;
	}
	
	public boolean isLeaf() {
		return fMap.isEmpty();
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}

}
