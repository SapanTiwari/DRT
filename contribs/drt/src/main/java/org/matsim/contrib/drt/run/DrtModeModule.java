/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.drt.run;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.drt.optimizer.rebalancing.NoRebalancingStrategy;
import org.matsim.contrib.drt.optimizer.rebalancing.RebalancingStrategy;
import org.matsim.contrib.drt.optimizer.rebalancing.mincostflow.DrtModeMinCostFlowRebalancingModule;
import org.matsim.contrib.drt.routing.ClosestAccessEgressFacilityFinder;
import org.matsim.contrib.drt.routing.DecideOnLinkAccessEgressFacilityFinder;
import org.matsim.contrib.drt.routing.DefaultDrtRouteUpdater;
import org.matsim.contrib.drt.routing.DrtRouteLegCalculator;
import org.matsim.contrib.drt.routing.DrtRouteUpdater;
import org.matsim.contrib.drt.routing.NonNetworkWalkRouter;
import org.matsim.contrib.drt.routing.StopBasedDrtRoutingModule;
import org.matsim.contrib.drt.routing.StopBasedDrtRoutingModule.AccessEgressFacilityFinder;
import org.matsim.contrib.dvrp.fleet.FleetModule;
import org.matsim.contrib.dvrp.router.DvrpRoutingNetworkProvider;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.contrib.dvrp.run.DvrpModes;
import org.matsim.contrib.dvrp.run.ModalProviders;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.FastAStarEuclideanFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.pt.transitSchedule.TransitScheduleUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

/**
 * @author michalm (Michal Maciejewski)
 */
public final class DrtModeModule extends AbstractDvrpModeModule {
	private final DrtConfigGroup drtCfg;
	private final PlansCalcRouteConfigGroup plansCalcRouteCfg;

	public DrtModeModule(DrtConfigGroup drtCfg, PlansCalcRouteConfigGroup plansCalcRouteCfg) {
		super(drtCfg.getMode());
		this.drtCfg = drtCfg;
		this.plansCalcRouteCfg = plansCalcRouteCfg;
	}

	@Override
	public void install() {
		DvrpModes.registerDvrpMode(binder(), getMode());
		install(DvrpRoutingNetworkProvider.createDvrpModeRoutingNetworkModule(getMode(),
				drtCfg.isUseModeFilteredSubnetwork()));
		bindModal(TravelDisutilityFactory.class).toInstance(TimeAsTravelDisutility::new);

		install(new FleetModule(getMode(), drtCfg.getVehiclesFileUrl(getConfig().getContext()),
				drtCfg.isChangeStartLinkToLastLinkInSchedule()));

		if (drtCfg.getMinCostFlowRebalancing().isPresent()) {
			install(new DrtModeMinCostFlowRebalancingModule(drtCfg));
		} else {
			bindModal(RebalancingStrategy.class).to(NoRebalancingStrategy.class).asEagerSingleton();
		}

		bindModal(DrtRouteLegCalculator.class).toProvider(new DrtRouteLegCalculatorProvider(drtCfg));// not singleton
		addRoutingModuleBinding(getMode()).toProvider(
				new StopBasedDrtRoutingModuleProvider(drtCfg, plansCalcRouteCfg));// not singleton

		switch (drtCfg.getOperationalScheme()) {
			case door2door:
				bindModal(AccessEgressFacilityFinder.class).toProvider(modalProvider(
						getter -> new DecideOnLinkAccessEgressFacilityFinder(getter.getModal(Network.class))))
						.asEagerSingleton();
				break;

			case serviceAreaBased:
			case stopbased:
				if (drtCfg.getOperationalScheme() == DrtConfigGroup.OperationalScheme.serviceAreaBased) {
					bindModal(TransitSchedule.class).toProvider(new ShapeFileStopProvider(getConfig(), drtCfg))
							.asEagerSingleton();
				} else {
					bindModal(TransitSchedule.class).toInstance(readTransitSchedule());
				}

				TypeLiteral<QuadTree<TransitStopFacility>> quadTreeTypeLiteral = new TypeLiteral<QuadTree<TransitStopFacility>>() {
				};

				bindModal(quadTreeTypeLiteral).toProvider(new StopsQuadTreeProvider(drtCfg));

				bindModal(AccessEgressFacilityFinder.class).toProvider(modalProvider(
						getter -> new ClosestAccessEgressFacilityFinder(drtCfg.getMaxWalkDistance(),
								getter.get(Network.class), getter.getModal(quadTreeTypeLiteral)))).asEagerSingleton();
				break;

			default:
				throw new IllegalStateException();
		}

		bindModal(DrtRouteUpdater.class).toProvider(new ModalProviders.AbstractProvider<DrtRouteUpdater>(getMode()) {
			@Inject
			@Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
			private TravelTime travelTime;

			@Inject
			private Population population;

			@Inject
			private Config config;

			@Override
			public DefaultDrtRouteUpdater get() {
				Network network = getModalInstance(Network.class);
				return new DefaultDrtRouteUpdater(drtCfg, network, travelTime,
						getModalInstance(TravelDisutilityFactory.class), population, config);
			}
		}).asEagerSingleton();

		addControlerListenerBinding().to(modalKey(DrtRouteUpdater.class));
	}

