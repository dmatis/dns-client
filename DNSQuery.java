import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class DNSQuery {
    private String fqdn;
    private InetAddress rootServer;
    private DatagramSocket socket = null;
    private byte[] sendData;
    private byte[] receiveData;
	private int queryID = 0x2b2b;

	public byte[] getReceiveData(){
		return receiveData;
	}
	
	public int getQueryID(){
		return queryID;
	}

    public DNSQuery(String fqdn, InetAddress rootNameServer) throws Exception {
        this.fqdn = fqdn;
		String[] fqdnServers = fqdn.split("\\.");
        this.rootServer = rootNameServer;
        socket = new DatagramSocket();
		int noOfSendDataBytes = getNoOfBytes(fqdnServers);
        sendData = new byte[noOfSendDataBytes + 17];
        receiveData = new byte[512];

		byte[] result = generateRandomQueryID();

		sendData[0] = result[0];
		sendData[1] = result[1];		
        sendData[2] = 0x00;
        sendData[3] = 0x00;
        sendData[4] = 0x00;
        sendData[5] = 0x01;
        sendData[6] = 0x00;
        sendData[7] = 0x00;
        sendData[8] = 0x00;
        sendData[9] = 0x00;
        sendData[10] = 0x00;
        sendData[11] = 0x00; 

        // send DNS Query request
        byte[] domainBytes;
		int i = 0;
		for(int serverCounter = 0; serverCounter < fqdnServers.length; serverCounter++){
			sendData[i + 12] = (byte) fqdnServers[serverCounter].length();
			domainBytes = fqdnServers[serverCounter].getBytes();
			i++;
			for (int j = 0; j < domainBytes.length; j++) {
				sendData[i + 12] = domainBytes[j];
				i++;
			}
		}
		sendData[i+13] = 0x00; 
		sendData[i+14] = 0x01; 
		sendData[i+15] = 0x00; 
		sendData[i+16] = 0x01; 

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, rootServer, 53);
        socket.send(sendPacket);
        socket.setSoTimeout(5000);    //Set 5 sec timeout timer

        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
	}
	
	private int getNoOfBytes(String[] fqdnServers){
		int length = 0;
		for(int serverCounter = 0; serverCounter < fqdnServers.length; serverCounter++){
			byte[] domainBytes = fqdnServers[serverCounter].getBytes();
			length += (domainBytes.length + 1);
		}
		return length;
	}
	
	public byte[] generateRandomQueryID(){
		byte[] result= new byte[2];
		Random random= new Random();
		random.nextBytes(result);
		if(result[0] < 0){
			result[0] *= -1; 
		}
		if(result[1] < 0){
			result[1] *= -1; 
		}
		queryID = (result[0]<<8) | result[1];
		return result;
	}

    public InetAddress getServerAddress() {
        return rootServer;
    }

    public String getQueryAsString() {
        return fqdn;
    }
}
