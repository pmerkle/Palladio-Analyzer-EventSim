package edu.kit.ipd.sdq.eventsim.system;

import java.util.Map;

import org.apache.log4j.Logger;
import org.osgi.framework.Bundle;
import org.palladiosimulator.pcm.core.composition.AssemblyContext;
import org.palladiosimulator.pcm.core.entity.Entity;
import org.palladiosimulator.pcm.repository.Interface;
import org.palladiosimulator.pcm.repository.OperationSignature;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.ExternalCallAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingBehaviour;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import edu.kit.ipd.sdq.eventsim.api.Procedure;
import edu.kit.ipd.sdq.eventsim.api.IActiveResource;
import edu.kit.ipd.sdq.eventsim.api.IPassiveResource;
import edu.kit.ipd.sdq.eventsim.api.ISimulationMiddleware;
import edu.kit.ipd.sdq.eventsim.api.ISystem;
import edu.kit.ipd.sdq.eventsim.api.IUser;
import edu.kit.ipd.sdq.eventsim.api.PCMModel;
import edu.kit.ipd.sdq.eventsim.api.events.IEventHandler.Registration;
import edu.kit.ipd.sdq.eventsim.api.events.SimulationPrepareEvent;
import edu.kit.ipd.sdq.eventsim.api.events.SimulationStopEvent;
import edu.kit.ipd.sdq.eventsim.api.events.SystemRequestFinishedEvent;
import edu.kit.ipd.sdq.eventsim.api.events.SystemRequestSpawnEvent;
import edu.kit.ipd.sdq.eventsim.command.PCMModelCommandExecutor;
import edu.kit.ipd.sdq.eventsim.instrumentation.description.action.ActionRepresentative;
import edu.kit.ipd.sdq.eventsim.instrumentation.description.core.InstrumentationDescription;
import edu.kit.ipd.sdq.eventsim.instrumentation.injection.Instrumentor;
import edu.kit.ipd.sdq.eventsim.instrumentation.injection.InstrumentorBuilder;
import edu.kit.ipd.sdq.eventsim.interpreter.TraversalListenerRegistry;
import edu.kit.ipd.sdq.eventsim.measurement.MeasurementFacade;
import edu.kit.ipd.sdq.eventsim.measurement.MeasurementStorage;
import edu.kit.ipd.sdq.eventsim.measurement.osgi.BundleProbeLocator;
import edu.kit.ipd.sdq.eventsim.system.command.BuildComponentInstances;
import edu.kit.ipd.sdq.eventsim.system.command.FindAssemblyContextForSystemCall;
import edu.kit.ipd.sdq.eventsim.system.command.InstallExternalCallParameterHandling;
import edu.kit.ipd.sdq.eventsim.system.debug.DebugSeffTraversalListener;
import edu.kit.ipd.sdq.eventsim.system.entities.ForkedRequest;
import edu.kit.ipd.sdq.eventsim.system.entities.Request;
import edu.kit.ipd.sdq.eventsim.system.entities.RequestFactory;
import edu.kit.ipd.sdq.eventsim.system.handler.AfterSystemCallParameterHandler;
import edu.kit.ipd.sdq.eventsim.system.handler.BeforeSystemCallParameterHandler;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.AllocationRegistry;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.ComponentInstance;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.SimulatedResourceContainer;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.SimulatedResourceEnvironment;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.commands.BuildResourceAllocation;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.commands.BuildSimulatedResourceEnvironment;

/**
 * The simulation model. This is the central class of an EventSim simulation run. Before the
 * simulation starts, it initialises the simulation in the {@code init()} method. During the
 * simulation, it provides information about the PCM model that is to be simulated, the simulation
 * configuration and the simulation status. Finally, it cleans up after a simulation run in the
 * {finalise()} method.
 * <p>
 * Instances are created by using the static {@code create} method that builds the simulation model
 * in accordance with a specified simulation configuration.
 * 
 * @author Philipp Merkle
 * 
 */
@Singleton
public class EventSimSystemModel implements ISystem {

    private static final Logger logger = Logger.getLogger(EventSimSystemModel.class);

    @Inject
    private IActiveResource activeResource;

    @Inject
    private IPassiveResource passiveResource;

    @Inject
    private PCMModelCommandExecutor executor;

    @Inject
    private MeasurementStorage measurementStorage;

    @Inject
    private ISimulationMiddleware middleware;

    @Inject
    private TraversalListenerRegistry<AbstractAction, Request> traversalListeners;

    @Inject
    private PCMModel pcm;

    @Inject
    private InstrumentationDescription instrumentation;

    @Inject
    private RequestFactory requestFactory;

    private MeasurementFacade<SystemMeasurementConfiguration> measurementFacade;

    private SimulatedResourceEnvironment resourceEnvironment;
    private AllocationRegistry resourceAllocation;
    private Map<String, ComponentInstance> componentRegistry;

    @Inject
    public EventSimSystemModel(ISimulationMiddleware middleware) {
        // initialize in simulation preparation phase
        middleware.registerEventHandler(SimulationPrepareEvent.class, e -> {
            init();
            return Registration.UNREGISTER;
        });
    }

