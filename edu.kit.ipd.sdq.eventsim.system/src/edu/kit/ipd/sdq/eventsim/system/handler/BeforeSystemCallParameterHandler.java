package edu.kit.ipd.sdq.eventsim.system.handler;

import java.util.List;

import org.apache.log4j.Logger;
import org.palladiosimulator.pcm.core.composition.AssemblyContext;
import org.palladiosimulator.pcm.parameter.VariableUsage;
import org.palladiosimulator.pcm.usagemodel.EntryLevelSystemCall;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import de.uka.ipd.sdq.simucomframework.variables.stackframe.SimulatedStackframe;
import edu.kit.ipd.sdq.eventsim.api.events.IEventHandler;
import edu.kit.ipd.sdq.eventsim.api.events.SystemRequestSpawnEvent;
import edu.kit.ipd.sdq.eventsim.command.PCMModelCommandExecutor;
import edu.kit.ipd.sdq.eventsim.system.EventSimSystemModel;
import edu.kit.ipd.sdq.eventsim.system.command.FindAssemblyContextForSystemCall;
import edu.kit.ipd.sdq.eventsim.system.entities.Request;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.ComponentInstance;
import edu.kit.ipd.sdq.eventsim.util.ParameterHelper;

public class BeforeSystemCallParameterHandler implements IEventHandler<SystemRequestSpawnEvent> {

    private static final Logger logger = Logger.getLogger(BeforeSystemCallParameterHandler.class);

    private EventSimSystemModel model;

    private PCMModelCommandExecutor executor;

    public BeforeSystemCallParameterHandler(EventSimSystemModel model, PCMModelCommandExecutor executor) {
        this.model = model;
        this.executor = executor;
    }

    @Override
    public Registration handle(SystemRequestSpawnEvent simulationEvent) {
        if (logger.isDebugEnabled()) {
            logger.debug("Begin handling system call input parameters");
        }

        Request request = (Request) simulationEvent.getRequest();

        final EntryLevelSystemCall call = request.getSystemCall();
        final StackContext ctx = request.getRequestState().getStoExContext();

        // get a reference on the current stack frame which is being covered soon
        final SimulatedStackframe<Object> outerFrame = ctx.getStack().currentStackFrame();

        // enter a new scope in which the call is being executed
        final SimulatedStackframe<Object> serviceBodyFrame = ctx.getStack().createAndPushNewStackFrame();

        // add component parameters
        final AssemblyContext assemblyCtx = executor.execute(new FindAssemblyContextForSystemCall(call));
        final ComponentInstance component = model.getComponent(assemblyCtx);
        serviceBodyFrame.addVariables(component.getComponentParameters());

        // evaluate the input parameters and add them to the call's scope
        final List<VariableUsage> parameters = call.getInputParameterUsages_EntryLevelSystemCall();
        ParameterHelper.evaluateParametersAndCopyToFrame(parameters, outerFrame, serviceBodyFrame);

        if (logger.isDebugEnabled()) {
            logger.debug("Finished handling system call input parameters");
        }

        return Registration.KEEP_REGISTERED;
    }

}
