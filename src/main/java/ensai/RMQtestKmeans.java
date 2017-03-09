package ensai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.flink.api.common.functions.FilterFunction;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.tuple.Tuple6;
import org.apache.flink.api.java.tuple.Tuple7;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.connectors.rabbitmq.RMQSource;
import org.apache.flink.streaming.connectors.rabbitmq.common.RMQConnectionConfig;
import org.apache.flink.streaming.util.serialization.SimpleStringSchema;
import org.apache.flink.util.Collector;

import localKmeans.Cluster;
import localKmeans.KMeans;
import localKmeans.Point;

/**
 * This example shows an implementation of WordCount with data from a text
 * socket. To run the example make sure that the service providing the text data
 * is already up and running.
 * 
 * <p>
 * To start an example socket text stream on your local machine run netcat from
 * a command line: <code>nc -lk 9999</code>, where the parameter specifies the
 * port number.
 * 
 * 
 * <p>
 * Usage: <code>SocketTextStreamWordCount &lt;hostname&gt; &lt;port&gt;</code>
 * <br>
 * 
 * <p>
 * This example shows how to:
 * <ul>
 * <li>use StreamExecutionEnvironment.socketTextStream
 * <li>write a simple Flink program
 * <li>write and use user-defined functions
 * </ul>
 * 
 * @see <a href="www.openbsd.org/cgi-bin/man.cgi?query=nc">netcat</a>
 */
public class RMQtestKmeans {
	//
	// Program
	//

	public static int currentObsGroup;
	public static Map<Integer, Integer> mapObsSensors = new HashMap<Integer, Integer>();
	public static String currentTimestamp;
	public static String currentMachineType;
	public static int currentMachine;
	public static int numA = 0;
	public static int numT = 0;

	public static ArrayList<Long> listeLatence = new ArrayList<Long>();

	public static int ws = 50;

	public static int maxCluster = 3;
	public static int maxIterKmean = 100;

	private final static String QUEUE_NAME2 = "voila";

	public static void main(String[] args) throws Exception {

		//Pour lancer le programme : - lancer RabbitMQ
		//							 - lancer flink
		//							 - aller dans projet eclipse : mvn clean install -Pbuild-jar
		//							 - ouvrir le output des jobmanager : tail -f log/flink-*-jobmanager-*.out
		//							 - lancer la classe ensai.RMQtestKmeans sous flink : bin/flink run -c ensai.RMQtestKmeans /home/minifranger/ensai_debs/PFE/target/debs-1.0-SNAPSHOT.jar
		// 							 - lancer la méthode classe ensai.Send sous eclipse 

		// Liste des capteurs à garder
		List<Integer> listSensorsModling = new ArrayList<Integer>();
		listSensorsModling.add(3);
		listSensorsModling.add(17);
		listSensorsModling.add(19);
		// listSensorsModling.add(20);
		// listSensorsModling.add(23);

		// Liste des seuils par cateur
		Map<Integer, Double> mapSeuils = new HashMap<Integer, Double>();
		mapSeuils.put(3,3.1);
		mapSeuils.put(17,3.1);
		mapSeuils.put(19,3.1);

		// Timestamp et numero de machine de l'observation en cours de lecture
		// String currentTimestamp = null;
		// int currentMachine = 0;

		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		final RMQConnectionConfig connectionConfig = new RMQConnectionConfig.Builder().setHost("localhost")
				.setPort(5672).setUserName("guest").setPassword("guest").setVirtualHost("/").build();

		final DataStream<String> stream = env
				.addSource(new RMQSource<String>(connectionConfig, // config for
						// the
						// RabbitMQ
						// connection
						"coucou", // name of the RabbitMQ queue to consume
						true, // use correlation ids; can be false if only
						// at-least-once is required
						new SimpleStringSchema())) // deserialization schema to
				// turn messages into Java
				// objects
				.setParallelism(1);

		// stream.print();

		DataStream<Tuple3<String, String, String>> res = stream.map(new LineSplitter());

		// res.print();

		DataStream<Tuple6<Integer, Integer, Float, String, Long, Long>> st = res.flatMap(new InputAnalyze(listSensorsModling));


		DataStream<ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>>> dt = st.keyBy(0, 1).countWindow(ws, 1)
				.apply(new Kmeans());

		//dt.print();

		DataStream<Tuple7<Integer, Integer, Float, String, Double, Long, Long>> dt2 = dt.map(new Markov()).filter(new Filter(mapSeuils));

		dt2.countWindowAll(3).apply(new ExecutionTime());

		dt2.print();

		//DataStream<String> dt3 = dt2.flatMap(new SortieTransfo());

		//dt3.print();

		//dt3.addSink(new RMQSink<String>(connectionConfig, // config for the
		// RabbitMQ
		// connection
		//		"voila", // name of the RabbitMQ queue to send messages to
		//		new SimpleStringSchema()));

		env.execute("ENSAI debs 2017 flink");
	}