	private static class StopBasedDrtRoutingModuleProvider
			extends ModalProviders.AbstractProvider<StopBasedDrtRoutingModule> {
		@Inject
		@Named(TransportMode.walk)
		private RoutingModule walkRouter;

		@Inject
		private Scenario scenario;

		private final DrtConfigGroup drtCfg;
		private final boolean insertingAccessEgressWalk;

		private StopBasedDrtRoutingModuleProvider(DrtConfigGroup drtCfg, PlansCalcRouteConfigGroup plansCalcRouteCfg) {
			super(drtCfg.getMode());
			this.drtCfg = drtCfg;
			//TODO this is a temporary switch for backward compatibility with (original) DrtRoutingModule
			//XXX in the long term: always insert access/egress walk by default
			insertingAccessEgressWalk = drtCfg.getOperationalScheme() != DrtConfigGroup.OperationalScheme.door2door
					|| plansCalcRouteCfg.isInsertingAccessEgressWalk();
		}

		@Override
		public StopBasedDrtRoutingModule get() {
			RoutingModule accessEgressRouter = insertingAccessEgressWalk ?
					new NonNetworkWalkRouter(walkRouter) :
					(fromFacility, toFacility, departureTime, person) -> Collections.emptyList();
			return new StopBasedDrtRoutingModule(getModalInstance(DrtRouteLegCalculator.class), accessEgressRouter,
					accessEgressRouter, getModalInstance(AccessEgressFacilityFinder.class), drtCfg, scenario,
					getModalInstance(Network.class));
		}
	}

	private static class DrtRouteLegCalculatorProvider extends ModalProviders.AbstractProvider<DrtRouteLegCalculator> {
		private final LeastCostPathCalculatorFactory leastCostPathCalculatorFactory = new FastAStarEuclideanFactory();
		private final DrtConfigGroup drtCfg;

		@Inject
		@Named(DvrpTravelTimeModule.DVRP_ESTIMATED)
		private TravelTime travelTime;

		@Inject
		private Scenario scenario;

		private DrtRouteLegCalculatorProvider(DrtConfigGroup drtCfg) {
			super(drtCfg.getMode());
			this.drtCfg = drtCfg;
		}

		@Override
		public DrtRouteLegCalculator get() {
			Network network = getModalInstance(Network.class);
			return new DrtRouteLegCalculator(drtCfg, network, leastCostPathCalculatorFactory, travelTime,
					getModalInstance(TravelDisutilityFactory.class), scenario);
		}
	}

	private static class ShapeFileStopProvider extends ModalProviders.AbstractProvider<TransitSchedule> {

		private final DrtConfigGroup drtCfg;
		private final URL context;

		private ShapeFileStopProvider(Config config, DrtConfigGroup drtCfg) {
			super(drtCfg.getMode());
			this.drtCfg = drtCfg;
			this.context = config.getContext();
		}

		@Override
		public TransitSchedule get() {
			final List<PreparedGeometry> preparedGeometries = ShpGeometryUtils.loadPreparedGeometries(
					drtCfg.getDrtServiceAreaShapeFileURL(context));
			Network network = getModalInstance(Network.class);
			Set<Link> relevantLinks = network.getLinks()
					.values()
					.stream()
					.filter(link -> ShpGeometryUtils.isCoordInPreparedGeometries(link.getToNode().getCoord(),
							preparedGeometries))
					.collect(Collectors.toSet());
			final TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig())
					.getTransitSchedule();
			relevantLinks.stream().forEach(link -> {
				TransitStopFacility f = schedule.getFactory()
						.createTransitStopFacility(Id.create(link.getId(), TransitStopFacility.class),
								link.getToNode().getCoord(), false);
				f.setLinkId(link.getId());
				schedule.addStopFacility(f);
			});
			return schedule;
		}
	}

	private TransitSchedule readTransitSchedule() {
		URL url = drtCfg.getTransitStopsFileUrl(getConfig().getContext());
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readURL(url);
		return scenario.getTransitSchedule();
	}

	private static class StopsQuadTreeProvider extends ModalProviders.AbstractProvider<QuadTree<TransitStopFacility>> {

		private QuadTree<TransitStopFacility> stopsQT = null;

		private StopsQuadTreeProvider(DrtConfigGroup drtCfg) {
			super(drtCfg.getMode());
		}

		@Override
		public QuadTree<TransitStopFacility> get() {
			if (stopsQT == null) {
				TransitSchedule stopsTransitSchedule = getModalInstance(TransitSchedule.class);
				stopsQT = TransitScheduleUtils.createQuadTreeOfTransitStopFacilities(stopsTransitSchedule);
			}
			return stopsQT;
		}
	}

}
