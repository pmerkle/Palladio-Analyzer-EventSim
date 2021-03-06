package edu.kit.ipd.sdq.eventsim.command.useraction;

import java.util.ArrayList;
import java.util.List;

import org.palladiosimulator.pcm.usagemodel.AbstractUserAction;
import org.palladiosimulator.pcm.usagemodel.UsageScenario;

import edu.kit.ipd.sdq.eventsim.api.PCMModel;
import edu.kit.ipd.sdq.eventsim.command.ICommandExecutor;
import edu.kit.ipd.sdq.eventsim.command.IPCMCommand;

/**
 * TODO
 * 
 * @author Philipp Merkle
 * @author Christoph Föhrdes
 * @author Henning Schulz
 * 
 */
public class FindAllUserActionsByType<A extends AbstractUserAction> implements IPCMCommand<List<A>> {

	private Class<A> actionType;

	public FindAllUserActionsByType(Class<A> actionType) {
		this.actionType = actionType;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<A> execute(PCMModel pcm, ICommandExecutor<PCMModel> executor) {
		List<A> result = new ArrayList<>();
		for (UsageScenario s : pcm.getUsageModel().getUsageScenario_UsageModel()) {
			result.addAll(executor.execute(new FindActionsInUsageScenario<A>(s, actionType, true)));
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean cachable() {
		return false;
	}

}
