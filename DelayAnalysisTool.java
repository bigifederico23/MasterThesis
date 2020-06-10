package delayAnalisis;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonStuckEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

import Zurich_scenario_Baseline.copy.Zurich_Baseline;


/**
 * Calculate total delay and delay per link. 
 * Delay of stucked agents can be considered (call considerDelayOfStuckedAgents()).
 * Delay occurring between PersonDeparture and VehicleEntersTraffic event is included 
 * (that is why it is not enough to consider only vehicle events).
 * Delay of all passengers (not only the driver) is considered.
 * 
 * @author tthunig
 */
public class DelayAnalysisTool implements PersonDepartureEventHandler, PersonArrivalEventHandler, PersonEntersVehicleEventHandler, LinkEnterEventHandler, LinkLeaveEventHandler, PersonStuckEventHandler, 
IterationEndsListener {

	private static final Logger LOG = Logger.getLogger(DelayAnalysisTool.class);

	private final Network network;
	private boolean considerStuckedAgents = false;

	private double totalDelay = 0.0;
	private double simtime = 0.0;
	private boolean flag = false;
	private static int currentit = 0;
	private WaitingTimeHandler waitingTimeHandler;
	private static Map<Id<Person>, Double> totalWaitTimeAtPTperAgent = new HashMap<>();
	private Map<Id<Link>, Double> totalDelayPerLink = new HashMap<>();
	private Map<Id<Link>, Integer> numberOfAgentsPerLink = new HashMap<>();
	private Map<Id<Person>, Integer> numberOfLinkPassedByAnAgent = new HashMap<Id<Person>, Integer>();
	private Map<Id<Person>, Double> earliestLinkExitTimePerAgent = new HashMap<>();
	private Map<Id<Person>, Double> agentDelay = new HashMap<Id<Person>, Double>();
	private Map<Id<Person>, Double> speedMap = new HashMap<Id<Person>, Double>();
	private Map<Id<Vehicle>, Set<Id<Person>>> vehicleIdToPassengerIds = new HashMap<>();
	//aggiunto 30/07
	private Map<Id<Person>, Set<Link>> agentTrip = new HashMap<>();
	private Map<Id<Person>, Double> totalDistanceCoveredByAnAgent = new HashMap<>();
	private Map<Id<Person>, Double> personWalkOrBikeDelay = new HashMap<>();
	private Set<Integer> capacity = new HashSet<>();

	@Inject Scenario sc;
	public DelayAnalysisTool(Network network) {
		this.network = network;
	}

	@Inject
	public DelayAnalysisTool(Network network, EventsManager em) {
		this(network);
		em.addHandler(this);		 
		waitingTimeHandler = new WaitingTimeHandler();												
		em.addHandler(waitingTimeHandler);
	}


	//quando entra in un determinato link, lo uso sia per inizializzare la mappa dei ritardi degli agenti che il numero di agenti che passano in un determinato link 
	//che per tenere il conto di quanti link passa ogni agente 
	@Override
	public void handleEvent(PersonArrivalEvent event) {
		if(flag == false) {
			addAgentToFinalMapS();
			flag = true;
		}
	}

	//quando esce da un determinato link  
	@Override
	public void handleEvent(PersonDepartureEvent event) {
		// for the first link every vehicle needs one second without delay
		earliestLinkExitTimePerAgent.put(event.getPersonId(), event.getTime() + 1);
	}


	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (!vehicleIdToPassengerIds.containsKey(event.getVehicleId())){
			// register empty vehicle
			vehicleIdToPassengerIds.put(event.getVehicleId(), new HashSet<Id<Person>>());
		}
		// add passenger
		vehicleIdToPassengerIds.get(event.getVehicleId()).add(event.getPersonId());
	}



	public Integer getVehicleTypeFromCapacity(Id<Vehicle> id) {
		if(sc.getTransitVehicles().getVehicles().get(id)!= null) {
			return sc.getTransitVehicles().getVehicles().get(id).getType().getCapacity().getSeats();
		}
		else {
			return null;
		}
	}



	@Override
	public void handleEvent(LinkEnterEvent event) {
		// calculate earliest link exit time
		Link currentLink = network.getLinks().get(event.getLinkId());
		//modify here for bike/walk -> divide by freespeed by car and by person
		double freespeedTt;
		freespeedTt = currentLink.getLength() / currentLink.getFreespeed();

		// this is the earliest time where matsim sets the agent to the next link
		double matsimFreespeedTT = Math.floor(freespeedTt + 1);	
		for (Id<Person> passengerId : vehicleIdToPassengerIds.get(event.getVehicleId())){
			this.earliestLinkExitTimePerAgent.put(passengerId, event.getTime() + matsimFreespeedTT); //this is the earliest time an agent can go through a link
			/*
			 * quando una persona entra in un link qui aumento il numero di link in cui passa
			 */
			if(numberOfLinkPassedByAnAgent.get(passengerId) == null) {
				numberOfLinkPassedByAnAgent.put(passengerId, 1);
			}
			else numberOfLinkPassedByAnAgent.put(passengerId, numberOfLinkPassedByAnAgent.get(passengerId)+1);
		}
	}
	@Override
	public void handleEvent(LinkLeaveEvent event) {
		// initialize link based analysis data structure
		if (!totalDelayPerLink.containsKey(event.getLinkId())) {
			totalDelayPerLink.put(event.getLinkId(), 0.0);
			numberOfAgentsPerLink.put(event.getLinkId(), 0);
		}

		for (Id<Person> passengerId : vehicleIdToPassengerIds.get(event.getVehicleId())) {
			// calculate delay for every passenger
			double currentDelay = event.getTime() - this.earliestLinkExitTimePerAgent.get(passengerId); //delay dell'agente calcolato come actual time - freespeed time
			totalDelayPerLink.put(event.getLinkId(), totalDelayPerLink.get(event.getLinkId()) + currentDelay);
			totalDelay += currentDelay/60; 
			// in questo modo dovrei poter aggiungere alla mappa il delay corrente calcolato dal linkleaveevent
			// calcolo il delay basato sul massimo tra il suo delay attuale e il precedente
			if(agentDelay.get(passengerId) != null && currentDelay > 0) {
				if(currentDelay >= agentDelay.get(passengerId)) {
					agentDelay.put(passengerId, currentDelay); 
				}
				// increase agent counter
				numberOfAgentsPerLink.put(event.getLinkId(), numberOfAgentsPerLink.get(event.getLinkId()) +1 );
				//aggiunto 30/07
				//aggiunto il link al path dell'agente
				if(agentTrip.get(passengerId)!= null) {
					agentTrip.get(passengerId).add(sc.getNetwork().getLinks().get(event.getLinkId()));
					totalDistanceCoveredByAnAgent.put(passengerId, totalDistanceCoveredByAnAgent.get(passengerId) + sc.getNetwork()
					.getLinks().get(event.getLinkId()).getLength()); //aggiungo il link su cui e' passato l'agente alla lista
					//e' in metri! ndr
				}
			}
		}
	}

	@Override
	public void handleEvent(PersonStuckEvent event) {
		if (this.considerStuckedAgents) {
			if (!totalDelayPerLink.containsKey(event.getLinkId())) {
				// initialize link based analysis data structure
				totalDelayPerLink.put(event.getLinkId(), 0.0);
				numberOfAgentsPerLink.put(event.getLinkId(), 0);
			}
			double stuckDelay = event.getTime() - this.earliestLinkExitTimePerAgent.get(event.getPersonId());
			LOG.warn("Add delay " + stuckDelay + " of agent " + event.getPersonId() + " that stucked on link "
					+ event.getLinkId());
			totalDelayPerLink.put(event.getLinkId(), totalDelayPerLink.get(event.getLinkId()) + stuckDelay);
			this.totalDelay += stuckDelay;
			// increase agent counter
			numberOfAgentsPerLink.put(event.getLinkId(), numberOfAgentsPerLink.get(event.getLinkId()) + 1);
		}
	}




	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		this.agentDelay = refineMap(agentDelay);
		simtime = simtime + event.getServices().getConfig().qsim().getEndTime();
		/*
		 * in questo pezzo di codice conto i PT Stops che un agente ha passato durante il suo percorso
		 *se sono 0 -> non ha usato i pt
		 *se > 0 -> divido e ottengo l'attesa media di ogni stop per ogni agente.
		 */
		for(Person p : sc.getPopulation().getPersons().values()) {
			int numberOfStopsPtPassed;
			if(waitingTimeHandler.returnNumberOfPtStopsPassed(p.getId()) == 0) 	numberOfStopsPtPassed = 1;
			else {
				numberOfStopsPtPassed = waitingTimeHandler.returnNumberOfPtStopsPassed(p.getId());
				//aggiungo i wait time ad una mappa per stamparli, valuto i piu' alti e quello lo considero come delay dell'agente
				DelayAnalysisTool.totalWaitTimeAtPTperAgent.put(p.getId(), waitingTimeHandler.getWaitingTime(p.getId())); 
			}
		}
		createAgentSpeedMap();
		try {
			this.writeWaitTimeAtPTperAgent(event.getIteration());
			this.writeAvgDelayPerLinkToCsv(event.getIteration());
			this.writeAvgDelayPerAgentToCsv(event.getIteration());
			this.writeNumberOfLinkPassedByAgentToCsv(event.getIteration());
			this.writeAgentPathToCsv(event.getIteration());
			this.writeTotalDistanceCovered(event.getIteration());
			this.writeAVGSpeedPerAgent(event.getIteration());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		flag = false;
		currentit++;
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


	//voglio ponderare il ritardo di un agente rispetto al numero di link in cui egli e' passato
	private Map<Id<Person>, Double> refineMap(Map<Id<Person>, Double> agentDelay2) {
		// TODO Auto-generated method stub
		Map<Id<Person>, Double> finalMap = agentDelay2;
		//se l'agente non passa nessun link e' un problema! metto il check per != 0 per evitare problemi
		for(Id<Person> idPers : finalMap.keySet()) {
			if(numberOfLinkPassedByAnAgent.get(idPers)!= 0) {
				double finalDelay = agentDelay2.get(idPers); 
				finalMap.put(idPers, finalDelay);
			}
		}
		return finalMap;
	}




	//inizializzo la mappa
	public void addAgentToFinalMapS() {
		for(Person p : sc.getPopulation().getPersons().values()) { //aggiungo l'id della persona alla lista a partire da TUTTA la popolazione
			numberOfLinkPassedByAnAgent.put(p.getId(), 0);
			agentDelay.put(p.getId(), 0.0);
			agentTrip.put(p.getId(), new HashSet<Link>());
			totalWaitTimeAtPTperAgent.put(p.getId(), 0.0);
			totalDistanceCoveredByAnAgent.put(p.getId(), 0.0);
			speedMap.put(p.getId(), 0.0);
			//add 24/08
			personWalkOrBikeDelay.put(p.getId(), 0.0);
		}
	}


	@Override
	public void reset(int iteration) {
		this.totalDelay = 0.0;
		this.totalWaitTimeAtPTperAgent.clear();
		this.earliestLinkExitTimePerAgent.clear();
		this.vehicleIdToPassengerIds.clear();
		this.totalDelayPerLink.clear();
		this.agentDelay.clear();
		this.numberOfAgentsPerLink.clear();
		this.numberOfLinkPassedByAnAgent.clear();
		//aggiunto 30/07
		this.agentTrip.clear();
		this.totalDistanceCoveredByAnAgent.clear();
		this.speedMap.clear();
		this.personWalkOrBikeDelay.clear();
	}



	/*
	 * 
	 * printers
	 * 
	 * 
	 */

	private void writeTotalDistanceCovered(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/totalDistanceCoveredByAnAgent_"+j+"_.csv")) {
			Map<Id<Person>, Double> toCsv = this.getTotalDistanceCoveredByAnAgent();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue())) /*mt*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}


	private void writeAVGSpeedPerAgent(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/avgSpeedPerAgent_"+j+"_.csv")) {
			Map<Id<Person>, Double> toCsv = this.getSpeedMap();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue()*0.06)) //mt/s to km/min
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}



	private void writeWaitTimeAtPTperAgent(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/WaitTimeAtPTperAgent"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = getTotalWaitTimeAtPTperAgent();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue()/60)) //minutes
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}


	private void writeAgentPathToCsv(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentTrip"+j+".csv")) {
			Map<Id<Person>, Set<Link>> toCsv = this.getAgentTrip();
			for (Entry<Id<Person>, Set<Link>> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString());
				for(Link l : entry.getValue()) {
					writer.append(',').append(""+l.getId());
				}
				writer.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}

	public void writeAvgDelayPerLinkToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/avgDelayPerLinkIter"+j+".csv")) {
			Map<Id<Link>, Double> toCsv = this.getAvgDelayPerLink();
			for (Map.Entry<Id<Link>, Double> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue()/60)) /*minutes*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}

	public void writeAvgDelayPerAgentToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentDelayIter"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = this.getAgentDelay();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue()/60)) /*minutes*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}

	public void writeNumberOfLinkPassedByAgentToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/numberOfLinkPassed"+j+".csv")) {
			Map<Id<Person>, Integer> toCsv = this.getNumberOfLinkPassedByAnAgent();
			for (Map.Entry<Id<Person>, Integer> entry : toCsv.entrySet()) {
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(entry.getValue()))
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
	}


	/*getters and setters
	 * 
	 * 
	 */




	public Map<Id<Person>, Double> getAgentDelay() {
		return agentDelay;
	}

	public void setAgentDelay(Map<Id<Person>, Double> agentDelay) {
		this.agentDelay = agentDelay;
	}

	public Map<Id<Person>, Integer> getNumberOfLinkPassedByAnAgent() {
		return numberOfLinkPassedByAnAgent;
	}

	public Map<Id<Person>, Set<Link>> getAgentTrip() {
		return agentTrip;
	}

	public void setAgentTrip(Map<Id<Person>, Set<Link>> agentTrip) {
		this.agentTrip = agentTrip;
	}

	public static Map<Id<Person>, Double> getTotalWaitTimeAtPTperAgent() {
		return totalWaitTimeAtPTperAgent;
	}

	public void setTotalWaitTimeAtPTperAgent(Map<Id<Person>, Double> totalWaitTimeAtPTperAgent) {
		this.totalWaitTimeAtPTperAgent = totalWaitTimeAtPTperAgent;
	}

	public double getTotalDelay(){
		return totalDelay;
	}

	public Map<Id<Link>, Double> getTotalDelayPerLink(){
		return totalDelayPerLink;
	}

	public Map<Id<Link>, Double> getAvgDelayPerLink(){
		Map<Id<Link>, Double> avgDelayMap = new HashMap<>();
		for (Id<Link> linkId : totalDelayPerLink.keySet()){
			avgDelayMap.put(linkId, totalDelayPerLink.get(linkId) / numberOfAgentsPerLink.get(linkId));
		}
		return avgDelayMap;
	}

	public void considerDelayOfStuckedAgents() {
		this.considerStuckedAgents = true;
	}

	public Map<Id<Person>, Double> getTotalDistanceCoveredByAnAgent() {
		return totalDistanceCoveredByAnAgent;
	}

	public void setTotalDistanceCoveredByAnAgent(Map<Id<Person>, Double> totalDistanceCoveredByAnAgent) {
		this.totalDistanceCoveredByAnAgent = totalDistanceCoveredByAnAgent;
	}

	public Map<Id<Person>, Double> getSpeedMap() {
		return speedMap;
	}

	public void setSpeedMap(Map<Id<Person>, Double> speedMap) {
		this.speedMap = speedMap;
	}

	public Map<Id<Person>, Double> getPersonWalkOrBikeDelay() {
		return personWalkOrBikeDelay;
	}




}