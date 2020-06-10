package delayAnalisis;

import java.util.ArrayList;

/*
 *  *********************************************************************** *
 *  * project: org.matsim.*
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  * copyright       : (C) 2014 by the members listed in the COPYING, *
 *  *                   LICENSE and WARRANTY file.                            *
 *  * email           : info at matsim dot org                                *
 *  *                                                                         *
 *  * *********************************************************************** *
 *  *                                                                         *
 *  *   This program is free software; you can redistribute it and/or modify  *
 *  *   it under the terms of the GNU General Public License as published by  *
 *  *   the Free Software Foundation; either version 2 of the License, or     *
 *  *   (at your option) any later version.                                   *
 *  *   See also COPYING, LICENSE and WARRANTY file                           *
 *  *                                                                         *
 *  * ***********************************************************************
 */


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.vehicles.Vehicle;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;

public class DelayAnalyzer_Car_Bike_Walk_PT implements  PersonDepartureEventHandler, PersonArrivalEventHandler, IterationEndsListener {

	private final Network network;
	private double totalDelay = 0.0;
	private double simtime = 0.0;
	private boolean flag = false;
	
	
	
	
	private static double avgBikeSpeed = 3.625;
	private static double avgWalkSpeed = 1.33;
	
	static int currentit = 0;
	private WaitingTimeHandler waitingTimeHandler;
	private static Map<Id<Person>, Double> totalWaitTimeAtPTperAgent = new HashMap<>();
	private static Map<Id<Link>, Double> totalDelayPerLink = new HashMap<>();
	private static Map<Id<Link>, Integer> numberOfAgentsPerLink = new HashMap<>();
	private static Map<Id<Person>, Integer> numberOfLinkPassedByAnAgent = new HashMap<Id<Person>, Integer>();
	private static Map<Id<Person>, Double> earliestLinkExitTimePerAgentCAR = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> earliestLinkExitTimePerAgentBIKE = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> earliestLinkExitTimePerAgentWALK = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> agentDelayCar = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> agentDelayPT = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> agentDelayWalk = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, Double> agentDelayBike = new HashMap<Id<Person>, Double>();
	private static Map<Id<Person>, List<Link>> agentPathCar = new HashMap<Id<Person>, List<Link>>();



	static Map<Id<Person>, Double> agentActivity = new HashMap<Id<Person>, Double>();
	static Map<Id<Person>, ArrayList<Double>> agentActivityStart = new HashMap<Id<Person>, ArrayList<Double>>();
	private static Map<Id<Person>, ArrayList<Double>> agentActivityEnd = new HashMap<Id<Person>, ArrayList<Double>>();

	private static Map<Id<Person>, Double> speedMap = new HashMap<Id<Person>, Double>();
	//aggiunto 30/07
	private static Map<Id<Person>, Set<Link>> agentTrip = new HashMap<>();
	private static Map<Id<Person>, Double> totalDistanceCoveredByAnAgent = new HashMap<>();
	private static Map<Id<Person>, Double> personWalkOrBikeDelay = new HashMap<>();
	private Map<Id<Person>, TripCreator> mapToTrip = new HashMap<Id<Person>, TripCreator>();



	@Inject private LeastCostPathCalculatorFactory pathCalculatorFactory ;
	@Inject private Map<String, TravelTime> travelTimes ;
	@Inject private Map<String, TravelDisutilityFactory> travelDisutilityFactories;
	@Inject Scenario sc;


	public DelayAnalyzer_Car_Bike_Walk_PT(Network network) {
		this.network = network;
	}

	@Inject
	public DelayAnalyzer_Car_Bike_Walk_PT(Network network, EventsManager em) {
		this(network);
		em.addHandler(this);		 
		waitingTimeHandler = new WaitingTimeHandler();												
		em.addHandler(waitingTimeHandler);
	}