	//
	// User Functions
	//

	public static class ExecutionTime implements AllWindowFunction<Tuple7<Integer, Integer, Float, String, Double, Long, Long>,  ArrayList<Long>, GlobalWindow> {

		@Override
		public void apply(GlobalWindow window,
				Iterable<Tuple7<Integer, Integer, Float, String, Double, Long, Long>> input,
				Collector<ArrayList<Long>> out)
						throws Exception {

			long resultat = 0;

			for (Tuple7<Integer, Integer, Float, String, Double, Long, Long> t : input) {
				resultat = t.f6 - t.f5;
			}	
			listeLatence.add(resultat);


			out.collect(listeLatence);
		}
	}

	public static class Filter implements FilterFunction<Tuple7<Integer, Integer, Float, String, Double, Long, Long>> {

		private Map<Integer, Double> mapSeuils;
		//
		// public Filter(Map<Integer, Double> mapSeuils) {
		// this.mapSeuils=mapSeuils;c
		// }

		public Filter(Map<Integer, Double> mapSeuils) {
			this.mapSeuils = mapSeuils;
		}

		@Override
		public boolean filter(Tuple7<Integer, Integer, Float, String, Double, Long, Long> input) throws Exception {

			long end = System.currentTimeMillis();
			input.f6 = end;
			
			long sommeLatence = 0;
			long moyenneLatence = 0;

			if (listeLatence.size()>0) {
				for (int i = 0; i < listeLatence.size(); i++) {
					sommeLatence = sommeLatence + listeLatence.get(i);
				}
				moyenneLatence = sommeLatence/listeLatence.size();
			}
			System.out.println(listeLatence);
			System.out.println(moyenneLatence);

			return (input.f4 < this.mapSeuils.get(input.f1));
		}
	}

	public static class Kmeans implements
	WindowFunction<Tuple6<Integer, Integer, Float, String, Long, Long>,  ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>>, Tuple, GlobalWindow> {

		@Override
		public void apply(Tuple arg0, GlobalWindow arg1, Iterable<Tuple6<Integer, Integer, Float, String, Long, Long>> input,
				Collector<ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>>> output) throws Exception {

			int machine = 0;
			int capteur = 0;

			long debut = 0;
			long fin = 0;

			KMeans km = new KMeans();
			List<Point> l = new ArrayList<Point>();
			List<Cluster> lc = new ArrayList<Cluster>();
			List<Float> listCentroidCluster = new ArrayList<Float>();
			ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>> res = new ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>>();
			int nbCluster = 0;

			for (Tuple6<Integer, Integer, Float, String, Long, Long> t : input) {
				if (nbCluster < maxCluster && !listCentroidCluster.contains(t.f2)) {
					listCentroidCluster.add(t.f2);
					machine = t.f0;
					capteur = t.f1;
					Cluster c = new Cluster(nbCluster);
					c.setCentroid(new Point(t.f2));
					lc.add(c);
					nbCluster++;
				}
				l.add(new Point(t.f2, t.f3));
				debut = t.f4;
				fin = t.f5;

			}
			km.setPoints(l);
			km.setNUM_POINTS(l.size());

			km.setClusters(lc);
			km.setNUM_CLUSTERS(lc.size());

			// km.plotClusters();
			km.calculate(maxIterKmean);

			for (Point p : km.getPoints()) {
				// System.out.println("Point : " + p.getX() + " | Cluster :
				// "+p.getCluster());
				//				output.collect(new Tuple5<Integer, Integer, Float, String, Integer>(machine, capteur, (float) p.getX(),
				//						p.getTimestamp(), p.getCluster()));
				res.add(new Tuple7<Integer, Integer, Float, String, Integer, Long, Long>(machine, capteur, (float) p.getX(), p.getTimestamp(), p.getCluster(), debut, fin));
			}
			output.collect(res);
		}
	}

