package org.eclipse.cdt.debug.stackaggregation.ui.dsf;

import org.eclipse.cdt.dsf.debug.service.IRunControl;
import org.eclipse.cdt.dsf.service.DsfServiceEventHandler;


public class StackAggregationEventListener {

	
	private IViewUpdateTrigger updateTrigger;
	
	public StackAggregationEventListener(IViewUpdateTrigger updateTrigger) {
		this.updateTrigger = updateTrigger;
	}
	
	@DsfServiceEventHandler
	public void handleEvent(final IRunControl.ISuspendedDMEvent event) {
		if(updateTrigger != null) {
			updateTrigger.updateView();
		}
	}

}
