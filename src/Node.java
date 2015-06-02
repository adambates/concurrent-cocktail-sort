import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Node {

	private int masterPort;
	private String masterAddress;
	private Socket masterSocket;
	private DataInputStream masterInput;
	private DataOutputStream masterOutput;

	private int port;
	private ServerSocket serverSocket;

	/* Right Node Information */
	private int rightPort;
	private InetAddress rightAddress;
	private Socket rightSocket;
	private DataInputStream rightInput;
	private DataOutputStream rightOutput;

	/* Left Node Information */
	private Socket leftSocket;
	private DataInputStream leftInput;
	private DataOutputStream leftOutput;

	private boolean run = false;

	private int nodeType;
	private int list[];

	private boolean noUpSwap = false;
	private boolean noDownSwap = false;
	private boolean sent = false;
	private boolean out = false;

	/* Node constructor */
	public Node(int masterPort, String masterAddress, int port) {		
		this.masterPort = masterPort;
		this.masterAddress = masterAddress;
		this.port = port;
	}

	/* Setup connection to master and listening socket */
	public boolean setupConnections(){
		try {
			serverSocket = new ServerSocket(port);
		} catch (IOException e) {
			System.out.println("Couldn't create serverSocket");
			return false;
		}
		try {
			masterSocket = new Socket(masterAddress, masterPort);
			masterInput = new DataInputStream(masterSocket.getInputStream());
			masterOutput = new DataOutputStream(masterSocket.getOutputStream());
		} catch (UnknownHostException e) {
			System.out.println("Couldn't Connect to master");
			return false;
		} catch (IOException e) {
			System.out.println("Couldn't Connect to master input/output stream");
			return false;
		}
		return true;
	}

	/* Gets information from the master required for the algorithm.
	 * Information includes: Left and Right neighbour information depending if node is a left(1), right(3) or center(2) node.
	 * The initial list of numbers assigned to this node.
	 */
	public void readData(){
		try {
			nodeType = masterInput.readInt();

			if(nodeType == 2 || nodeType == 3){
				masterOutput.writeInt(port);
			}
			
			
			try{
				if(nodeType == 1 || nodeType == 2){
					int length = masterInput.readInt();
					readRight(length);
				}
			}catch (IOException e){
				e.printStackTrace();
			}
			// Read in list to sort
			int ints = masterInput.readInt();
			list = new int[ints];
			for(int i = 0; i < ints; i++){
				list[i] = masterInput.readInt();
			}
			System.out.println("Finished reading ints");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	/* Reads right node information from the master node 
	 * - right nodes address
	 * - right nodes port number
	 * */
	public void readRight(int length) throws IOException{
		byte[] message = new byte[length];
		masterInput.readFully(message, 0, message.length);
		rightPort = masterInput.readInt();
		rightAddress = InetAddress.getByAddress(message);
		if(rightAddress.isLoopbackAddress()){
			rightAddress = masterSocket.getInetAddress();
		}
	}

	/* Setup neighbouring node connections */
	public void setupNodeConnections(){

		if(nodeType == 2 || nodeType == 3){
			/* New thread that listens for node connection from the left */
			new Thread(new Runnable(){
				@Override
				public void run() {
					try {
						leftSocket = serverSocket.accept();
						System.out.println("Server socket accepted");
						leftInput = new DataInputStream(leftSocket.getInputStream());
						leftOutput = new DataOutputStream(leftSocket.getOutputStream());
						leftOutput.writeBoolean(true);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}).start();
		}
		try {
			// Tell master node that this node is ready
			masterOutput.writeBoolean(true);
			masterOutput.flush();

			// True response so start
			if(masterInput.readBoolean()){
				if(nodeType == 1 || nodeType == 2){
					rightSocket = new Socket(rightAddress, rightPort);
					rightInput = new DataInputStream(rightSocket.getInputStream());
					rightOutput = new DataOutputStream(rightSocket.getOutputStream());

					rightInput.readBoolean();
				}

			}
			masterOutput.writeInt(100);	// Tell master that this worker node is ready to start sorting
			masterOutput.flush();
			run = masterInput.readBoolean();	// On response, can start.
		} catch (IOException e) {
			return;
		}
	}

	/* Runs the shaker sort algorithm between worker nodes */
	public void run(){
		try {
			while(run){
				if(nodeType == 1){
					sendRight();
					recieveRight();
					bubbleDown();
				}
				else if(nodeType == 2){
					recieveLeft();
					sendRight();
					recieveRight();
					sendLeft();
				}
				else if(nodeType == 3){
					recieveLeft();
					bubbleUp();
					sendLeft();
				}
			}
			out = true;
			while(!run){}
			if(!rightSocket.isClosed())
				rightSocket.close();
			if(!masterSocket.isClosed())
				masterSocket.close();			
		} catch (IOException e) {
			return;
		}
	}
	
	/* Send highest value to the right, then compare returned value with highest value, if lower then replace */
	public void sendRight() throws IOException{
		int max = bubbleUp();
		rightOutput.writeInt(max);
		int fromRight = rightInput.readInt();
		if(fromRight < max)
			list[list.length-1] = fromRight; 
	}

	public void recieveRight() throws IOException{
		int fromRight = rightInput.readInt();
		rightOutput.writeInt(list[list.length-1]);
		if(fromRight < list[list.length-1])
			list[list.length-1] = fromRight;
	}

	/* Send lowest value to the left neighbour and compare the returned value to see if it is larger, if so replace */
	public void sendLeft() throws IOException{
		int min = bubbleDown();
		leftOutput.writeInt(min);
		int fromLeft = leftInput.readInt();
		if(fromLeft > list[0])
			list[0] = fromLeft;
	}

	/* Waits to receive value from left neighbour and sends back first value in the list
	 * If received value is greater than lowest value than replace it.
	 *  */
	public void recieveLeft() throws IOException{
		int fromLeft = leftInput.readInt();
		leftOutput.writeInt(list[0]);
		if(fromLeft > list[0])
			list[0] = fromLeft;
	}

	/* Shifts highest number to the top of the list and returns it */
	public int bubbleUp(){
		noUpSwap = true;
		for(int i = 1; i < list.length; i++){
			if(list[i-1] > list[i]){
				int tmp = list[i];
				list[i] = list[i-1];
				list[i-1] = tmp;
				noUpSwap = false;
			}
		}
		return list[list.length-1];
	}

	/* Shifts lowest number to the bottom of the list */
	public int bubbleDown(){
		noDownSwap = true;
		for(int i = (list.length-2); i >= 0; i--){
			if(list[i+1] < list[i]){
				int tmp = list[i];
				list[i] = list[i+1];
				list[i+1] = tmp;
				noDownSwap = false;
			}
		}
		
		// If the worker node is sorted then this new thread is created to communicate to the master node while the main thread
		// is still performing the algorithm so other nodes will continue to be sorted
		if(!sent){
			if(noUpSwap && noDownSwap){
				sent = true;
				new Thread(new Runnable(){
					@Override
					public void run() {
						// If no up swaps and down swaps then node is sorted
						try {
							masterOutput.writeBoolean(true);	// Inform master that we are sorted
							masterOutput.flush();
							masterInput.readBoolean();			// On response we can stop the sort because all other nodes are sorted
							run = false;
							
							// Send sorted list to the master.
							masterOutput.writeInt(list.length);
							masterOutput.flush();
							for(int i = 0; i < list.length; i++){
								masterOutput.writeInt(list[i]);
								masterOutput.flush();
							}
							masterInput.readBoolean();
							System.exit(0); // Terminate application
							run = true;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}).start();
			}
		}
		return list[0];
	}
	
	/* FOR TESTING, Prints list when called */
	private void printList(){
		for(int i = 0; i < list.length; i++)
			System.out.printf("%d ", list[i]);
		System.out.println();
	}

	/* Main */
	public static void main(String[] args) {
		Scanner scan = new Scanner(System.in);
		int serverPort = 0;
		String serverAddress = "";

		System.out.print("Master Node Address?: ");
		serverAddress = scan.nextLine();
		System.out.print("Master Node Port?: ");
		serverPort = scan.nextInt();
		System.out.print("This node ServerSocket port: ");
		int port = scan.nextInt();
		scan.close();

		Node node = new Node(serverPort, serverAddress, port);
		if(node.setupConnections()){
			node.readData();
			node.setupNodeConnections();
			node.run();
		}
		else System.out.println("error");
	}
}