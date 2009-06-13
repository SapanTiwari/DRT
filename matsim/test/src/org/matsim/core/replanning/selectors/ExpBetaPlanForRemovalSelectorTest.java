/* *********************************************************************** *
 * project: org.matsim.*
 * BestPlanSelectorTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.core.replanning.selectors;

import org.apache.log4j.Logger;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.population.PersonImpl;

/**
 * Tests for {@link ExpBetaPlanForRemovalSelector}.
 *
 * @author mrieser
 */
public class ExpBetaPlanForRemovalSelectorTest extends AbstractPlanSelectorTest {

	private final static Logger log = Logger.getLogger(ExpBetaPlanForRemovalSelectorTest.class);
	private Config config = null;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		this.config = loadConfig(null); // required for planCalcScore.beta to be defined
	}

	@Override
	protected PlanSelector getPlanSelector() {
		return new ExpBetaPlanForRemovalSelector();
	}

	/**
	 * Test that plans are selected depending on their weight, use beta = 2.0.
	 */
	public void testExpBeta2() {
		this.config.charyparNagelScoring().setBrainExpBeta(2.0);
		Person person = new PersonImpl(new IdImpl(1));
		// weight = Math.exp(this.beta * 5 * (plan.getScore() / maxScore));
		Plan plan1 = person.createPlan(false); // weight: 14764.781
		plan1.setScore(96.0);
		Plan plan2 = person.createPlan(false); // weight: 16317.607
		plan2.setScore(97.0);
		Plan plan3 = person.createPlan(false); // weight: 18033.744
		plan3.setScore(98.0);
		Plan plan4 = person.createPlan(false); // weight: 19930.370
		plan4.setScore(99.0);
		Plan plan5 = person.createPlan(false);// weight: 22026.465
		plan5.setScore(100.0);
		
		PlanSelector selector = new ExpBetaPlanForRemovalSelector();
		int cnt1 = 0;
		int cnt2 = 0;
		int cnt3 = 0;
		int cnt4 = 0;
		int cnt5 = 0;

		for (int i = 0; i < 10000; i++) {
			Plan plan = selector.selectPlan(person);
			if (plan == plan1) cnt1++;
			if (plan == plan2) cnt2++;
			if (plan == plan3) cnt3++;
			if (plan == plan4) cnt4++;
			if (plan == plan5) cnt5++;
		}

		log.info("Plan 1 was returned " + cnt1 + " times.");
		log.info("Plan 2 was returned " + cnt2 + " times.");
		log.info("Plan 3 was returned " + cnt3 + " times.");
		log.info("Plan 4 was returned " + cnt4 + " times.");
		log.info("Plan 5 was returned " + cnt5 + " times.");

		/* The fixed values here must correspond to the weights.
		 * The numbers will never match exactly because of the randomness, but
		 * they should still be feasible. In this example, the numbers should
		 * differ by a factor of ~1.1 from each other, according to the weights.
		 */
		assertEquals(1578, cnt1);
		assertEquals(1772, cnt2);
		assertEquals(1995, cnt3);
		assertEquals(2205, cnt4);
		assertEquals(2450, cnt5);
	}

	/**
	 * Test that plans are selected depending on their weight, use beta = 2.0.
	 */
	public void testExpBeta1() {
		this.config.charyparNagelScoring().setBrainExpBeta(1.0);
		Person person = new PersonImpl(new IdImpl(1));
		// weight = Math.exp(this.beta * (plan.getScore() - maxScore));
		// weight: 121.5104175
		Plan plan1 = person.createPlan(false); 
		plan1.setScore(96.0);
		// weight: 127.74038984602878
		Plan plan2 = person.createPlan(false); 
		plan2.setScore(97.0);
		// weight: 134.289779684935
		Plan plan3 = person.createPlan(false); 
		plan3.setScore(98.0);
		// weight: 141.1749639214768
		Plan plan4 = person.createPlan(false); 
		plan4.setScore(99.0);
		// weight: 148.41315910257657
		Plan plan5 = person.createPlan(false);
		plan5.setScore(100.0);
		
		PlanSelector selector = new ExpBetaPlanForRemovalSelector();
		int cnt1 = 0;
		int cnt2 = 0;
		int cnt3 = 0;
		int cnt4 = 0;
		int cnt5 = 0;

		for (int i = 0; i < 10000; i++) {
			Plan plan = selector.selectPlan(person);
			if (plan == plan1) cnt1++;
			if (plan == plan2) cnt2++;
			if (plan == plan3) cnt3++;
			if (plan == plan4) cnt4++;
			if (plan == plan5) cnt5++;
		}

		log.info("Plan 1 was returned " + cnt1 + " times.");
		log.info("Plan 2 was returned " + cnt2 + " times.");
		log.info("Plan 3 was returned " + cnt3 + " times.");
		log.info("Plan 4 was returned " + cnt4 + " times.");
		log.info("Plan 5 was returned " + cnt5 + " times.");

		/* The fixed values here must correspond to the weights.
		 * The numbers will never match exactly because of the randomness, but
		 * they should still be feasible. In this example, the numbers should
		 * differ by a factor of ~1.05 from each other, according to the weights.
		 */
		assertEquals(1755, cnt1);
		assertEquals(1850, cnt2);
		assertEquals(2013, cnt3);
		assertEquals(2146, cnt4);
		assertEquals(2236, cnt5);
	}

}