    private void init() {
        // install debug traversal listeners, if debugging is enabled
        if (logger.isDebugEnabled()) {
            traversalListeners.addTraversalListener(new DebugSeffTraversalListener());
        }

        this.setupMeasurements();

        // initialise resource environment and allocation
        this.resourceEnvironment = executor.execute(new BuildSimulatedResourceEnvironment());
        this.resourceAllocation = executor.execute(new BuildResourceAllocation(this.resourceEnvironment));

        // initialise component instances
        this.componentRegistry = executor.execute(new BuildComponentInstances(this.resourceAllocation));

        // install extern call parameter handling
        executor.execute(new InstallExternalCallParameterHandling(traversalListeners));

        registerEventHandler();
    }

    /**
     * Handles the simulation of a service call. Service calls are usually generated by a workload
     * simulation component.
     * 
     * @param user
     *            The user which initiated the call
     * @param call
     *            The called service in form of a PCM entry level system call action
     */
    @Override
    public void callService(IUser user, EntryLevelSystemCall call, Procedure callback) {
        // find the component which provides the call
        final AssemblyContext assemblyCtx = executor.execute(new FindAssemblyContextForSystemCall(call));
        final ComponentInstance component = this.getComponent(assemblyCtx);
        final OperationSignature signature = call.getOperationSignature__EntryLevelSystemCall();
        final ResourceDemandingBehaviour behaviour = component.getServiceEffectSpecification(signature);

        // spawn a new EventSim request
        final Request request = requestFactory.createRequest(call, user);

        // simulate request
        request.simulateBehaviour(behaviour, component, callback);
    }

    private void finalise() {
        // TODO really?
        // nothing to do, currently
    }

    public IActiveResource getActiveResource() {
        return activeResource;
    }

    public IPassiveResource getPassiveResource() {
        return passiveResource;
    }

    /**
     * Register event handler to react on specific simulation events.
     */
    private void registerEventHandler() {
        middleware.registerEventHandler(SimulationStopEvent.class, e -> {
            finalise();
            return Registration.UNREGISTER;
        });

        // setup system call parameter handling
        middleware.registerEventHandler(SystemRequestSpawnEvent.class,
                new BeforeSystemCallParameterHandler(this, executor));
        middleware.registerEventHandler(SystemRequestFinishedEvent.class, new AfterSystemCallParameterHandler());
    }

    private void setupMeasurements() {
        // create instrumentor for instrumentation description
        // TODO get rid of cast
        Instrumentor<?, ?> instrumentor = InstrumentorBuilder.buildFor(pcm).inBundle(Activator.getContext().getBundle())
                .withDescription(instrumentation).withStorage(measurementStorage)
                .forModelType(ActionRepresentative.class).withoutMapping().createFor(getMeasurementFacade());
        instrumentor.instrumentAll();

        measurementStorage.addIdExtractor(Request.class, c -> Long.toString(((Request) c).getId()));
        measurementStorage.addNameExtractor(Request.class, c -> ((Request) c).getName());
        measurementStorage.addIdExtractor(ForkedRequest.class, c -> Long.toString(((ForkedRequest) c).getEntityId()));
        measurementStorage.addNameExtractor(ForkedRequest.class, c -> ((ForkedRequest) c).getName());
        measurementStorage.addIdExtractor(Entity.class, c -> ((Entity) c).getId());
        measurementStorage.addNameExtractor(Entity.class, c -> ((Entity) c).getEntityName());
        measurementStorage.addNameExtractor(ExternalCallAction.class, c -> {
            ExternalCallAction action = (ExternalCallAction) c;
            OperationSignature calledSignature = action.getCalledService_ExternalService();
            Interface calledInterface = calledSignature.getInterface__OperationSignature();
            return calledInterface.getEntityName() + "." + calledSignature.getEntityName();
        });
    }

    /**
     * Returns the resource environment comprising {@link SimulatedResourceContainer}.
     * 
     * @return the resource environment
     */
    public SimulatedResourceEnvironment getResourceEnvironment() {
        return this.resourceEnvironment;
    }

    /**
     * Returns the allocation of {@link AssemblyContext}s to {@link SimulatedResourceContainer}s.
     * 
     * @return a registry containing the resource allocations
     */
    public AllocationRegistry getResourceAllocation() {
        return this.resourceAllocation;
    }

    /**
     * Returns the component instance that is encapsulated by the specified assembly context.
     * 
     * @param assemblyContext
     *            the assembly context
     * @return the queried component instance
     */
    public ComponentInstance getComponent(final AssemblyContext assemblyContext) {
        return this.componentRegistry.get(assemblyContext.getId());
    }

    public MeasurementFacade<SystemMeasurementConfiguration> getMeasurementFacade() {
        if (measurementFacade == null) {
            // setup measurement facade
            Bundle bundle = Activator.getContext().getBundle();
            measurementFacade = new MeasurementFacade<>(new SystemMeasurementConfiguration(traversalListeners),
                    new BundleProbeLocator<>(bundle));
        }
        return measurementFacade;
    }

}
