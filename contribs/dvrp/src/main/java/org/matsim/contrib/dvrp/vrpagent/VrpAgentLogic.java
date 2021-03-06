/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.contrib.dvrp.vrpagent;

import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.schedule.Schedule;
import org.matsim.contrib.dvrp.schedule.Task;
import org.matsim.contrib.dynagent.DynAction;
import org.matsim.contrib.dynagent.DynActivity;
import org.matsim.contrib.dynagent.DynAgent;
import org.matsim.contrib.dynagent.DynAgentLogic;
import org.matsim.contrib.dynagent.IdleDynActivity;
import org.matsim.core.api.experimental.events.EventsManager;

/**
 * @author michalm
 */
public final class VrpAgentLogic implements DynAgentLogic {
	public static final String BEFORE_SCHEDULE_ACTIVITY_TYPE = "BeforeVrpSchedule";
	public static final String AFTER_SCHEDULE_ACTIVITY_TYPE = "AfterVrpSchedule";

	public interface DynActionCreator {
		DynAction createAction(DynAgent dynAgent, DvrpVehicle vehicle, double now);
	}

	private final EventsManager eventsManager;
	private final VrpOptimizer optimizer;
	private final DynActionCreator dynActionCreator;
	private final DvrpVehicle vehicle;
	private DynAgent agent;

	public VrpAgentLogic(VrpOptimizer optimizer, DynActionCreator dynActionCreator, DvrpVehicle vehicle,
			EventsManager eventsManager) {
		this.optimizer = optimizer;
		this.dynActionCreator = dynActionCreator;
		this.vehicle = vehicle;
		this.eventsManager = eventsManager;
	}

	@Override
	public DynActivity computeInitialActivity(DynAgent dynAgent) {
		this.agent = dynAgent;
		return createBeforeScheduleActivity();// INITIAL ACTIVITY (activate the agent in QSim)
	}

	@Override
	public DynAgent getDynAgent() {
		return agent;
	}

	@Override
	public DynAction computeNextAction(DynAction oldAction, double now) {
		//We need to synchronize the whole method, not only optimizer.nextTask() to address corner cases:
		// - optimisation executed directly on VrpOptimizer.requestSubmitted()
		// - VrpOptimizer.nextTask() may make additional decisions base on the state of other vehicles
		// (e.g. relocation to the best depot if vehicle becomes idle)
		// Additionally, the effect of task initialisation (DynActionCreator) should be visible to other threads
		// calling the optimiser/scheduler

		synchronized (optimizer) {
			Schedule schedule = vehicle.getSchedule();
			switch (schedule.getStatus()) {
				case UNPLANNED:
					return createAfterScheduleActivity();// FINAL ACTIVITY (deactivate the agent in QSim)

				case STARTED:
					Task task = schedule.getCurrentTask();
					eventsManager.processEvent(
							new TaskEndedEvent(now, vehicle.getId(), task.getTaskType(), task.getTaskIdx()));
					break;

				case PLANNED:
					break;

				default:
					throw new IllegalStateException();
			}

			optimizer.nextTask(vehicle);

			switch (schedule.getStatus()) {
				case STARTED:
					Task task = schedule.getCurrentTask();
					eventsManager.processEvent(
							new TaskStartedEvent(now, vehicle.getId(), task.getTaskType(), task.getTaskIdx()));
					return dynActionCreator.createAction(agent, vehicle, now);

				case COMPLETED:
					return createAfterScheduleActivity();// FINAL ACTIVITY (deactivate the agent in QSim)

				default:
					throw new IllegalStateException();
			}
		}
	}

	private DynActivity createBeforeScheduleActivity() {
		return new IdleDynActivity(BEFORE_SCHEDULE_ACTIVITY_TYPE, () -> {
			Schedule s = vehicle.getSchedule();
			switch (s.getStatus()) {
				case PLANNED:
					return s.getBeginTime();
				case UNPLANNED:
					return vehicle.getServiceEndTime();
				default:
					throw new IllegalStateException("Only PLANNED or UNPLANNED schedules allowed.");
			}
		});
	}

	private DynActivity createAfterScheduleActivity() {
		return new IdleDynActivity(AFTER_SCHEDULE_ACTIVITY_TYPE, Double.POSITIVE_INFINITY);
	}

	DvrpVehicle getVehicle() {
		return vehicle;
	}
}