	public static final class Markov implements MapFunction<ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>>,
	Tuple7<Integer, Integer, Float, String, Double, Long, Long>>{

		@Override
		public Tuple7<Integer, Integer, Float, String, Double, Long, Long> map(
				ArrayList<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>> input) throws Exception {

			/** Number of transitions used for combined state transition probability */
			int N = 2;
			/** Number of clusters */
			int K = 3;
			/** Size of the window */
			int windowSize = input.size();

			Tuple7<Integer, Integer, Float, String, Double, Long, Long> output ;

			if(windowSize <= N){
				/** TODO : Trouver une bonne valeur par défaut pour les premiers tuples qui ne peuvent
				 * pas avoir de probabilité de transition. */
				Tuple7<Integer,Integer, Float, String, Integer, Long, Long> lastTuple = input.get(windowSize-1);
				output = new Tuple7(lastTuple.f0, lastTuple.f1, lastTuple.f2, lastTuple.f3, Double.valueOf(2), lastTuple.f5, lastTuple.f6);
			}else{
				HashMap<Tuple2<Integer,Integer>,Integer> countTransitions = new HashMap<Tuple2<Integer,Integer>,Integer>();
				double[] countFrom = new double[K];

				Iterator<Tuple7<Integer, Integer, Float, String, Integer, Long, Long>> tupleIter = input.iterator();
				Tuple7<Integer, Integer, Float, String, Integer, Long, Long> tuple = tupleIter.next();

				Integer stateFrom = tuple.f4;

				while(tupleIter.hasNext()){
					countFrom[tuple.f4]++;
					tuple = tupleIter.next();

					Tuple2<Integer, Integer> transitionKey = new Tuple2<Integer,Integer>(stateFrom, tuple.f4);
					if(!countTransitions.containsKey(transitionKey)){
						countTransitions.put(transitionKey, 1);
					}else{
						countTransitions.put(transitionKey, countTransitions.get(transitionKey)+1);
					}        

					stateFrom = tuple.f4;
				}

				ArrayList<Integer> lastN = new ArrayList<Integer>(N+1);
				for(int j = N+1; j>0; j--){
					lastN.add(input.get(windowSize-j).f4);
				}

				Double probability = Double.valueOf(1) ;
				for(int position = 0; position<N; position++){
					Tuple2<Integer,Integer> couple = new Tuple2<Integer,Integer>(lastN.get(position), lastN.get(position+1));
					probability = probability * countTransitions.get(couple)/countFrom[couple.f0];
				}
				output = new Tuple7(tuple.f0, tuple.f1, tuple.f2, tuple.f3, probability, tuple.f5, (long) 0.0);
			}
			return output;
		}

	}

	/**
	 * LineSplitter sépare les triplets RDF en 3 String
	 */
	public static final class LineSplitter implements MapFunction<String, Tuple3<String, String, String>> {

		@Override
		public Tuple3<String, String, String> map(String value) throws Exception {
			// normalize and split the line
			String[] tokens = value.toLowerCase().split(" ");
			tokens[0] = tokens[0].split("#")[1];
			tokens[1] = tokens[1].split("#")[1];
			String[] tab = tokens[2].split("\\^\\^");
			if (tab.length == 1) {
				tokens[2] = tab[0].split("#")[1];
			} else {
				tokens[2] = tab[0];
			}
			return new Tuple3<String, String, String>(tokens[0], tokens[1], tokens[2]);
		}
	}

