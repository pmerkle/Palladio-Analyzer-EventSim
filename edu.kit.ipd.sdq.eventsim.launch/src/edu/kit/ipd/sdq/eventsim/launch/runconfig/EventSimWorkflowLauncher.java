package edu.kit.ipd.sdq.eventsim.launch.runconfig;

import java.util.ArrayList;

import org.apache.log4j.Level;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.ui.PlatformUI;
import org.palladiosimulator.analyzer.workflow.configurations.AbstractPCMLaunchConfigurationDelegate;
import org.palladiosimulator.analyzer.workflow.configurations.PCMWorkflowConfigurationBuilder;

import de.uka.ipd.sdq.codegen.simucontroller.debug.IDebugListener;
import de.uka.ipd.sdq.codegen.simucontroller.debug.SimulationDebugListener;
import de.uka.ipd.sdq.workflow.jobs.IJob;
import de.uka.ipd.sdq.workflow.launchconfig.AbstractWorkflowConfigurationBuilder;
import de.uka.ipd.sdq.workflow.logging.console.LoggerAppenderStruct;
import edu.kit.ipd.sdq.eventsim.launch.workflow.jobs.EventSimJob;

/**
 * The workflow launcher registered as simulation controller at the
 * de.uka.ipd.sdq.codegen.simucontroller.simulator extension point. Launches the
 * workflow which starts a simulation component based simulation based on the
 * users simulation (launch) configuration.
 * 
 * This class is based on {@link EventSimWorkflowLauncher}. Some code has been
 * reused.
 * 
 * @author Christoph Föhrdes
 */
public class EventSimWorkflowLauncher extends AbstractPCMLaunchConfigurationDelegate<EventSimWorkflowConfiguration> {

	@Override
	protected IJob createWorkflowJob(EventSimWorkflowConfiguration config, ILaunch launch) throws CoreException {
		IDebugListener listener = null;
		if (config.isDebug()) {
			listener = new SimulationDebugListener(launch);
		}

		// TODO allow for sensitivity analysis. See class
		// SimuComWorkflowLauncher

		return new EventSimJob(config, listener);
	}

	@Override
	protected EventSimWorkflowConfiguration deriveConfiguration(ILaunchConfiguration configuration, String mode) throws CoreException {
		EventSimWorkflowConfiguration config = new EventSimWorkflowConfiguration(configuration.getAttributes());

		AbstractWorkflowConfigurationBuilder builder;

		builder = new PCMWorkflowConfigurationBuilder(configuration, mode);
		builder.fillConfiguration(config);

		builder = new EventSimLaunchConfigurationBasedConfigBuilder(configuration, mode);
		builder.fillConfiguration(config);

		return config;
	}

	/*
	 * copied from class SimuComWorflowLauncher
	 */
	@Override
	protected ArrayList<LoggerAppenderStruct> setupLogging(Level logLevel) throws CoreException {
	    ArrayList<LoggerAppenderStruct> loggerList = super.setupLogging(logLevel);
	    if(PlatformUI.isWorkbenchRunning()) {
	        // TODO update (or create extension point, perhaps)
	        loggerList.add(setupLogger("de.uka.ipd.sdq.codegen", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.simucomframework", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.workflow.mdsd.emf.qvtr", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.statistics", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.probespec.framework", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.pipesandfilters.framework", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("edu.kit.ipd.sdq.eventsim", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("de.uka.ipd.sdq.simulation", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("edu.kit.ipd.sdq.simcomp", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN));
	        loggerList.add(setupLogger("org.palladiosimulator.transactions", logLevel, Level.DEBUG == logLevel ? DETAILED_LOG_PATTERN : SHORT_LOG_PATTERN)); // TODO   
	    }
		return loggerList;
	}

}
