package delayAnalisis;


import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.vehicles.Vehicle;

import com.google.inject.Inject;

public class TripCreator {
	private Id<Link> start; 
	private Id<Link> end;
	private String mode;
	private Double time;
	private LeastCostPathCalculatorFactory pathCalculatorFactory ;
	private Map<String, TravelTime> travelTimes ;
	private Map<String, TravelDisutilityFactory> travelDisutilityFactories;
	private Scenario sc;


	@Inject private LeastCostPathCalculatorFactory pathFactory ;
	@Inject private Map<String, TravelTime> TT ;
	@Inject private Map<String, TravelDisutilityFactory> TDF;
	@Inject Scenario scenario;

	public TripCreator() {
		this.setStart(null);
		this.setEnd(null);
		this.setMode(null);
		this.setTime(null);
		this.sc = scenario;
		this.travelTimes = TT;
		this.pathCalculatorFactory = pathFactory;
		this.travelDisutilityFactories = TDF;
	}

	@Inject
	public TripCreator(Id<Link> start, Id<Link> end, String mode, Double time) {
		this.setStart(start);
		this.setEnd(end);
		this.setMode(mode);
		this.setTime(time);
		this.sc = scenario;
		this.travelTimes = TT;
		this.pathCalculatorFactory = pathFactory;
		this.travelDisutilityFactories = TDF;
	}

	public Path createPath(Id<Person> person) {
		Path path = null;
		TravelTime TT = null;
		TravelDisutility TD = null;
		if(this.mode.equals(TransportMode.car)) {
			TT = travelTimes.get(TransportMode.car);
			TD = travelDisutilityFactories.get( TransportMode.car ).createTravelDisutility(TT) ;
		}
		if(this.mode.equals(TransportMode.bike)) {
			TT = travelTimes.get(TransportMode.bike);
			TD = travelDisutilityFactories.get(TransportMode.bike).createTravelDisutility(TT) ;
		}
		if(this.mode.equals(TransportMode.walk)) {
			TT = travelTimes.get(TransportMode.walk);
			TD = travelDisutilityFactories.get(TransportMode.walk).createTravelDisutility(TT) ;
		}
		
		LeastCostPathCalculator pathCalculator = pathCalculatorFactory.createPathCalculator(sc.getNetwork(), TD, TT) ;
		Id<Vehicle> carId = Id.createVehicleId("1000031600");
		Vehicle vehicle = sc.getVehicles().getVehicles().get(carId);
		Link startLink = sc.getNetwork().getLinks().get(start);
		Link endLink = sc.getNetwork().getLinks().get(end);
		path = pathCalculator.
				calcLeastCostPath(sc.getNetwork().getLinks().get(startLink.getId()).getFromNode(), sc.getNetwork().getLinks().get(endLink.getId()).getToNode(), this.getTime(),
						sc.getPopulation().getPersons().get(person), vehicle);

//		else if(this.mode.equals(TransportMode.bike)) {
//			TravelTime travelTimeforBike = travelTimes.get(TransportMode.bike);
//			TravelDisutility travelDisutilityforBike = travelDisutilityFactories.get( TransportMode.bike ).createTravelDisutility(travelTimeforBike) ;
//			LeastCostPathCalculator pathCalculatorforCar = pathCalculatorFactory.createPathCalculator(sc.getNetwork(), travelDisutilityforBike, travelTimeforBike) ;
//			Id<Vehicle> bikeId = Id.createVehicleId("1000031600");
//			Vehicle bike = sc.getVehicles().getVehicles().get(bikeId);
//			Link startLink = sc.getNetwork().getLinks().get(start);
//			Link endLink = sc.getNetwork().getLinks().get(end);
//			path = pathCalculatorforCar.
//					calcLeastCostPath(sc.getNetwork().getLinks().get(startLink.getId()).getFromNode(), sc.getNetwork().getLinks().get(endLink.getId()).getToNode(), this.getTime(),
//							sc.getPopulation().getPersons().get(person), bike);
//		}
//		else if(this.mode.equals(TransportMode.walk)) {
//			TravelTime travelTimeforWalk = travelTimes.get(TransportMode.walk);
//			TravelDisutility travelDisutilityforWalk = travelDisutilityFactories.get( TransportMode.walk ).createTravelDisutility(travelTimeforWalk) ;
//			LeastCostPathCalculator pathCalculatorforWalk = pathCalculatorFactory.createPathCalculator(sc.getNetwork(), travelDisutilityforWalk, travelTimeforWalk) ;
//			Id<Vehicle> walkID = Id.createVehicleId("1000031600");
//			Vehicle walk = sc.getVehicles().getVehicles().get(walkID);
//			Link startLink = sc.getNetwork().getLinks().get(start);
//			Link endLink = sc.getNetwork().getLinks().get(end);
//			path = pathCalculatorforWalk.
//					calcLeastCostPath(sc.getNetwork().getLinks().get(startLink.getId()).getFromNode(), sc.getNetwork().getLinks().get(endLink.getId()).getToNode(), this.getTime(),
//							sc.getPopulation().getPersons().get(person), walk);
//		}
//


		return path;
	}


	public Id<Link> getStart() {
		return start;
	}




	public void setStart(Id<Link> start) {
		this.start = start;
	}




	public Id<Link> getEnd() {
		return end;
	}




	public void setEnd(Id<Link> end) {
		this.end = end;
	}




	public String getMode() {
		return mode;
	}




	public void setMode(String mode) {
		this.mode = mode;
	}

	public Double getTime() {
		return time;
	}

	public void setTime(Double time) {
		this.time = time;
	}

}
