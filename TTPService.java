

import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import java.util.LinkedList;
import java.util.Queue;



public class TTPService {

	public int windowSize;
	public double rtTimerInterval;
	public static DatagramService ds;
	public static final short SYN = 1; 
	public static final short ACK = 2;
	public static final short SYNACK = 3;
	public static final short FIN = 4;
	public static final short DATA = 5;
	public static final short EOF = 6;
	public static final short FILE = 7;
    public static final short FILENAME = 8;
    public static final short SYNACKACK = 9;
    public static final short FINACK = 10;
    public static final short FINACKACK = 11;

	public static ConcurrentHashMap<String,TTPConnection> openConnectionMap;

	private static Queue<String> sharedQueue = new LinkedList<String>();

	//private static
	public TTPService(int port)
    {
		windowSize=0;
		rtTimerInterval=0;
		openConnectionMap = new ConcurrentHashMap<String,TTPConnection>();

		try
        {
			ds = new DatagramService(port, 10);
		}
        catch (SocketException e)
        {
			e.printStackTrace();
		}
	}
	
	public String receiver()
    {
		
		/**
		 * Get the response datagram from the client
		 */
		
		//	1. Receive the datagram
		//  1. a) create connection object
		//  2. spawn response sender thread
		//  2. a) use the connection object to send an ack.
		 String key = null;

		try
        {
			//1. Receive the datagram
			Datagram datagram = ds.receiveDatagram();
            if(!TTPSegment.validateCheckSum(datagram))
            {
                System.out.println("checksum error!");
                System.exit(0);
            }

            key = datagram.getSrcaddr()+":"+Short.toString(datagram.getSrcport());

            TTPConnection ttpCon = null;

			//check SYN packet
			if(TTPConnection.validateFlag(datagram, TTPService.SYN)) {
			//  1. a) create connection object
                if(openConnectionMap.get(key)==null)
                {
                    ttpCon = new TTPConnection();

                    ttpCon.setDestIP(datagram.getSrcaddr());

                    ttpCon.setSourceIP(datagram.getDstaddr());
                    ttpCon.setSourcePort(datagram.getDstport());
                    ttpCon.setDestPort(datagram.getSrcport());
                    ttpCon.setExpectedAckNumber(0);
                    ttpCon.setLastAckSeqNum(0);
                    ttpCon.setCurrentSequenceNumber(0);
                    ttpCon.setDatagramService(ds);
                    ttpCon.setWindowSize(windowSize);
                    ttpCon.setTimeOutInterval(rtTimerInterval);
                    ttpCon.configureTimer();
                    openConnectionMap.put(key, ttpCon);



                }
                else
                {
                    System.out.println("Excess SYN Packet");
                }
			}
            if(TTPConnection.validateFlag(datagram,TTPService.SYNACK))
            {
                System.out.println("loooped!");
            }

			
			//spawn a new  responseSender Thread 
			//uses key to access connObject and send and ack
			Thread responseSenderThread = new HelperThread(key,datagram);
			responseSenderThread.start();

            try
            {
                //Wait for the worker thread to updat
                Thread.sleep(1000);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
			/*always returns first client data String check for null*/
            if(sharedQueue.isEmpty() == false)
            {
                return sharedQueue.poll();
            }
            else
            {
                return null;
            }

		}
        catch (Exception e)
        {
			e.printStackTrace();
		}
		
		return null;
		
	}
	
	public int getWindowSize()
    {
		return windowSize;
	}

	public void setWindowSize(int windowSize)
    {
		this.windowSize = windowSize;
	}

	public double getRtTimerInterval()
    {
		return rtTimerInterval;
	}

	public void setRtTimerInterval(double rtTimerInterval) {
		this.rtTimerInterval = rtTimerInterval;
	}

	
	public int sender(String clientIP,String clientPort, byte[] byteStream,short flag)
    {




        String key = clientIP +":" + clientPort;
	
		Thread dataSenderThread = new Thread(new DataSenderThread(key,byteStream,flag));
        dataSenderThread.start();

		return -1;
	}
	
	 /**
	  * 
	  * Helper thread class to send acks in case of SYN
	  * Check the ackNum in TTPConn obj
	  *
	  */
	 public class HelperThread extends Thread {
		 
		 public HelperThread(String key, Datagram datagram)
         {
			 /*Get TTP Connection Object*/
			 TTPConnection ttpConnectionObject = openConnectionMap.get(key);

			 /*check for SYN; simply send the ACK */
			 if(TTPConnection.validateFlag(datagram, TTPService.SYN))
             {
                 System.out.println("About to send SYNACK");
                 ttpConnectionObject.sendSynAck(TTPSegment.getSeqNum((byte[])datagram.getData()));
			 }

			 /*if it is ACK; check if it the expected one*/
			 else if(TTPConnection.validateFlag(datagram, TTPService.ACK))
             {
                 System.out.println("Received ACK with seqNum:"+TTPSegment.getSeqNum((byte[]) datagram.getData()));
                 ttpConnectionObject.ackAction(datagram);
			
             }
			
			 /*added additional FLAG as we cannot validate the data and say if the text is a
			  * filename. Even the random data can contain FileName*/
			 else if(TTPConnection.validateFlag(datagram, TTPService.FILENAME))
             {


                 System.out.println("FILENAME RECEVIED");
				 byte[] segment = (byte[]) datagram.getData();
                 byte[] header = TTPSegment.extractHeader(segment);
				 byte[] segmentData = TTPSegment.extractData(segment);

				 int validPayload = TTPSegment.getValidPayLoadBytes(header);

				 byte[] fileName = Arrays.copyOf(segmentData, validPayload);

                 for(int index=0;index<fileName.length;index++)
                 {
                    //System.out.println(index+" "+ fileName[index]);
                 }

                 String name= null;
                 try
                 {
                     name = new String(fileName, "UTF-8");
                 }
                 catch(Exception e)
                 {

                 }
				 String clientInfo = datagram.getSrcaddr() + ":" + datagram.getSrcport() + ":" +name;
                 //System.out.println(clientInfo);
                 ttpConnectionObject.acknowledgeFileName(datagram);


				 synchronized(sharedQueue)
                 {
					 sharedQueue.add(clientInfo);
				 }
			 }
             else if( TTPConnection.validateFlag(datagram,TTPService.FIN))
             {
                 String clientInfo = datagram.getSrcaddr() + ":" + datagram.getSrcport();
                 ttpConnectionObject.sendFinAck(datagram);
                 //remove the client from the map
                 openConnectionMap.remove(clientInfo);
             }
			 
			 
		 }
		 
	 }

    /**
     * Thread to send the file data to the client.
     */
    public class DataSenderThread implements  Runnable
    {
        public String key;
        public byte[] data;
        public short flag;
        public DataSenderThread(String key, byte[] data,short flag)
        {
            this.key = key;
            this.data = data;
            this.flag =flag;
        }

        @Override
        public void run()
        {
            try {

                TTPConnection ttpCon = openConnectionMap.get(this.key);

                ttpCon.sendData(flag,data);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

    }
}