package edu.kit.ipd.sdq.eventsim.middleware.events;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import com.google.inject.Singleton;

import edu.kit.ipd.sdq.eventsim.api.events.IEventHandler;
import edu.kit.ipd.sdq.eventsim.api.events.IEventHandler.Registration;
import edu.kit.ipd.sdq.eventsim.api.events.SimulationEvent;
import edu.kit.ipd.sdq.eventsim.middleware.Activator;

/**
 * Wraps the OSGi {@link EventAdmin} service for better type safety. {@link SimulationEvent}s and
 * {@link IEventHandler}s are strongly typed, whereas the classical way of using OSGi {@link Event}s
 * (hidden by this wrapper) involves undesired type casts.
 * 
 * @author Christoph Föhrdes
 * @author Philipp Merkle
 *
 */
@Singleton
public class EventManager {

    private static final Logger log = Logger.getLogger(EventManager.class);

    private EventAdmin eventAdmin;

    private Set<ServiceRegistration<?>> handlerRegistrations;

    public EventManager() {
        handlerRegistrations = new HashSet<ServiceRegistration<?>>();

        // discover event admin service
        BundleContext bundleContext = Activator.getContext();
        ServiceReference<EventAdmin> eventAdminServiceReference = bundleContext.getServiceReference(EventAdmin.class);
        eventAdmin = bundleContext.getService(eventAdminServiceReference);
    }

    /**
     * Delivers the specified {@code event} to interested event handlers. Returns not until all
     * interested event handlers processed the event completely (synchronous delivery).
     * 
     * @param event
     *            the event to be delivered
     */
    public void triggerEvent(SimulationEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("Event triggered (" + SimulationEvent.topicName(event.getClass()) + ")");
        }

        // we delegate the event to the OSGi event admin service
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(SimulationEvent.ENCAPSULATED_EVENT, event);
        properties.putAll(event.getProperties());
        eventAdmin.sendEvent(new Event(SimulationEvent.topicName(event.getClass()), properties));

    }

    /**
     * Registers the specified handler with events of the specified type.
     * 
     * @param eventType
     *            the type of events handled by the handler
     * @param handler
     *            the event handler
     */
    public <T extends SimulationEvent> void registerEventHandler(Class<T> eventType, final IEventHandler<T> handler,
            String filter) {
        BundleContext bundleContext = Activator.getContext();
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(EventConstants.EVENT_TOPIC, SimulationEvent.topicName(eventType));
        if (filter != null && !filter.isEmpty()) {
            properties.put(EventConstants.EVENT_FILTER, filter);
        }
        FutureServiceRegistration futureRegistration = new FutureServiceRegistration();
        ServiceRegistration<EventHandler> handlerRegistration = bundleContext.registerService(EventHandler.class,
                event -> {
                    // delegate event handling to registered event handler
                    @SuppressWarnings("unchecked")
                    T encapsulatedEvent = (T) event.getProperty(SimulationEvent.ENCAPSULATED_EVENT);
                    Registration registrationHint = handler.handle(encapsulatedEvent);
                    // remove event handler if requested (indicated by its returned hint)
                    if (registrationHint == Registration.UNREGISTER) {
                        if (futureRegistration.get() != null) {
                            // remove handler registration, if it is still contained in handler set;
                            // otherwise do nothing
                            boolean removed = handlerRegistrations.remove(futureRegistration.get());
                            if (removed) {
                                futureRegistration.get().unregister();
                            }
                        } else {
                            log.warn("Cannot unregister event handler because the service registration "
                                    + "is not yet available.");
                        }
                    } // else keep registered
                }, properties);
        // handler registration is now known; inform the corresponding future
        futureRegistration.set(handlerRegistration);

        // store service registration for later cleanup
        handlerRegistrations.add(handlerRegistration);
    }

    public void unregisterAllEventHandlers() {
        for (ServiceRegistration<?> reg : handlerRegistrations) {
            reg.unregister();
        }
        handlerRegistrations.clear();
    }

    /**
     * Wraps a {@link ServiceRegistration} instance that will become available in future (via set
     * method), but is not yet known at instantiation time.
     */
    private class FutureServiceRegistration {

        private ServiceRegistration<EventHandler> handlerRegistration;

        public void set(ServiceRegistration<EventHandler> handlerRegistration) {
            this.handlerRegistration = handlerRegistration;
        }

        public ServiceRegistration<EventHandler> get() {
            return this.handlerRegistration;
        }

    }

}
