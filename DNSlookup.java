
import java.net.InetAddress;

/**
 * 
 */

/**
 * @author Peter Ostrovsky & Darren Matis
 * Adapted from work by Donald Acton
 *
 */
public class DNSlookup {

	static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
	static final int MAX_ITERATIONS = 30;
	static boolean tracingOn = false;
	static InetAddress rootNameServer;
	static DNSResponse response;
	static String origFQDN;
	static boolean nsRetrieval = false;
	static int counter = 0;
    static boolean firstTimeOut = true;
	static int DNSminTTL = Integer.MAX_VALUE;

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		String fqdn;

		int argCount = args.length;

		if (argCount < 2 || argCount > 3) {
			usage();
			return;
		}

		rootNameServer = InetAddress.getByName(args[0]);
		fqdn = args[1];

		if (argCount == 3 && args[2].equals("-t"))
			tracingOn = true;

		origFQDN = fqdn;
        try {
            getResp(fqdn, rootNameServer);

			if(response.getReplyCode() == 3) {
				System.out.println(origFQDN + " -1 0.0.0.0");
			}
            else if (!response.invalidType) {
				System.out.println(origFQDN + " " + DNSminTTL + " " + response.finalOutput());
			}
            else System.out.println(origFQDN + " -4 0.0.0.0");
        }
		catch (MaximumIterationsException me) {
            System.out.println(origFQDN + " -3 0.0.0.0");
        }
		catch (Exception e) {
            if (!firstTimeOut) {
                //query timed out and not the first occurrence
                System.out.println(origFQDN + " -2 0.0.0.0");
            }
            else System.out.println(origFQDN + " -4 0.0.0.0");
        }
	}
	
	private static void getResp(String fqdn, InetAddress nameServer) throws MaximumIterationsException, Exception{
		counter ++;
		if(counter > MAX_ITERATIONS){
			throw new MaximumIterationsException();
		}

        DNSQuery query = null;
        try {
            query = new DNSQuery(fqdn, nameServer);
        }
        catch (java.net.SocketTimeoutException e) {
            if (firstTimeOut) {
                //On the first time out, retry the query
                firstTimeOut = false;
                System.out.println("\n**REQUERYING DUE TO TIMEOUT**");
                getResp(fqdn, nameServer);
            }
        }

        response = new DNSResponse(query.getReceiveData(), query.getReceiveData().length, query);

		response.updateTTL();

		if (response.getMinTTL() < DNSminTTL) {
			DNSminTTL = response.getMinTTL();
		}

        if (tracingOn) {
            response.dumpResponse();
        }

        if (response.getAuthoritative() && response.getCNAME() != null) {
            //use CNAME to find the IP address of the next DNS server
            //Record the minTTL to display in final result
            getResp(response.getCNAME(), rootNameServer);

        } else if (response.getAuthoritative()) {
            //check if currently performing NS IP address retrieval
            if (nsRetrieval) {
                nsRetrieval = false;
                getResp(origFQDN, response.getIPaddr());
            }
            //Answer received
            return;
        } else if (response.getAdditionalCount() == 0) {
            //need to perform NS address retrieval
            nsRetrieval = true;
            getResp(response.getAuthoritativeDNSname(), rootNameServer);
        } else {
            getResp(fqdn, response.reQueryTo());
        }
    }

	private static void usage() {
		System.out.println("Usage: java -jar DNSlookup.jar rootDNS name [-t]");
		System.out.println("   where");
		System.out.println("       rootDNS - the IP address (in dotted form) of the root");
		System.out.println("                 DNS server you are to start your search at");
		System.out.println("       name    - fully qualified domain name to lookup");
		System.out.println("       -t      -trace the queries made and responses received");
	}
	/**
 * My custom exception class.
 */
	private static class MaximumIterationsException extends Exception{
	}
}



