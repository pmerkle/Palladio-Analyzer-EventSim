package edu.kit.ipd.sdq.eventsim.workload.generator;

import org.palladiosimulator.pcm.core.PCMRandomVariable;
import org.palladiosimulator.pcm.usagemodel.ClosedWorkload;
import org.palladiosimulator.pcm.usagemodel.UsageScenario;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import de.uka.ipd.sdq.simulation.abstractsimengine.ISimulationModel;
import edu.kit.ipd.sdq.eventsim.api.ISimulationMiddleware;
import edu.kit.ipd.sdq.eventsim.api.events.WorkloadUserFinishedEvent;
import edu.kit.ipd.sdq.eventsim.entities.IEntityListener;
import edu.kit.ipd.sdq.eventsim.workload.entities.User;
import edu.kit.ipd.sdq.eventsim.workload.entities.UserFactory;
import edu.kit.ipd.sdq.eventsim.workload.events.BeginUsageTraversalEvent;
import edu.kit.ipd.sdq.eventsim.workload.interpreter.UsageBehaviourInterpreter;

/**
 * A closed workload is a workload sustaining a fixed amount of {@link User}s, which are called the workload population.
 * The workload starts with generating a whole user generation. Whenever a user finishes its usage scenario, a new user
 * is generated after waiting a specified amount of time. This duration is called the think time of an user.
 * 
 * @author Philipp Merkle
 * 
 */
public class ClosedWorkloadGenerator implements IWorkloadGenerator {

	private final ClosedWorkload workload;
	private final int population;
	private final PCMRandomVariable thinkTime;
	private UserFactory userFactory;
	private ISimulationModel model;
	private ISimulationMiddleware middleware;
	private Provider<UsageBehaviourInterpreter> interpreterProvider;
	
	/**
	 * Constructs a closed workload in accordance with the specified workload description.
	 * 
	 * @param model
	 *            the model
	 * @param workload
	 *            the workload description
	 */
    @Inject
    public ClosedWorkloadGenerator(ISimulationModel model, ISimulationMiddleware middleware,
            Provider<UsageBehaviourInterpreter> interpreterProvider, UserFactory userFactory,
            @Assisted ClosedWorkload workload) {
        this.model = model;
        this.middleware = middleware;
        this.interpreterProvider = interpreterProvider;
        this.userFactory = userFactory;
        this.workload = workload;
        this.population = workload.getPopulation();
        this.thinkTime = workload.getThinkTime_ClosedWorkload();
    }

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processWorkload() {
		// spawn initial user population
		for (int i = 0; i < this.population; i++) {
			this.spawnUser();
		}
	}

	/**
	 * Creates a new user and schedules him to enter the system after the think time has passed.
	 */
	private void spawnUser() {
		// create the user
		final UsageScenario scenario = this.workload.getUsageScenario_Workload();
		User user = userFactory.create(scenario);

		// when the user leaves the system, we schedule a new one
		user.addEntityListener(new IEntityListener() {

			@Override
			public void enteredSystem() {
				// nothing to do
			}

			@Override
			public void leftSystem() {
				// trigger event that the user finished his work
				middleware.triggerEvent(new WorkloadUserFinishedEvent(user));

				ClosedWorkloadGenerator.this.spawnUser();
			}

		});
		double waitingTime = StackContext.evaluateStatic(this.thinkTime.getSpecification(), Double.class);
		waitingTime = Math.max(0, waitingTime); // ensure non-negative
		new BeginUsageTraversalEvent(model, scenario, middleware, interpreterProvider.get()).schedule(user, waitingTime);
	}

}
