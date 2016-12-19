import java.io.*; 
import java.net.*; 
import java.util.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.*;
import java.util.Arrays;
class router {

	static String router_ids = null;
	static InetAddress nse_host = null;
	static String nse_ports = null;
	static String router_ports = null;

	static int router_id = 0;
	static int nse_port = 0;
	static int router_port = 0;

	static int totallink = 0;

	static byte[] sendData = new byte[1024];
	static byte[] receiveData = new byte[1024];
	static DatagramSocket router;

	static Vector<pkt_LSPDU> data = new Vector<pkt_LSPDU>();//save all the recived lspdu

	static circuit_DB mydb;

	static Vector<Vector<Integer>> myrib = new Vector<Vector<Integer>>();// = new array();

	static Vector<Vector<Integer>> myribshort = new Vector<Vector<Integer>>();// = new array();//a 5 x 10 grid to store the router and cost


	static int NBR_ROUTER = 5;

	public static void printrib(PrintWriter log){//print rib
		log.println();
		log.println("router_id: " + router_id);
		log.println("# RIB");
		for (int i = 0; i < 5 ; ++i) {
			data.trimToSize();
			if(i == router_id - 1){
				log.println("R" + router_id + " -> " + "R" + router_id + " -> Local, 0");
			}
			else if(myrib.get(i).get(1) == 65535){
				log.println("R" + router_id + " -> " + "R" + (i + 1) + " -> INF, INF");
			}
			else{
				log.println("R" + router_id + " -> " + "R" + (i + 1) + " -> " + "R" + myrib.get(i).get(0) + " cost " + myrib.get(i).get(1));
			}
		}
		log.println();
		log.flush();
	}

	public static void printtopology(PrintWriter log){//print topology
		log.println();
		log.println("router_id: " + router_id);
		log.println("# Topology database");
		for (int i = 0; i < 5; ++i) {
			data.trimToSize();
			Vector<pkt_LSPDU> tempdata = new Vector<pkt_LSPDU>();
			for (int j = 0; j < data.capacity(); ++j) {
				//System.out.println(data.capacity());
				if(data.get(j).router_id == i + 1){
					tempdata.addElement(data.get(j));
				}
			}
			tempdata.trimToSize();
			if(tempdata.capacity() != 0){
				log.println("R" + router_id + " -> " + "R" + (i + 1) + " " + "nbr " + "link " + tempdata.capacity());
				for (int k = 0; k < tempdata.capacity(); ++k) {
					log.println("R" + router_id + " -> " + "R" + (i + 1) + " link " + tempdata.get(k).link_id + " cost " + tempdata.get(k).cost);
				}
			}
		}
		log.println();
		log.flush();
	}
	public static void sendsomething(int send, int routerid, int linkid, int costt, int via) throws Exception {//send packet
		ByteBuffer buffersendback = ByteBuffer.allocate(20);
		buffersendback.order(ByteOrder.LITTLE_ENDIAN);
		buffersendback.putInt(send);
		buffersendback.putInt(routerid);
		buffersendback.putInt(linkid);
		buffersendback.putInt(costt);
		buffersendback.putInt(via);
		sendData = buffersendback.array();
		DatagramPacket sendback = new DatagramPacket(sendData, sendData.length, nse_host, nse_port);
		router.send(sendback);
	}

	public static boolean isInteger( String input ){//function to check whether a string is all numbers
	    try{
	      Integer.parseInt(input);
	      return true;
	    }
	    catch(Exception e){
	      return false;
	    }
	}

	static void alg(){
		Vector<Integer> stack = new Vector<Integer>();
		int[] nei = new int[5];
		int[] dis = new int[5];
		for (int i = 0; i < 5; ++i) {//5 neis
			stack.add(i);
			nei[i] = i;
			dis[i] = 65535;//set as the max value
		}
		dis[router_id - 1] = 0;//Rx -> Rx cost 0  
		stack.trimToSize();
		//int min = 65535;
		while(stack.capacity() != 0){
			int min = 65535;
			int temp = 0;
			//find shortest 
			for (int i = 0; i < stack.capacity(); ++i) {
				if(dis[stack.get(i)] < min){
					min = dis[stack.get(i)];
					temp = i;
					//break;
				}
			}
			for (int i = 0; i < 5; ++i) {
				if(myribshort.get(i).get(1 + stack.get(temp) * 2) < 65535){
					int temp2 = dis[stack.get(temp)] + myribshort.get(i).get(1 + (stack.get(temp) * 2));
					if(temp2 < dis[i]){//compare with the original cost
						dis[i] = temp2;//if yes store the new cost
						if(router_id - 1 != stack.get(temp)){//if not itself then renew the nei
							nei[i] = nei[stack.get(temp)];
						}
						else{
							nei[i] = nei[i];
						}
					}
				}
			}
			stack.remove(temp);
			stack.trimToSize();
		}
		for (int i = 0; i < 5; ++i) {//renew the shortest path
			myrib.get(i).set(0, myribshort.get(router_id - 1).get(nei[i] * 2));
			myrib.get(i).set(1, dis[i]);
		}
	}
	
