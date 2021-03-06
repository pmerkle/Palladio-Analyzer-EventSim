package edu.kit.ipd.sdq.eventsim.launch.workflow.jobs;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.palladiosimulator.analyzer.workflow.blackboard.PCMResourceSetPartition;
import org.palladiosimulator.analyzer.workflow.jobs.LoadPCMModelsIntoBlackboardJob;

import com.google.inject.Guice;
import com.google.inject.Injector;

import de.uka.ipd.sdq.codegen.simucontroller.dockmodel.DockModel;
import de.uka.ipd.sdq.workflow.extension.AbstractExtendableJob;
import de.uka.ipd.sdq.workflow.jobs.JobFailedException;
import de.uka.ipd.sdq.workflow.jobs.UserCanceledException;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.MDSDBlackboard;
import edu.kit.ipd.sdq.eventsim.SimulationConfiguration;
import edu.kit.ipd.sdq.eventsim.api.PCMModel;
import edu.kit.ipd.sdq.eventsim.exceptions.unchecked.EventSimException;
import edu.kit.ipd.sdq.eventsim.instrumentation.description.core.InstrumentationDescription;
import edu.kit.ipd.sdq.eventsim.instrumentation.xml.DescriptionToXmlParser;
import edu.kit.ipd.sdq.eventsim.launch.ExtendableSimulationModule;
import edu.kit.ipd.sdq.eventsim.launch.SimulationDockWrapper;
import edu.kit.ipd.sdq.eventsim.launch.SimulationManager;
import edu.kit.ipd.sdq.eventsim.launch.runconfig.EventSimConfigurationConstants;
import edu.kit.ipd.sdq.eventsim.launch.runconfig.EventSimWorkflowConfiguration;
import edu.kit.ipd.sdq.eventsim.measurement.MeasurementStorage;
import edu.kit.ipd.sdq.eventsim.measurement.Metadata;
import edu.kit.ipd.sdq.eventsim.modules.SimulationModule;
import edu.kit.ipd.sdq.eventsim.modules.SimulationModuleRegistry;

/**
 * Starts an EventSim simulation.
 * <p>
 * Connects to a simulation dock (see {@link DockModel}) to display simulation progress.
 * 
 * @author Philipp Merkle
 *
 */
public class StartSimulationJob extends AbstractExtendableJob<MDSDBlackboard> {

    private final EventSimWorkflowConfiguration workflowConfiguration;

    private List<Metadata> metadata;

    public StartSimulationJob(EventSimWorkflowConfiguration workflowConfiguration) {
        this(workflowConfiguration, Collections.emptyList());
    }

    public StartSimulationJob(EventSimWorkflowConfiguration workflowConfiguration, List<Metadata> metadata) {
        this.workflowConfiguration = workflowConfiguration;
        this.metadata = metadata;
    }

    public void execute(IProgressMonitor monitor) throws JobFailedException, UserCanceledException {
        // derive configuration
        SimulationConfiguration config = workflowConfiguration.getSimulationConfiguration();

        // obtain PCM model from MDSD blackboard
        // TODO multiple repositories are not supported by the following
        PCMModel model = loadModelFromBlackboard();
        config.setModel(model);

        // read instrumentation description from specified file
        InstrumentationDescription instrumentationDescription = loadInstrumentationDesciptionFromXML();
        config.setInstrumentationDescription(instrumentationDescription);

        // assemble simulation components...
        Injector injector = Guice.createInjector(ExtendableSimulationModule.create(config, instrumentationDescription));

        // instantiate simulation module entry points, if present
        SimulationModuleRegistry moduleRegistry = injector.getInstance(SimulationModuleRegistry.class);
        for (SimulationModule m : moduleRegistry.getModules()) {
            if (m.isEnabled() && m.getEntryPoint() != null) {
                injector.getInstance(m.getEntryPoint());
            }
        }

        // set global metadata, if present
        if (metadata != null) {
            MeasurementStorage measurementStorage = injector.getInstance(MeasurementStorage.class);
            measurementStorage.addMetadata(metadata);
        }

        // ...and start simulation, displaying simulation progress in a simulation dock (progress
        // viewer)
        SimulationDockWrapper dock = SimulationDockWrapper.getBestFreeDock();
        dock.start();
        injector.getInstance(SimulationManager.class).startSimulation(dock);
        dock.stop();

        super.execute(monitor); // TODO needed?
    }

    private InstrumentationDescription loadInstrumentationDesciptionFromXML() {
        InstrumentationDescription instrumentationDescription = null;
        try {
            // TODO should be read from configuration rather than reading from raw attributes map
            String instrumentatinFileLocation = (String) workflowConfiguration.getAttributes()
                    .get(EventSimConfigurationConstants.INSTRUMENTATION_FILE);
            URL url = new URL(instrumentatinFileLocation);
            instrumentationDescription = new DescriptionToXmlParser().readFromInputStream(url.openStream());
        } catch (JAXBException | IOException e) {
            throw new EventSimException("Could not read default instrumentation description", e);
        }
        return instrumentationDescription;
    }

    private PCMModel loadModelFromBlackboard() {
        PCMResourceSetPartition p = (PCMResourceSetPartition) getBlackboard()
                .getPartition(LoadPCMModelsIntoBlackboardJob.PCM_MODELS_PARTITION_ID);
        PCMModel model = new PCMModel(p.getAllocation(), p.getRepositories().get(0), p.getResourceEnvironment(),
                p.getSystem(), p.getUsageModel(), p.getResourceTypeRepository());
        return model;
    }

}