	/**
	 * Analyse des triplets RDF et renvoi des Tuples de la forme (Numéro de
	 * machine, Numéro du capteur, valeur de l'observation, Timestamp)
	 */
	public static final class InputAnalyze
	implements FlatMapFunction<Tuple3<String, String, String>, Tuple6<Integer, Integer, Float, String, Long, Long>> {

		List<Integer> listSensors;

		public InputAnalyze(List<Integer> listSensors) {
			this.listSensors = listSensors;

		}

		@Override
		public void flatMap(Tuple3<String, String, String> value,
				Collector<Tuple6<Integer, Integer, Float, String, Long, Long>> out) throws Exception {

			switch (value.f1) {

			case "machine>":
				RMQtestKmeans.currentMachine = Integer.parseInt(value.f2.split("\\_|>")[1]);
				System.out.println("Current machine number :  " + RMQtestKmeans.currentMachine);
				break;

			case "type>":
				if (value.f0.split("\\_")[0].equals("ObservationGroup")) {
					RMQtestKmeans.currentMachineType = value.f2.split(">")[0];
					System.out.println("Current machine type : " + RMQtestKmeans.currentMachineType);
				}
				break;

			case "observationresulttime>":
				RMQtestKmeans.currentObsGroup = Integer.parseInt(value.f0.split("\\_|>")[1]);
				System.out.println("Current Observation group : " + RMQtestKmeans.currentObsGroup);
				break;

			case "observedproperty>":

				// System.out.println("Numéro de capteur : " +
				// Integer.parseInt(value.f2.split("\\_|>")[1]));
				// System.out.println("Numéro d'observation : " +
				// Integer.parseInt(value.f0.split("\\_|>")[1]));

				int sensorNb = Integer.parseInt(value.f2.split("\\_|>")[1]);
				int obsNb = Integer.parseInt(value.f0.split("\\_|>")[1]);

				// System.out.println(listSensors.contains(sensorNb));
				// System.out.println(listSensors);
				// System.out.println(sensorNb);

				if (listSensors.contains(sensorNb)) {
					RMQtestKmeans.mapObsSensors.put(obsNb, sensorNb);
					// System.out.println("Numéro d'observation enregisté");
				} else {
					// System.out.println("Ce capteur ne nous interesse pas
					// !!");
				}

				break;

			case "valueliteral>":

				String[] tab = value.f0.split("\\_|>");

				if (tab[0].split("\\_")[0].equals("timestamp")) {
					RMQtestKmeans.currentTimestamp = value.f2;
					System.out.println("Current timestamp : " + RMQtestKmeans.currentTimestamp);
				}

				else if (tab[0].equals("value") && RMQtestKmeans.mapObsSensors.containsKey(Integer.parseInt(tab[1]))) {
					// System.out.println(" ================= > " + "Capteur : "
					// +
					// StreamRabbitMQ.mapObsSensors.get(Integer.parseInt(tab[1]))
					// + " | Valeur : "
					// + Float.parseFloat(value.f2.split("\"")[1]));
					long debut = System.currentTimeMillis();
					out.collect(new Tuple6<Integer, Integer, Float, String, Long, Long>(RMQtestKmeans.currentMachine,
							RMQtestKmeans.mapObsSensors.get(Integer.parseInt(tab[1])),
							Float.parseFloat(value.f2.split("\"")[1]), RMQtestKmeans.currentTimestamp, debut, (long) 0.0));

					RMQtestKmeans.mapObsSensors.remove(Integer.parseInt(tab[1]));

				}
				break;
			}
		}
	}

	public static final class SortieTransfo
	implements FlatMapFunction<Tuple6<Integer, Integer, Float, String, Double, String>, String> {

		@Override
		public void flatMap(Tuple6<Integer, Integer, Float, String, Double, String> avant, Collector<String> apres)
				throws Exception {
			String numMachine = avant.f0.toString();
			String numSensor = avant.f1.toString();
			String valeur = avant.f2.toString();
			String timestamp = avant.f3.toString();
			String probTrans = avant.f4.toString();
			String temps = avant.f5;

			numA++;
			numT++;
			String numAno = Integer.toString(numA);
			String numTsp = Integer.toString(numT);

			String l1 = "<http://yourNamespace/debs2017#Anomaly_" + numAno
					+ "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.agtinternational.com/ontologies/DEBSAnalyticResults#Anomaly> .";
			String l2 = "<http://yourNamespace/debs2017#Anomaly_" + numAno
					+ "> <http://www.agtinternational.com/ontologies/DEBSAnalyticResults#hasProbabilityOfObservedAbnormalSequence> \""
					+ probTrans + "\"^^<http://www.w3.org/2001/XMLSchema#float> .";
			String l3 = "<http://yourNamespace/debs2017#Anomaly_" + numAno
					+ "> <http://www.agtinternational.com/ontologies/DEBSAnalyticResults#hasTimestamp> <http://project-hobbit.eu/resources/debs2017#Timestamp_"
					+ numTsp + "> .";
			String l4 = "<http://yourNamespace/debs2017#Anomaly_" + numAno
					+ "> <http://www.agtinternational.com/ontologies/DEBSAnalyticResults#inAbnormalDimension> <http://www.agtinternational.com/ontologies/WeidmullerMetadata#"
					+ numSensor + "> .";
			String l5 = "<http://yourNamespace/debs2017#Anomaly_" + numAno
					+ "> <http://www.agtinternational.com/ontologies/I4.0#machine> <http://www.agtinternational.com/ontologies/WeidmullerMetadata#MoldingMachine_"
					+ numMachine + ">";
			String l6 = "<http://yourNamespace/debs2017#Timestamp_" + numTsp
					+ "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.agtinternational.com/ontologies/IoTCore#Timestamp> .";
			String l7 = "<http://yourNamespace/debs2017#Timestamp_" + numTsp
					+ "> <http://www.agtinternational.com/ontologies/IoTCore#valueLiteral> \"" + timestamp
					+ "\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .";

			apres.collect(l1);
			apres.collect(l2);
			apres.collect(l3);
			apres.collect(l4);
			apres.collect(l5);
			apres.collect(l6);
			apres.collect(l7);
		}
	}

}