	public static void helloandlisten(PrintWriter log) throws Exception {
		//send hello
		for (int i = 0; i < totallink ; ++i) {
			ByteBuffer bufferhello = ByteBuffer.allocate(8);
			bufferhello.order(ByteOrder.LITTLE_ENDIAN);
			bufferhello.putInt(router_id);
			bufferhello.putInt(mydb.links[i].link);
			sendData = bufferhello.array();
			DatagramPacket hello = new DatagramPacket(sendData, sendData.length, nse_host, nse_port);
			router.send(hello);
			//log.println("R" + router_id + " sends an hello: to R" + mydb.links[i].link + 1 + " ");
		}

		//waitting for hello or lspdu
		while (true) {
			DatagramPacket received = new DatagramPacket(receiveData, receiveData.length);
			router.receive(received);
			ByteBuffer receivedd = ByteBuffer.wrap(receiveData);
			receivedd.order(ByteOrder.LITTLE_ENDIAN);
			//LSPDU
			if(received.getLength() != 8){
				boolean change = false;
				pkt_LSPDU temppdu = new pkt_LSPDU(receivedd.getInt(), receivedd.getInt(), receivedd.getInt(), receivedd.getInt(), receivedd.getInt());
				log.println("R" + router_id + " receives an LS PDU: sender " + temppdu.sender +  ", router_id" + temppdu.router_id + ", link_id" + temppdu.link_id + ", cost" + temppdu.cost + ", via" + temppdu.via);
				for(int i = 0; i < data.capacity(); ++i){//to check whether recived the lspdu before
					if(data.get(i).router_id == temppdu.router_id){
						if(data.get(i).link_id == temppdu.link_id){
							change = true;
							break;
						}
					}
				}
				if(!change){//first recive this lspdu
					
					data.addElement(temppdu);//add the database
					boolean changemyribshort = false;
					data.trimToSize();
					for (int i = 0;  i < data.capacity(); ++i) {
						if(temppdu.link_id == data.get(i).link_id){//if have the same link_id of the exist lspdu then renew the myribshort
							myribshort.get(data.get(i).router_id - 1).set((temppdu.router_id - 1) * 2, temppdu.router_id);
							myribshort.get(data.get(i).router_id - 1).set(((temppdu.router_id - 1) * 2) + 1, temppdu.cost);
							myribshort.get(temppdu.router_id - 1).set((data.get(i).router_id - 1) * 2, (data.get(i).router_id));
							myribshort.get(temppdu.router_id - 1).set(((data.get(i).router_id - 1) * 2) + 1, temppdu.cost);
							changemyribshort = true;
						}
						if(changemyribshort){//and through the Dijkatra alg the get the shoutest path
							alg();
						}
					}

					//send to other nei
					for (int i = 0; i < totallink; ++i) {
						if(mydb.links[i].link != temppdu.via){
							sendsomething(router_id, temppdu.router_id, temppdu.link_id, temppdu.cost, mydb.links[i].link);
						}
					}
					printtopology(log);
					//find the shortest path
					// for (int i = 0; i < 5; ++i) {
					// 	if(i == temppdu.router_id - 1){
					// 		int temp1 = myrib.get(temppdu.sender-1).get(1);
					// 		int temp2 = myrib.get(i).get(1);

					// 		if(temp1 + temppdu.cost < temp2){
					// 			myrib.get(temppdu.router_id - 1).set(0, temppdu.sender);
					// 			myrib.get(temppdu.router_id - 1).set(1, temp1 + temppdu.cost);
					// 		}else{
					// 			myrib.get(temppdu.router_id - 1).set(0, temppdu.router_id - 1);
					// 			myrib.get(temppdu.router_id - 1).set(1, temp2);
					// 		}
					// 	}
					// }
					printrib(log);
				}
			}
			//hello
			else{
				int send = receivedd.getInt();
				int link = receivedd.getInt();
				for (int i = 0; i < totallink ; ++i) {
					if (link == mydb.links[i].link) {
						myrib.get(send - 1).set(0, send - 1);
						myrib.get(send - 1).set(1, mydb.links[i].cost);
						break;
					}
				}
				printrib(log);
				//sendback lspdus
				for(int i = 0; i < data.capacity(); ++i){
					sendsomething(data.get(i).sender, data.get(i).router_id, data.get(i).link_id, data.get(i).cost, link);
				}
			}
		}
	}
	public static void main(String args[]) throws Exception {


		if(args.length != 4){//check the number of arguments
			System.out.println("Please enter <router_id>, <nse_host>, <nse_port> and <router_port>");
			return;
		}

		router_ids = args[0];
		nse_host = InetAddress.getByName(args[1]);
		nse_ports = args[2];
		router_ports = args[3];
		

		if(!((isInteger(router_ids)) && (isInteger(nse_ports)) && (isInteger(router_ports)))){//check the req_code whether all numbers
			System.out.println("Please enter an integer router_ids/nse_ports/router_ports");
			return;
		}
		router_id = Integer.valueOf(router_ids);
		nse_port = Integer.valueOf(nse_ports);
		router_port = Integer.valueOf(router_ports);

		router = new DatagramSocket(router_port);
		
		PrintWriter log = new PrintWriter("router" + router_id + ".log");

		//INIT
		ByteBuffer bufferinit = ByteBuffer.allocate(4);
		bufferinit.order(ByteOrder.LITTLE_ENDIAN);
		bufferinit.putInt(router_id);
		sendData = bufferinit.array();
		DatagramPacket sendinit = new DatagramPacket(sendData, sendData.length, nse_host, nse_port);
		router.send(sendinit);

		//receive db
		DatagramPacket receivedb = new DatagramPacket(receiveData, receiveData.length);
		router.receive(receivedb);
		ByteBuffer bufferreceive = ByteBuffer.wrap(receiveData);
		bufferreceive.order(ByteOrder.LITTLE_ENDIAN);
		totallink = bufferreceive.getInt();

		//creat my circuit
		int temp = 0;
		mydb = new circuit_DB();
		mydb.nbr_link = totallink;
		while(temp < totallink){
			mydb.links[temp] = new link_cost();
			mydb.links[temp].link = bufferreceive.getInt();
			mydb.links[temp].cost = bufferreceive.getInt();
			temp++;
		}
		temp = 0;

		//creat rib
		//myrib = new HashMap<Integer, Integer>();
		//5x2 form
		myrib.addElement(new Vector<Integer>());
		myrib.get(0).addElement(0);
		myrib.get(0).addElement(65535);

		myrib.addElement(new Vector<Integer>());
		myrib.get(1).addElement(0);
		myrib.get(1).addElement(65535);

		myrib.addElement(new Vector<Integer>());
		myrib.get(2).addElement(0);
		myrib.get(2).addElement(65535);

		myrib.addElement(new Vector<Integer>());
		myrib.get(3).addElement(0);
		myrib.get(3).addElement(65535);

		myrib.addElement(new Vector<Integer>());
		myrib.get(4).addElement(0);
		myrib.get(4).addElement(65535);

		myrib.addElement(new Vector<Integer>());
		myrib.get(router_id-1).set(0, router_id);
		myrib.get(router_id-1).set(1, 0);
		myrib.trimToSize();
		//5x10 form
		for (int i = 0; i < 5 ; ++i) {
			myribshort.addElement(new Vector<Integer>());
			for (int k = 0; k < 10; ++k) {
				myribshort.get(i).addElement(-1);
				myribshort.get(i).addElement(65535);
			}
			myribshort.get(i).set(i * 2, -1);
			myribshort.get(i).set(i * 2 + 1, 0);
			myribshort.trimToSize();
		}

		//creat  LINK STATE DATABASE
		//data = new pkt_LSPDU[totallink];
		while(temp < totallink){
			data.addElement(new pkt_LSPDU(router_id, mydb, temp));
			temp++;
		}
		data.trimToSize();
		temp = 0;
		printtopology(log);
		printrib(log);
		helloandlisten(log);
	}
}
class pkt_HELLO {
  		public int router_id;
  		public int link_id;
  	}

	class pkt_LSPDU {
  		public int sender;
  		public int router_id;
  		public int link_id;
  		public int cost;
  		public int via;
  		public pkt_LSPDU(int router_id, circuit_DB mydb,int temp){
  			sender = router_id;
  			this.router_id = router_id;
  			link_id = mydb.links[temp].link;
  			cost = mydb.links[temp].cost;
  		};
		public pkt_LSPDU(int sender, int router_id, int link_id, int cost, int via){
  			this.sender = sender;
  			this.router_id = router_id;
  			this.link_id = link_id;
  			this.cost = cost;
  			this.via = via;
  		};
  	}

	class pkt_INIT {
  		public int router_id;
  	}

	class link_cost	{
  		public int link;
  		public int cost;
  	}

	class circuit_DB {
		public int nbr_link;
		public link_cost[] links = new link_cost[5];
	}