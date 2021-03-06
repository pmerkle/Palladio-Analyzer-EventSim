package edu.kit.ipd.sdq.eventsim.workload.generator;

import org.palladiosimulator.pcm.core.PCMRandomVariable;
import org.palladiosimulator.pcm.usagemodel.OpenWorkload;
import org.palladiosimulator.pcm.usagemodel.UsageScenario;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import edu.kit.ipd.sdq.eventsim.entities.IEntityListener;
import edu.kit.ipd.sdq.eventsim.workload.entities.User;
import edu.kit.ipd.sdq.eventsim.workload.entities.UserFactory;

/**
 * An open workload generates a new {@link User} as soon as a specified time duration has passed
 * since the previous user has been created. This time duration between two subsequent user arrivals
 * is called the interarrival time.
 * 
 * @author Philipp Merkle
 * 
 */
public class OpenWorkloadGenerator implements WorkloadGenerator {

    private final OpenWorkload workload;
    private final PCMRandomVariable interarrivalTime;
    private UserFactory userFactory;

    /**
     * Constructs an open workload in accordance with the specified workload description.
     * 
     * @param middleware
     * @param userFactory
     * @param workload
     *            the workload description
     */
    @Inject
    public OpenWorkloadGenerator(UserFactory userFactory, @Assisted final OpenWorkload workload) {
        this.userFactory = userFactory;
        this.workload = workload;

        this.interarrivalTime = workload.getInterArrivalTime_OpenWorkload();
    }

    /**
     * {@inheritDoc}
     */
    public void processWorkload() {
        // spawn initial user
        this.spawnUser(0);
    }

    /**
     * Creates a new user and schedule the next user to enter the system after the interarrival time
     * has passed.
     */
    private void spawnUser(double waitingTime) {
        // ensure non-negative wating time
        waitingTime = Math.max(0, waitingTime);

        // create the user
        final UsageScenario scenario = this.workload.getUsageScenario_Workload();
        User user = userFactory.create(scenario);

        // when the user entered the system, we wait until the interarrival time has passed and then
        // schedule a new one
        user.addEntityListener(new IEntityListener() {

            @Override
            public void enteredSystem() {
                final double waitingTime = StackContext
                        .evaluateStatic(OpenWorkloadGenerator.this.interarrivalTime.getSpecification(), Double.class);
                OpenWorkloadGenerator.this.spawnUser(waitingTime);
            }

            @Override
            public void leftSystem() {
                // nothing to do
            }

        });

        // 1) wait
        user.delay(waitingTime, () -> {
            // 2) then simulate the user's behaviour
            user.simulateBehaviour(scenario.getScenarioBehaviour_UsageScenario(), () -> {
            });
        });
    }

}
