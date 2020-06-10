package delayAnalisis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentWaitingForPtEvent;
import org.matsim.core.api.experimental.events.handler.AgentWaitingForPtEventHandler;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.listener.IterationStartsListener;

import com.google.inject.Inject;

public class WaitingTimeHandler implements  AgentWaitingForPtEventHandler, PersonEntersVehicleEventHandler, IterationStartsListener{

	Map<Id<Person>,ArrayList<Double>> start = new HashMap<Id<Person>,ArrayList<Double>>();
	Map<Id<Person>,ArrayList<Double>> end = new HashMap<Id<Person>,ArrayList<Double>>();
	Map<Id<Person>, Integer> numberOfPtPassedByAgent = new HashMap<>();
	@Inject Scenario sc;
	@Override
	public void reset(int iteration) {
		start.clear();
		end.clear();
		numberOfPtPassedByAgent.clear();
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		for(Person p : sc.getPopulation().getPersons().values()) {
			ArrayList<Double> newList = new ArrayList<Double>();
			start.put(p.getId(), newList);
			end.put(p.getId(), newList);
			numberOfPtPassedByAgent.put(p.getId(), 0);
		}

	}
	
	
	@Override
	public void handleEvent(AgentWaitingForPtEvent event) {						
		//if((event.getTime() > 27000 && event.getTime() < 39600)||(event.getTime()> 61200  && event.getTime() <70200 ) ) {//se ci troviamo nelle PEAK HOURS, 7:30-11/17-19:30
		if(start.containsKey(event.getPersonId())){
			ArrayList<Double> newList = start.get(event.getPersonId());
			newList.add(event.getTime());
			start.put(event.getPersonId(), newList);
		}
		else{
			ArrayList<Double> newList = new ArrayList<Double>();
			newList.add(event.getTime());
			start.put(event.getPersonId(), newList);
		}

		if(numberOfPtPassedByAgent.containsKey(event.getPersonId())) {
			numberOfPtPassedByAgent.put((event.getPersonId()), numberOfPtPassedByAgent.get(event.getPersonId())+1); //aggiorno il numero di Ptstop che l'agente sta attraversando
		}
		else {
			numberOfPtPassedByAgent.put(event.getPersonId(), 1); //aggiungo alla lista il nuovo agente e mi segno che ha passato un solo pt
		}


	}


	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(end.containsKey(event.getPersonId())){
			ArrayList<Double> newList = end.get(event.getPersonId());
			newList.add(event.getTime());
			end.put(event.getPersonId(), newList);
		}
		else{
			ArrayList<Double> newList = new ArrayList<Double>();
			newList.add(event.getTime());
			end.put(event.getPersonId(), newList);
		}

	}

	//we want avg waiting time! dividing the delay per number of stops passed
	public Double getWaitingTime(Id<Person> agent){
		Double timestamp1 = 0.0;
		Double timestamp2 = 0.0;
		//inizialmente l'agente non ha delay, 9/08/2019
		double currentWait = 0.0;
		double maxWait = 0.0;
		if(start.containsKey(agent) & end.containsKey(agent)){
			if(start.get(agent).size() == end.get(agent).size()){
				int i = 0;
				for(Double time : start.get(agent)){
					timestamp1 = time;
					timestamp2 = end.get(agent).get(i);
					currentWait = timestamp2-timestamp1;
					if(currentWait > maxWait) {
						maxWait = currentWait;
					}
					i++;
				}
			}
		}
		return maxWait;

	}

	public int returnNumberOfPtStopsPassed(Id<Person> id) {
		if(numberOfPtPassedByAgent.get(id) == null) {
			return 0;
		}
		else {
			return numberOfPtPassedByAgent.get(id);
		}
	}


}