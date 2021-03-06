package edu.kit.ipd.sdq.eventsim.resources.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.log4j.Logger;
import org.palladiosimulator.pcm.seff.AbstractAction;

import de.uka.ipd.sdq.scheduler.IActiveResource;
import de.uka.ipd.sdq.scheduler.ISchedulableProcess;
import de.uka.ipd.sdq.simulation.abstractsimengine.ISimulationModel;
import edu.kit.ipd.sdq.eventsim.api.IRequest;
import edu.kit.ipd.sdq.eventsim.api.Procedure;
import edu.kit.ipd.sdq.eventsim.entities.EventSimEntity;

/**
 * A simulated process is a process that can be scheduled on an active or
 * passive resource. Whenever the scheduler activates or passivates the process,
 * the {@link IProcessListener}, which has been passed to the constructor, gets
 * notified.
 * 
 * @author Philipp Merkle
 * 
 * @see ISchedulableProcess
 */
public class SimulatedProcess extends EventSimEntity implements ISchedulableProcess {

	private static final Logger logger = Logger.getLogger(SimulatedProcess.class);

	private final List<IActiveResource> terminatedObservers;
	private final IRequest request;
	private boolean terminated;
	private int priority;
	private SimulatedProcess parent;
	private Procedure onActivationCallback;

	private String id;

	public SimulatedProcess(ISimulationModel model, SimulatedProcess parent, final IRequest request) {
		super(model, "SimulatedProcess");
		this.parent = parent;
		this.request = request;
		this.terminatedObservers = new ArrayList<IActiveResource>();
		this.id = Long.toString(getEntityId());
	}

	public void setOnActivationCallback(Procedure callback) {
		if (onActivationCallback != null) {
			logger.warn(String.format("Overriding existing activation callback for %s", this));
		}
		this.onActivationCallback = Objects.requireNonNull(callback);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void activate() {
		if (onActivationCallback != null) {
			// first reset this object's callback, then call it
			Procedure callback = onActivationCallback;
			onActivationCallback = null;
			callback.execute();
		} else {
			logger.warn(String.format("Activating %s, but there is no activation callback registered", this));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void passivate() {
		if (onActivationCallback == null) {
			logger.warn(String.format("Passivating %s, but there is no activation callback registered", this));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ISchedulableProcess getRootProcess() {
		// TODO: what is expected here? (copied this method body from SimuCom)
		return null;
	}

	/**
	 * Returns the request that created this simulated process.
	 */
	public IRequest getRequest() {
		return request;
	}

	public SimulatedProcess getParent() {
		return parent;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFinished() {
		return terminated;
	}

	/**
	 * Terminates this simulated process. Notifies all observers that has been
	 * registered by the {@code addTerminatedObserver} method.
	 * <p>
	 * If this simulated process terminated already, calling this method has no
	 * effect.
	 */
	public void terminate() {
		if (!terminated) {
			terminated = true;
			notifyLeftSystem();
			fireTerminated();
		}
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	/**
	 * Adds an observer which gets notifies when the process has ended its
	 * execution.
	 */
	@Override
	public void addTerminatedObserver(final IActiveResource o) {
		terminatedObservers.add(o);
	}

	/**
	 * Notifies the observers which have been registered via
	 * {@link #addTerminatedObserver(IActiveResource)} that the process has
	 * ended it execution. This is automatically done, when {@link #terminate()}
	 * method is being invoked.
	 */
	@Override
	public void fireTerminated() {
		for (IActiveResource o : terminatedObservers) {
			o.notifyTerminated(this);
		}
		terminatedObservers.clear();
	}

	/**
	 * Removes the specified resource from the observer list.
	 */
	@Override
	public void removeTerminatedObserver(final IActiveResource o) {
		terminatedObservers.remove(o);
	}

	@Override
	public void timeout(String timeoutFailureName) {
		// TODO Failures are not yet supported
		throw new RuntimeException("Encountered a timeout but simulation of failures is not supported.");
	}

	@Override
	public String getName() {
		if (parent != null) {
			return super.getName() + ", parent of " + parent.getName();
		} else {
			return super.getName();
		}
	}

	public AbstractAction getCurrentPosition() {
		return request.getCurrentPosition();
	}

}
