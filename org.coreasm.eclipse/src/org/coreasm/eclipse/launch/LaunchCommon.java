package org.coreasm.eclipse.launch;

import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;

public class LaunchCommon {
	public static void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(ICoreASMConfigConstants.PROJECT,ICoreASMConfigConstants.PROJECT_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.SPEC,ICoreASMConfigConstants.SPEC_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_EMPTYACTIVEAGENTS,ICoreASMConfigConstants.STOPON_EMPTYACTIVEAGENTS_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_EMPTYUPDATES,ICoreASMConfigConstants.STOPON_EMPTYUPDATES_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_STABLEUPDATES,ICoreASMConfigConstants.STOPON_STABLEUPDATES_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_FAILEDUPDATES,ICoreASMConfigConstants.STOPON_FAILEDUPDATES_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_ERRORS,ICoreASMConfigConstants.STOPON_ERRORS_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.STOPON_STEPSLIMIT,ICoreASMConfigConstants.STOPON_STEPSLIMIT_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.MAXSTEPS,ICoreASMConfigConstants.MAXSTEPS_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.VERBOSITY,ICoreASMConfigConstants.VERBOSITY_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.DUMPUPDATES,ICoreASMConfigConstants.DUMPUPDATES_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.DUMPSTATE,ICoreASMConfigConstants.DUMPSTATE_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.DUMPFINAL,ICoreASMConfigConstants.DUMPFINAL_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.MARKSTEPS,ICoreASMConfigConstants.MARKSTEPS_DEFAULT);
		configuration.setAttribute(ICoreASMConfigConstants.PRINTAGENTS,ICoreASMConfigConstants.PRINTAGENTS_DEFAULT);
	}
}
