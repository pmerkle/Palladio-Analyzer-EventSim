package edu.kit.ipd.sdq.eventsim.system.interpreter.state;

import java.util.Stack;

import org.apache.log4j.Logger;
import org.palladiosimulator.pcm.seff.AbstractAction;
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF;

import de.uka.ipd.sdq.simucomframework.variables.StackContext;
import edu.kit.ipd.sdq.eventsim.interpreter.state.AbstractInterpreterState;
import edu.kit.ipd.sdq.eventsim.interpreter.state.ITraversalStrategyState;
import edu.kit.ipd.sdq.eventsim.system.entities.Request;
import edu.kit.ipd.sdq.eventsim.system.staticstructure.ComponentInstance;

/**
 * This class holds the state of a {@link Request}. The state is organised as a stack of
 * {@link RequestStateStackFrame}s.
 * 
 * @author Philipp Merkle
 * 
 */
public class RequestState extends AbstractInterpreterState<AbstractAction> implements IRequestState, Cloneable {

    private static final Logger logger = Logger.getLogger(RequestState.class);
    private static final boolean debug = logger.isDebugEnabled();

    private final Stack<RequestStateStackFrame> stack;
    private final StackContext stoExContext;

    public RequestState(final StackContext stoExContext) {
        this.stack = new Stack<RequestStateStackFrame>();
        this.stoExContext = stoExContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pushStackFrame() {
        if (debug) {
            logger.debug("Entering scope");
        }
        final RequestStateStackFrame f = new RequestStateStackFrame();
        this.stack.push(f);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void popStackFrame() {
        assert !this.stack.isEmpty() : "Tried to leave scope but there is no outer scope";
        if (debug) {
            logger.debug("Leaving scope");
        }
        this.stack.pop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOpenScope() {
        return this.stack.size() > 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return !this.stack.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComponentInstance getComponent() {
        return this.stack.peek().getComponent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setComponent(final ComponentInstance component) {
        this.stack.peek().setComponent(component);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInternalState(final AbstractAction action, final ITraversalStrategyState state) {
        this.stack.peek().addInternalState(action, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeInternalState(AbstractAction action) {
        this.stack.peek().removeInternalState(action);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITraversalStrategyState getInternalState(final AbstractAction action) {
        return this.stack.peek().getInternalState(action);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractAction dequeueFinishedAction() {
        return this.stack.peek().dequeueFinishedAction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enqueueFinishedAction(final AbstractAction action) {
        this.stack.peek().enqueueFinishedAction(action);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractAction getCurrentPosition() {
        return this.stack.peek().getCurrentPosition();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AbstractAction getPreviousPosition() {
        return this.stack.peek().getPreviousPosition();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasFinishedActions() {
        return this.stack.peek().hasFinishedActions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentPosition(final AbstractAction position) {
        this.stack.peek().setCurrentPosition(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPreviousPosition(final AbstractAction previousPosition) {
        this.stack.peek().setPreviousPosition(previousPosition);
    }

    /**
     * Returns the context that is used to evaluate stochastic expressions (StoEx). The context
     * comprises a stack that contains the local variables of service calls. While traversing a
     * {@link ResourceDemandingSEFF}, the stack content changes according to the traversal progress.
     * 
     * @return the evaluation context for stochastic expressions
     */
    public StackContext getStoExContext() {
        return this.stoExContext;
    }

    public boolean isForkedRequestState() {
        return false;
    }

    @Override
    public RequestState clone() throws CloneNotSupportedException {
        StackContext stoExContextCopy = new StackContext();
        stoExContextCopy.getStack().pushStackFrame(this.stoExContext.getStack().currentStackFrame().copyFrame());
        RequestState copy = new RequestState(stoExContextCopy);

        // copy stack
        RequestStateStackFrame[] frames = new RequestStateStackFrame[this.stack.size()];
        this.stack.toArray(frames);
        assert copy.stack.size() == 0 : "Stack is expected to be empty but has " + copy.stack.size() + " elements.";
        for (RequestStateStackFrame f : frames) {
            copy.stack.add(f.clone());
        }

        return copy;
    }

}
