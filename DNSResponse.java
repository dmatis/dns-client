
import java.net.InetAddress;




public class DNSResponse {
	private DNSQuery query;
	private int queryID;         /* this is for the response it must match the one in the request */
	private byte header[];
	private int byteNo = 0;
	private int answerCount = 0;
	private boolean decoded = false;
	private int nsCount = 0;
	private int additionalCount = 0;
	private boolean authoritative = false;
	private int replyCode = 0xf;
	private RR answerList[];
	private RR nsList[];
	private RR altInfoList[];
	private String aaFQDN; 
	boolean invalidType = false;
	boolean isQuery = true;
	int minTTL = Integer.MAX_VALUE;


	public int getInvalidType() {
		try {
			return altInfoList[0].getType();
		}
		catch (Exception e) {
			return 0;
		}
	}

	public String finalOutput(){
		return answerList[0].getIPaddress().toString().substring(1);
	}

	public int getCNameTTL() {
		return answerList[0].getTtl();
	}
	
	public boolean getAuthoritative(){
		return authoritative;
	}	
	
	public int getAdditionalCount(){
		return additionalCount;
	}
	
	public String getFirstSuggestedDNS(){
		return altInfoList[0].getCNAME();
	}
	
	void dumpResponse() {
		
		System.out.println("\n\nQuery ID     " + queryID + " " + query.getQueryAsString() + " --> " + 
				query.getServerAddress().getHostAddress());
		System.out.println("Response ID: " + queryID + " Authoritative = " + authoritative);

		int i; 
		System.out.println("  Answers (" + answerCount + ")");
		for (i = 0; i < answerCount; i++) {
			answerList[i].printItem();
		}

		System.out.println("  Nameservers (" + nsCount + ")");
		for (i = 0; i < nsCount; i++) {
			nsList[i].printItem();
		}
		System.out.println("  Additional Information (" + additionalCount + ")");
		for (i = 0; i < additionalCount; i++) {
			altInfoList[i].printItem();
		}
	}

	public InetAddress reQueryTo() {
		InetAddress res = null;

		if (decoded && (answerCount == 0) && (additionalCount>0)) 
			for (int i = 0; i < additionalCount; i++) {
				if (altInfoList[i].type == 1) {
					res = altInfoList[i].getIPaddress();	
					break;
				}
			}
		return res;
	}

	public DNSResponse (byte[] data, int len, DNSQuery q) {

		query = q;
		// Extract the ID
		queryID = 	(data[byteNo++] << 8) & 0xff00;
		queryID = queryID | (data[byteNo++] & 0xff);

		// Either not a query response to a standard query
		if ((data[byteNo] & 0xc0) != 0x80 ) {
			return;
		}

		if ((data[byteNo] & 0x4) != 0) {
			authoritative = true;
		}

		byteNo++;  
		replyCode = data[byteNo] & 0xf;

		if (replyCode != 0) {
			return;
		}
		
		byteNo++;

		// Question Count
		int count = (data[byteNo++] << 8) & 0xff00;
		count |= (data[byteNo++] & 0xff);

		// We are only ever going to deal with questions of size 1;
		if (count != 1)
			return; 

		// Answer Count
		answerCount = (data[byteNo++] << 8) & 0xFF00;
		answerCount |=  (data[byteNo++] & 0xff);
		answerList = new RR[answerCount];

		// NS Count
		nsCount = (data[byteNo++] << 8) & 0xFF00;
		nsCount |= (data[byteNo++] & 0xff);
		nsList = new RR[nsCount];

		// AR Count;
		additionalCount = (data[byteNo++] << 8) & 0xFF00;
		additionalCount |= (data[byteNo++] &0xff);
		altInfoList = new RR[additionalCount];

		// Question count of 1;
		aaFQDN = getFQDN(data);
        byteNo += 4;  // skip Qytpe and Qclass
	
		// Answers
		for (int i = 0 ; i < answerCount; i++) {
			answerList[i] = getRR(data);
		}

		// Name servers
		for (int i = 0; i < nsCount; i++) {
			nsList[i] = getRR(data);
		}

		// Additional information
		for (int i = 0; i < additionalCount; i++) {
			altInfoList[i] = getRR(data);
		}

		decoded = true;
	}

	public void updateTTL() {
		try {
			minTTL = answerList[0].ttl;
		}
		catch(Exception e){}
	}

