package edu.kit.ipd.sdq.eventsim.workload.probes;

import org.palladiosimulator.pcm.usagemodel.AbstractUserAction;

import edu.kit.ipd.sdq.eventsim.measurement.Measurement;
import edu.kit.ipd.sdq.eventsim.measurement.MeasuringPoint;
import edu.kit.ipd.sdq.eventsim.measurement.annotation.Probe;
import edu.kit.ipd.sdq.eventsim.measurement.probe.AbstractProbe;
import edu.kit.ipd.sdq.eventsim.workload.WorkloadMeasurementConfiguration;
import edu.kit.ipd.sdq.eventsim.workload.entities.User;
import edu.kit.ipd.sdq.eventsim.workload.interpreter.listener.IUsageTraversalListener;

@Probe(type = AbstractUserAction.class, property = "after")
public class UsageActionExitTimeProbe<E extends AbstractUserAction>
        extends AbstractProbe<E, WorkloadMeasurementConfiguration> {

    public UsageActionExitTimeProbe(MeasuringPoint<E> p, WorkloadMeasurementConfiguration cfg) {
        super(p, cfg);

        configuration.getWorkloadModel().getTraversalListeners().addTraversalListener(getMeasuringPoint().getElement(),
                new IUsageTraversalListener() {

                    @Override
                    public void before(AbstractUserAction action, User user) {
                        // nothing to do
                    }

                    @Override
                    public void after(AbstractUserAction action, User user) {
                        // build measurement
                        double simTime = user.getModel().getSimulationControl().getCurrentSimulationTime();
                        Measurement<E> m = new Measurement<>("CURRENT_TIME", getMeasuringPoint(), user, simTime,
                                simTime);

                        // store
                        cache.put(m);

                        // notify
                        measurementListener.forEach(l -> l.notify(m));
                    }
                });
    }

}