	@Override
	public void handleEvent(PersonArrivalEvent event) {
		if(flag == false) {
			addAgentToFinalMapS();
			flag = true;
		}
		
		
		if(!event.getPersonId().toString().contains("pt_")) { //if is not a matsim driver
			Id<Person> passengerId = event.getPersonId();
			double currentDelay;
			if(event.getLegMode().equals(TransportMode.car)) {
				TripCreator trip = this.mapToTrip.get(event.getPersonId());
				trip.setEnd(event.getLinkId());
				if(trip.getStart().equals(trip.getEnd())) {
				//	System.out.println("he is transfering!");
				}
				else if(this.createPath(passengerId, trip) != null) {
					Path path = this.createPath(passengerId, trip);
					double freespeedTt = 0, matsimFreespeedTT = 0;
					for(Link l : path.links) {
						freespeedTt += l.getLength() / l.getFreespeed(); //seconds
						List<Link> agentList = agentPathCar.get(event.getPersonId());
						agentList.add(l);
						agentPathCar.put(event.getPersonId(), agentList);
					}
					matsimFreespeedTT = Math.floor(freespeedTt + 1);
					double expectedFinishTime = trip.getTime() + matsimFreespeedTT;
					currentDelay = event.getTime() - expectedFinishTime; 
					if(currentDelay != 0)
					{ 
						agentDelayCar.put(passengerId, agentDelayCar.get(passengerId) + currentDelay);
					}
				}
			}
			if(event.getLegMode().equals(TransportMode.bike)) {
				TripCreator trip = this.mapToTrip.get(event.getPersonId());
				trip.setEnd(event.getLinkId());
				if(trip.getStart().equals(trip.getEnd())) {
				}
				else if(this.createPath(passengerId, trip) != null) {
					Path path = this.createPath(passengerId, trip);
					double freespeedTt = 0, matsimFreespeedTT = 0;
					for(Link l : path.links) {
						freespeedTt += l.getLength() / avgBikeSpeed; //seconds
					}
					matsimFreespeedTT = Math.floor(freespeedTt + 1);
					double expectedFinishTime = trip.getTime() + matsimFreespeedTT;
					currentDelay = event.getTime() - expectedFinishTime; 
					if(currentDelay != 0)
					{ 
						agentDelayBike.put(passengerId, agentDelayBike.get(passengerId) + currentDelay);
					}
				}
			}
			if(event.getLegMode().equals(TransportMode.walk)) {
				TripCreator trip = this.mapToTrip.get(event.getPersonId());
				trip.setEnd(event.getLinkId());
				if(trip.getStart().equals(trip.getEnd())) {
				}
				else if(this.createPath(passengerId, trip) != null) {
					Path path = this.createPath(passengerId, trip);
					double freespeedTt = 0, matsimFreespeedTT = 0;
					for(Link l : path.links) {
						freespeedTt += l.getLength() / avgWalkSpeed; //seconds
					}
					matsimFreespeedTT = Math.floor(freespeedTt + 1);
					double expectedFinishTime = trip.getTime() + matsimFreespeedTT;
					currentDelay = event.getTime() - expectedFinishTime; 
					if(currentDelay != 0)
					{ 
						agentDelayWalk.put(passengerId, agentDelayWalk.get(passengerId) + currentDelay);
					}
				}
			}
		}
	}