	private String getCompressedFQDN(String fqdn, byte[] data, int offset) {
		boolean firstTime = true;

		try {
			for (int cnt = (data[offset++] &0xff); cnt != 0; cnt = (data[offset++] &0xff)) {
				if (!firstTime) {
					fqdn += '.';
				} else {
					firstTime = false;
				}

				if ((cnt & 0xC0) > 0) {
					cnt = (cnt & 0x3f) << 8;
					cnt |= (data[offset++] & 0xff);
					fqdn = getCompressedFQDN(fqdn, data, cnt);
					break;
				} else {

					for (int i = 0; i < cnt; i++) {
						fqdn = fqdn + (char) data[offset++];
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Something is wrong");
		}

		return fqdn;
	}

	private String getFQDN(byte[] data) {
		String fqdn = new String();
		boolean firstTime = true;
		try {
			for (int cnt = (data[byteNo++] &0xff); cnt != 0; cnt = (data[byteNo++] & 0xff)) {

				if (!firstTime) { 
					fqdn += '.';
				} else {
					firstTime = false;
				}

				if ((cnt & 0xC0) > 0) {
					cnt = (cnt &0x3f) << 8;
					cnt |= (data[byteNo++] & 0xff);
					fqdn = getCompressedFQDN(fqdn, data, cnt);
					break;
				} else {
					for (int i = 0; i < cnt; i++) {
						fqdn += (char) data[byteNo++];
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception 2");
		}

		return fqdn;

	}

	public InetAddress getIPaddr() {
		if (answerCount >= 1) {
			if (answerList[0].type == 1) {
				return answerList[0].getIPaddress();
			} 
		}
		return (InetAddress) null;
	}

	public String getCNAME() {
		if (answerCount >= 1) {
			if (answerList[0].type == 5) {
				return answerList[0].getCNAME();	
			}
		}
		return null;
	}

	public String getAuthoritativeDNSname() {
		if (nsCount > 0)
			return nsList[0].getCNAME();

		return null;
	}

	public int getMinTTL() {
		return minTTL;
	}

	public int queryID() {
		return queryID;
	}

	public byte[] getHeader() {
		return header;
	}

	public int getLength() {
		return byteNo;
	}

	public int getReplyCode() {
		return replyCode;
	}

	private RR getRR(byte[] data) {

		// First field is a name
		String fqdn = getFQDN(data);

		// 2 bytes of type information
		int type = (data[byteNo++] << 8) &0xFFFF;
		type |= (data[byteNo++] & 0xFF);

		// 2 bytes of class information
		int rclass = (data[byteNo++] << 8) &0xFFFF;
		rclass |= (data[byteNo++] & 0xFF);

		// The TTL value is 4 bytes
		int ttl = 0;
		for (int i= 0; i < 4; i++) {
			ttl = ttl << 8;
			ttl |= (data[byteNo++] & 0xFF);
			//PO
			if(ttl < 0){
				System.out.println("TTL NEGATIVE!!!");
			}

		}

		// Total length of the remaining response data, 
		// The interpretation of this data depends upon the type
		int len = (data[byteNo++] << 8) &0xFFFF;
		len |= (data[byteNo++] & 0xFF);
		
		RR responseRecord = null;
		InetAddress ipAddress = null;

		if (rclass  == 1) {
			if (type == 1) { //IPV4 address 4 bytes long

				byte ip4Addr[] = new byte[4];
				for (int i = 0; i < 4; i++) {
					ip4Addr[i] = data[byteNo + i];
				}
				try {
					ipAddress = InetAddress.getByAddress(ip4Addr);
				} catch (Exception e) {
					System.out.println("AddressConversion failed");
				}
				responseRecord = new IpV4AddressRR(fqdn, type, rclass, ttl, ipAddress);
			} else if ((type == 2) || (type == 5)) {
				// Both types 2 and 5 have a FQDN as their result
				String cn = new String();
				cn = getCompressedFQDN(cn, data, byteNo);
				if (type == 2) {  // Nameserver type record
					responseRecord = new nameServerRR(fqdn, type, rclass, ttl, cn);
				} else {
					responseRecord = new cnameRR(fqdn, type, rclass, ttl, cn);
				}
			} else if (type == 28) {  // IPV6 address
				// Assuming 16 bytes
				int i;
				byte ip6Addr[] = new byte[16];
				for (i = 0; i < 16; i++) {
					ip6Addr[i] = data[byteNo + i];
				}
				try {
					ipAddress = InetAddress.getByAddress(ip6Addr);
				} catch (Exception e) {
					System.out.println("AddressConversion failed");
				}
				responseRecord = new IpV6AddressRR(fqdn, type, rclass, ttl, ipAddress);
			}
			else {
				invalidType = true;
				responseRecord = new RR(fqdn, type, rclass, ttl);
			}
		}
		else {
			invalidType = true;
			responseRecord = new RR(fqdn, type, rclass, ttl);
		}
		byteNo += len;	
		return responseRecord;
	}

	private class RR {
		private String recordName;
		private int type;
		private int rclass;
		private int ttl;

		RR(String n, int t, int r, int tt) {
			recordName = n;
			type = t;
			rclass = r;
			ttl = tt;
		}

		String getName() { return recordName; }
		int getType() { return type; }
		int getRclass() {return rclass; }
		int getTtl() {return ttl;}
		InetAddress getIPaddress() {return null;}
		String getCNAME() { return null; }

		void printFormattedItems(String recordType, String recordValue) {
			System.out.format("       %-30s %-10d %-4s %s\n", recordName, ttl, recordType, recordValue);
		}

		void printItem() {
			printFormattedItems(Integer.toString(type), "----");
		}

	}

	class nameServerRR extends RR {
		String serverName;
		nameServerRR(String n, int t, int r, int tt, String nm) {
			super(n, t, r, tt);
			serverName = nm;
		}
		String getCNAME() { return serverName; }
		void printItem() {
			printFormattedItems("NS", serverName);
		}
	}

	class cnameRR extends RR {
		String cname;
		cnameRR(String n, int t, int r, int tt, String nm) {
			super(n, t, r, tt);
			cname = nm;
		}
		String getCNAME() { return cname; }
		void printItem() {
			printFormattedItems("CN", cname);
		}
	}

	class IpV4AddressRR extends RR {
		InetAddress addr;
		IpV4AddressRR(String n, int t, int r, int tt, InetAddress ip) {
			super(n, t, r, tt);
			addr = ip;
		}
		InetAddress getIPaddress() { return addr; }

		void printItem() {
			printFormattedItems("A", addr.getHostAddress());
		}
	}
	
	class IpV6AddressRR extends RR {
		InetAddress addr;
		IpV6AddressRR(String n, int t, int r, int tt, InetAddress ip) {
			super(n, t, r, tt);
			addr = ip;
		}
		InetAddress getIPaddress() { return addr; }

		void printItem() {
			printFormattedItems("AAAA", addr.getHostAddress());
		}
	}
}

