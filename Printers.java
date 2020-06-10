package delayAnalisis;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

import Zurich_scenario_Baseline.copy.Zurich_Baseline;

public class Printers {
	static void writeTotalDistanceCovered(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/totalDistanceCoveredByAnAgent_"+j+"_.csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getTotalDistanceCoveredByAnAgent();
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

	static void writeAVGSpeedPerAgent(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/avgSpeedPerAgent_"+j+"_.csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getSpeedMap();
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


	static void writeAgentPathToCsv(int j) {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentTripCar"+j+".csv")) {
			Map<Id<Person>, List<Link>> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAgentPathCar();
			for (Entry<Id<Person>, List<Link>> entry : toCsv.entrySet()) {
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


	static void writeAvgDelayPerLinkToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/avgDelayPerLinkIter"+j+".csv")) {
			Map<Id<Link>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAvgDelayPerLink();
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

	static void writeAvgDelayPerAgentToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentDelayBikeIter"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAgentDelayBike();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				double mario = entry.getValue();
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(mario/180)) /*60*3, persondepartureevent is called each time 3 times*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentDelayCarIter"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAgentDelayCar();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				double mario = entry.getValue();
				//if(mario < 0) mario = 0;
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(mario/180)) /*60*3, persondepartureevent is called each time 3 times*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentDelayWalkIter"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAgentDelayWalk();
			for (Map.Entry<Id<Person>, Double> entry : toCsv.entrySet()) {
				double mario = entry.getValue();
				if(mario < 0) mario = 0;
				writer.append(entry.getKey().toString())
				.append(',')
				.append(String.valueOf(mario/180)) /*60*3, persondepartureevent is called each time 3 times*/
				.append(eol);
			}
		} catch (IOException ex) {
			ex.printStackTrace(System.err);
		}
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/agentDelayPTIter"+j+".csv")) {
			Map<Id<Person>, Double> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getAgentDelayPT();
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


	static void writeNumberOfLinkPassedByAgentToCsv(int j) throws Exception {
		String eol = System.getProperty("line.separator");
		try (Writer writer = new FileWriter(Zurich_Baseline.outputPath+j+"/numberOfLinkPassed"+j+".csv")) {
			Map<Id<Person>, Integer> toCsv = DelayAnalyzer_Car_Bike_Walk_PT.getNumberOfLinkPassedByAnAgent();
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

}