	//quando esce da un determinato link  
	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if(flag == false) {
			addAgentToFinalMapS();
			flag = true;
		}
		if(!event.getPersonId().toString().contains("pt_")) { //if is not a matsim driver
			if(event.getLegMode().equals(TransportMode.car)) {
				TripCreator newTrip = new TripCreator(event.getLinkId(), null, TransportMode.car, event.getTime());
				this.mapToTrip.put(event.getPersonId(), newTrip);
			}
			if(event.getLegMode().equals(TransportMode.bike)) {
				TripCreator newTrip = new TripCreator(event.getLinkId(), null, TransportMode.bike, event.getTime());
				this.mapToTrip.put(event.getPersonId(), newTrip);
			}
			if(event.getLegMode().equals(TransportMode.walk)) {
				TripCreator newTrip = new TripCreator(event.getLinkId(), null, TransportMode.walk, event.getTime());
				this.mapToTrip.put(event.getPersonId(), newTrip);
			}
		}
	}




	public Path createPath(Id<Person> person, TripCreator trip) {
		Path path = null;
		TravelTime TT = travelTimes.get(TransportMode.car);
		TravelDisutility TD = travelDisutilityFactories.get( TransportMode.car).createTravelDisutility(TT) ;
		LeastCostPathCalculator pathCalculator = pathCalculatorFactory.createPathCalculator(sc.getNetwork(), TD, TT) ;
		Id<Vehicle> carId = Id.createVehicleId("1000031600");
		Vehicle vehicle = sc.getVehicles().getVehicles().get(carId);
		Link startLink = sc.getNetwork().getLinks().get(trip.getStart());
		Link endLink = sc.getNetwork().getLinks().get(trip.getEnd());
		path = pathCalculator.
				calcLeastCostPath(sc.getNetwork().getLinks().get(startLink.getId()).getFromNode(), sc.getNetwork().getLinks().get(endLink.getId()).getToNode(), trip.getTime(),
						sc.getPopulation().getPersons().get(person), vehicle);
		return path;
	}




	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		simtime = simtime + event.getServices().getConfig().qsim().getEndTime();
		this.setFlag(false);
		if(flag == false) System.out.println("@@@@@OK");
		currentit++;
		for(Person p : sc.getPopulation().getPersons().values()) {
			//aggiungo i wait time ad una mappa per stamparli, valuto i piu' alti e quello lo considero come delay dell'agente
			DelayAnalyzer_Car_Bike_Walk_PT.agentDelayPT.put(p.getId(), waitingTimeHandler.getWaitingTime(p.getId())); 
		}
		
		for(Person p : sc.getPopulation().getPersons().values()) {
			if(agentPathCar.get(p.getId())!= null) {
				double amount = 0.0;
				for(Link l : agentPathCar.get(p.getId())) {
					amount += l.getLength();
				}
				System.out.println("total best trip length for agent "+p.getId() +" =" +amount);
			}
		}
		
		createAgentSpeedMap();
		try {
			Printers.writeAvgDelayPerLinkToCsv(event.getIteration());
			Printers.writeAvgDelayPerAgentToCsv(event.getIteration());
			Printers.writeNumberOfLinkPassedByAgentToCsv(event.getIteration());
			Printers.writeAgentPathToCsv(event.getIteration());
			Printers.writeTotalDistanceCovered(event.getIteration());
			Printers.writeAVGSpeedPerAgent(event.getIteration());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	



	private void createAgentSpeedMap() {
		for(Id<Person> id : agentTrip.keySet()) {
			for(Link l : agentTrip.get(id)) {
				speedMap.put(id, speedMap.get(id) + l.getFreespeed());
			}
		}
		for(Id<Person> id : speedMap.keySet()) {
			int number;
			if(numberOfLinkPassedByAnAgent.get(id) == 0) {
				number = 1;
			}
			else number = numberOfLinkPassedByAnAgent.get(id);
			speedMap.put(id, speedMap.get(id)/number); //ottengo la velocita' media su cui ogni agente passa in un determinato link 
		}
	}

	/*
	 * utility
	 * 
	 * 
	 */






	//inizializzo la mappa
	public void addAgentToFinalMapS() {
		for(Person p : sc.getPopulation().getPersons().values()) { //aggiungo l'id della persona alla lista a partire da TUTTA la popolazione
			numberOfLinkPassedByAnAgent.put(p.getId(), 0);

			agentDelayBike.put(p.getId(), 0.0);
			agentDelayCar.put(p.getId(), 0.0);
			agentDelayPT.put(p.getId(), 0.0);
			agentDelayWalk.put(p.getId(), 0.0);

			agentTrip.put(p.getId(), new HashSet<Link>());
			totalWaitTimeAtPTperAgent.put(p.getId(), 0.0);
			totalDistanceCoveredByAnAgent.put(p.getId(), 0.0);
			speedMap.put(p.getId(), 0.0);

			earliestLinkExitTimePerAgentBIKE.put(p.getId(), 0.0);
			earliestLinkExitTimePerAgentCAR.put(p.getId(), 0.0);
			earliestLinkExitTimePerAgentWALK.put(p.getId(), 0.0);

			agentActivity.put(p.getId(), 0.0);
			mapToTrip.put(p.getId(), new TripCreator());
			agentPathCar.put(p.getId(), new ArrayList<Link>());
		}
		for(Id<Link> l : sc.getNetwork().getLinks().keySet()) {
			totalDelayPerLink.put(l, 0.0);
			numberOfAgentsPerLink.put(l, 0);
		}
	}


	@Override
	public void reset(int iteration) {
		this.totalDelay = 0.0;
		DelayAnalyzer_Car_Bike_Walk_PT.totalWaitTimeAtPTperAgent.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.earliestLinkExitTimePerAgentBIKE.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.earliestLinkExitTimePerAgentCAR.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.earliestLinkExitTimePerAgentWALK.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.totalDelayPerLink.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayBike.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayCar.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayPT.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayWalk.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.numberOfAgentsPerLink.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.numberOfLinkPassedByAnAgent.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentTrip.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.agentActivity.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.totalDistanceCoveredByAnAgent.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.speedMap.clear();
		DelayAnalyzer_Car_Bike_Walk_PT.personWalkOrBikeDelay.clear();
	}



	/*
	 * 
	 * printers
	 * 
	 * 
	 */



	/*getters and setters
	 * 
	 * 
	 */





	public static Map<Id<Person>, Integer> getNumberOfLinkPassedByAnAgent() {
		return numberOfLinkPassedByAnAgent;
	}

	public static Map<Id<Person>, Set<Link>> getAgentTrip() {
		return agentTrip;
	}

	public void setAgentTrip(Map<Id<Person>, Set<Link>> agentTrip) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentTrip = agentTrip;
	}

	public static Map<Id<Person>, Double> getTotalWaitTimeAtPTperAgent() {
		return totalWaitTimeAtPTperAgent;
	}

	public void setTotalWaitTimeAtPTperAgent(Map<Id<Person>, Double> totalWaitTimeAtPTperAgent) {
		DelayAnalyzer_Car_Bike_Walk_PT.totalWaitTimeAtPTperAgent = totalWaitTimeAtPTperAgent;
	}

	public double getTotalDelay(){
		return totalDelay;
	}

	public Map<Id<Link>, Double> getTotalDelayPerLink(){
		return totalDelayPerLink;
	}

	public static Map<Id<Link>, Double> getAvgDelayPerLink(){
		Map<Id<Link>, Double> avgDelayMap = new HashMap<>();
		for (Id<Link> linkId : totalDelayPerLink.keySet()){
			avgDelayMap.put(linkId, totalDelayPerLink.get(linkId) / numberOfAgentsPerLink.get(linkId));
		}
		return avgDelayMap;
	}



	public static Map<Id<Person>, Double> getTotalDistanceCoveredByAnAgent() {
		return totalDistanceCoveredByAnAgent;
	}

	public void setTotalDistanceCoveredByAnAgent(Map<Id<Person>, Double> totalDistanceCoveredByAnAgent) {
		DelayAnalyzer_Car_Bike_Walk_PT.totalDistanceCoveredByAnAgent = totalDistanceCoveredByAnAgent;
	}

	public static Map<Id<Person>, Double> getSpeedMap() {
		return speedMap;
	}

	public static Map<Id<Person>, Double> getPersonWalkOrBikeDelay() {
		return personWalkOrBikeDelay;
	}

	public static Map<Id<Person>, Double> getAgentDelayCar() {
		return agentDelayCar;
	}

	public void setAgentDelayCar(Map<Id<Person>, Double> agentDelayCar) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayCar = agentDelayCar;
	}

	public static Map<Id<Person>, Double> getAgentDelayPT() {
		return agentDelayPT;
	}

	public void setAgentDelayPT(Map<Id<Person>, Double> agentDelayPT) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayPT = agentDelayPT;
	}

	public static Map<Id<Person>, Double> getAgentDelayWalk() {
		return agentDelayWalk;
	}

	public void setAgentDelayWalk(Map<Id<Person>, Double> agentDelayWalk) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayWalk = agentDelayWalk;
	}

	public static Map<Id<Person>, Double> getAgentDelayBike() {
		return agentDelayBike;
	}

	public void setAgentDelayBike(Map<Id<Person>, Double> agentDelayBike) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentDelayBike = agentDelayBike;
	}

	public boolean isFlag() {
		return flag;
	}

	public void setFlag(boolean flag) {
		this.flag = flag;
	}

	public static Map<Id<Person>, ArrayList<Double>> getAgentActivityStart() {
		return agentActivityStart;
	}

	public void setAgentActivityStart(Map<Id<Person>, ArrayList<Double>> agentActivityStart) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentActivityStart = agentActivityStart;
	}

	public static Map<Id<Person>, ArrayList<Double>> getAgentActivityEnd() {
		return agentActivityEnd;
	}

	public static Map<Id<Person>, List<Link>> getAgentPathCar() {
		return agentPathCar;
	}

	public static void setAgentPathCar(Map<Id<Person>, List<Link>> agentPathCar) {
		DelayAnalyzer_Car_Bike_Walk_PT.agentPathCar = agentPathCar;
	}



}