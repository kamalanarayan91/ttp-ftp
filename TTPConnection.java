

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;


import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Arrays;
import javax.swing.Timer;

import java.util.concurrent.ConcurrentSkipListMap;


import javax.xml.crypto.Data;

public class TTPConnection
{

    public static final short filename = 1;
    public static final short filecontent = 2;
    public static final short md5 = 3;

    int lastAckSeqNum;
    int windowSize;
    double timeOutInterval;
    
    String sourceIP;
    int sourcePort;
    
    String destIP;
    int destPort;

    //for sender use
    int expectedAckNumber;

    int currentSequenceNumber;
    int nextSequenceNumber;

    //for receiver use
    int expectedSequenceNumberFromServer;

    public static final int MTU_PAYLOAD = 1250;
    public Timer timer;

    public int baseSeqNumber;



    //new
    public int toAcknowledgeNumber;

    DatagramService datagramService;

    LinkedList<Datagram> sentDataGramList;
    LinkedList<Datagram> unAckDataGramList;
    LinkedList<Datagram> sendQueue;


    /**
     * Need to maintain the order!
     */
    ConcurrentSkipListMap<Integer,Datagram> unackDataMap;

    /**
     * TTPConnection()
     */
    public TTPConnection()
    {
        lastAckSeqNum=0;
        currentSequenceNumber = 0;
        expectedSequenceNumberFromServer = 0;
        nextSequenceNumber=0;
        expectedAckNumber = 0;


        sentDataGramList = new LinkedList<Datagram>();
        unAckDataGramList = new LinkedList<Datagram>();
        sendQueue = new LinkedList<Datagram>();

        sourceIP= "";
        destIP= "";
        sourcePort= 0;
        destPort= 0;

        datagramService = null;
        timer = null;
        unackDataMap = new ConcurrentSkipListMap<Integer,Datagram>();
        baseSeqNumber=0;

        toAcknowledgeNumber = 0;

    }

    /**
     * Do Action Listener.
     */
    ActionListener timeout = new ActionListener()
    {
        @Override
        public void actionPerformed(ActionEvent event) {
            for(Integer i: unackDataMap.keySet())
            {
                Datagram tosend = unackDataMap.get(i);
                try
                {
                    datagramService.sendDatagram(tosend);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                //System.out.println("Resending seqN:"+ TTPSegment.getSeqNum((byte[])tosend.getData()));
            }
        }
    };

    /**
     * setTimerdelay
     */
    public void configureTimer()
    {
        timer= new Timer((int)timeOutInterval ,timeout);
    }

    /**
     * setDatagramService():
     * @param ds
     */
    public void setDatagramService(DatagramService ds)
    {
        this.datagramService =ds;
    }


    /**
     * getTimeOutInterval():
     * @return
     */
	public double getTimeOutInterval()
	{
		return this.timeOutInterval;
	}
	public void setTimeOutInterval(double timeout)
	{
		this.timeOutInterval = timeout;
	}

    /**
     *
     * @param count
     */
	/*remove the acked Datagrams from unAckDgList*/
    public void removeAckDataGram(int count)
    {
    	while(count > 0 )
        {
    		unAckDataGramList.remove();
    	}
    }

	public int getCurrentSequenceNumber() {
		return currentSequenceNumber;
	}




	public void setCurrentSequenceNumber(int currentSequenceNumber) {
		this.currentSequenceNumber = currentSequenceNumber;
	}




	public int getExpectedAckNumber() {
		return expectedAckNumber;
	}




	public void setExpectedAckNumber(int expectedAckNumber) {
		this.expectedAckNumber = expectedAckNumber;
	}




	public String getSourceIP() {
		return sourceIP;
	}


	public void setSourceIP(String sourceIP) {
		this.sourceIP = sourceIP;
	}


	public int getSourcePort() {
		return sourcePort;
	}


	public void setSourcePort(int sourcePort) {
		this.sourcePort = sourcePort;
	}


	

	public int getLastAckSeqNum() {
		return lastAckSeqNum;
	}


	public void setLastAckSeqNum(int lastAckSeqNum) {
		this.lastAckSeqNum = lastAckSeqNum;
	}


	public int getWindowSize() {
		return windowSize;
	}

	public void setWindowSize(int windowSize) {
		this.windowSize = windowSize;
	}


	public String getDestIP() {
		return destIP;
	}


	public void setDestIP(String destIP) {
		this.destIP = destIP;
	}


	public int getDestPort() {
		return destPort;
	}


	public void setDestPort(int destPort) {
		this.destPort = destPort;
	}

    /**
     * openConnection(): Sends a SYN Packet and starts the timer, waits for the SYN ACK,sends ACK when
     * it receives it.
     *
     */
	public boolean openConnection()
    {

        currentSequenceNumber++;


        short checksum=0;

        //Syn
		byte[] synHeader = TTPSegment.createHeader((short)sourcePort,(short)destPort,currentSequenceNumber,
                (-1),TTPService.SYN,checksum,-1);
        checksum = TTPSegment.calculateCheckSum(synHeader);
        byte[] synHeaderWithCheckSum = TTPSegment.putChecksum(synHeader, checksum);




		try 
		{	
			short size = TTPSegment.SIZEOF_HEADER;
			Datagram synDatagram = new Datagram(sourceIP,destIP,(short)(sourcePort),
					(short)destPort,size,checksum,synHeaderWithCheckSum);
			datagramService.sendDatagram(synDatagram);
			System.out.println("Sent Syn from Client ");


            timer.start();

            unackDataMap.put(currentSequenceNumber,synDatagram);


            /**
             * wait for syn ack.
             */
			Datagram recvSynAckDatagram = datagramService.receiveDatagram();
            if(!TTPSegment.validateCheckSum(recvSynAckDatagram))
            {
                System.out.println("checksum error!");
                return false;
            }

			if(!validateFlag(recvSynAckDatagram,TTPService.SYNACK))
            {
				return false;
			}

            toAcknowledgeNumber = TTPSegment.getSeqNum((byte[]) recvSynAckDatagram.getData());

            System.out.println("Received Syn Ack from Server with seqN:"+ TTPSegment.getSeqNum((byte[]) recvSynAckDatagram.getData()));
            System.out.println("Received Syn Ack from Server with ackN:"+ TTPSegment.getAckNum((byte[]) recvSynAckDatagram.getData()));

            /**
             * Remove the SYN from the unAck map
             */

            unackDataMap.remove(currentSequenceNumber);
            timer.stop();


            /**
             * Acknumber
             */
            currentSequenceNumber++;
            checksum=0;
            byte[] ackHeader = TTPSegment.createHeader((short)sourcePort,(short)destPort,currentSequenceNumber,
                    toAcknowledgeNumber,TTPService.ACK,checksum,-1);
            checksum = TTPSegment.calculateCheckSum(ackHeader);
            ackHeader = TTPSegment.putChecksum(ackHeader, checksum);

            System.out.println("Sending SYNACK-ACK to Server");
            Datagram ackDatagram = new Datagram(sourceIP,destIP,(short)sourcePort,
					(short)destPort,size,checksum,ackHeader);
			datagramService.sendDatagram(ackDatagram);


            baseSeqNumber = currentSequenceNumber;
		}

		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return false;
	}

    /**
     * closeConnection(): Sends a fin packet to the server for initiating connection closing.
     * @return
     */
	public boolean closeConnection()
	{
        currentSequenceNumber++;
		short checksum = 0;
		
		byte[] finHeader = TTPSegment.createHeader((short)this.sourcePort, (short)this.destPort,currentSequenceNumber,
				-1,TTPService.FIN,checksum,-1);

        checksum = TTPSegment.calculateCheckSum(finHeader);
        finHeader = TTPSegment.putChecksum(finHeader, checksum);


        short size = 0; 	//check size
		try 
		{
			
			Datagram finDatagram = new Datagram(this.sourceIP,this.destIP,(short)this.sourcePort,
				(short)this.destPort,size,checksum,finHeader);
			datagramService.sendDatagram(finDatagram);

            unackDataMap.put(currentSequenceNumber,finDatagram);

			Datagram recvAckDatagram = datagramService.receiveDatagram();

            if(!TTPSegment.validateCheckSum(recvAckDatagram))
            {
                System.out.println("checksum error!");
                return false;
            }

			if(!validateFlag(recvAckDatagram,TTPService.FINACK))
			{
				return false;
			}

		} 
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		return true;
		
	}

    /**
     * Finds out the type of message received by comparing with the required flag field.
     * @param synAckDatagram
     * @param flag
     * @return
     */

	public static boolean validateFlag(Datagram synAckDatagram, short flag)
	{
		if( TTPSegment.getFlags((byte[])synAckDatagram.getData()) == flag)
		{
			//System.out.println("Received flag :: " + flag);
			return true;
		} 
		else
		{
			return false;
		}
		

	}

    /**
     * Receive function for the client side. This will receive the data and react accordingly.
     * @return
     */
	public byte[] receiveData()
	{
	
		ArrayList<Datagram> recvDataGramList = new ArrayList<Datagram>();
		int datapkt = 0;
		byte[] returnByteAry = null;


		try
        {
			Datagram datagram = datagramService.receiveDatagram();

            if(!TTPSegment.validateCheckSum(datagram))
            {
                System.out.println("checksum error!");
                return null;
            }

            //check flags
			if(validateFlag(datagram,TTPService.ACK))
            {
                //for both client and server.
                ackAction(datagram);
			}
            else if(validateFlag(datagram,TTPService.SYNACK))
            {
                //never
			}
            else if (validateFlag(datagram,TTPService.DATA))
            {
                //System.out.println(TTPSegment.getSeqNum((byte[])datagram.getData())+ " "+ Arrays.toString(TTPSegment.extractData((byte[])datagram.getData())));
                System.out.println("expectedSeqNum  "+expectedSequenceNumberFromServer);
                if(expectedSequenceNumberFromServer == TTPSegment.getSeqNum((byte[])datagram.getData()))
                {

                        acknowledegeData();


                    datapkt++;
                    recvDataGramList.add(datagram);


                    while (true)
                    {
                        Datagram datagramDataPkt = datagramService.receiveDatagram();
                        if(!TTPSegment.validateCheckSum(datagramDataPkt))
                        {
                            System.out.println("checksum error!");
                            return null;
                        }

                        if(expectedSequenceNumberFromServer !=TTPSegment.getSeqNum((byte[])datagramDataPkt.getData()))
                        {
                            continue;
                        }
                        else
                        {
                            acknowledegeData();
                        }

                        datapkt++;
                        recvDataGramList.add(datagramDataPkt);

                        if(validateFlag(datagramDataPkt,TTPService.EOF))
                        {


                            byte[] segment = (byte[]) datagramDataPkt.getData();
                            int validPayloadBytes = TTPSegment.getValidPayLoadBytes(segment);


                            ByteBuffer returnData = ByteBuffer.allocate( ( (datapkt - 1) * MTU_PAYLOAD) + validPayloadBytes);


                            for(int dataGramIndex=0;dataGramIndex<recvDataGramList.size();dataGramIndex++)
                            {
                                Datagram temp = recvDataGramList.get(dataGramIndex);
                                returnData.put(TTPSegment.extractData((byte[]) temp.getData()));
                            }


                            returnByteAry = returnData.array();
                           // System.out.println("array:"+Arrays.toString(returnByteAry));
                           // System.out.println(returnByteAry.length);

                            break;


                        }

                    }
                }
                else
                {
                    //discard packet
                }
				
			}
            else if (validateFlag(datagram,TTPService.EOF))
            {
                if(expectedSequenceNumberFromServer ==TTPSegment.getSeqNum((byte[])datagram.getData()))
                {
                    System.out.println("EOF!!!");
                    byte[] segment = (byte[]) datagram.getData();
                    byte[] extractedData = TTPSegment.extractData(segment);
                    int validPayloadBytes = TTPSegment.getValidPayLoadBytes(segment);
                    returnByteAry = new byte[validPayloadBytes];
                    System.arraycopy(extractedData, 0, returnByteAry, 0, returnByteAry.length);
                    acknowledegeData();
                }
            }
			
	 		}
            catch (Exception e)
            {
				e.printStackTrace();
			}


		return returnByteAry;
	}

    /**
     * Send the packets. If the window is full, queue the packets.
     * @param flag
     * @param data
     */
	 public void sendData(short flag, byte[] data)
	 {
            System.out.println("currentseq:"+currentSequenceNumber);


	        int nextSequenceNumber = currentSequenceNumber+1;
            System.out.println("nextseq:"+nextSequenceNumber);
            System.out.println("baseSeq:"+baseSeqNumber);
            System.out.println("ws:"+windowSize);

            if(nextSequenceNumber < baseSeqNumber + windowSize)
            {

                System.out.println("******send*********");
                    System.out.println("seqNo to send!"+nextSequenceNumber);

                    int remainingBytes = data.length;
                    short flags = 0;

                    int currentIndex = 0;
                    int count = 0;

                    while (remainingBytes > 0)
                    {

                        int currentFragSize = Math.min(MTU_PAYLOAD, remainingBytes);
                        byte[] fragment = new byte[currentFragSize];

                        remainingBytes = remainingBytes - currentFragSize;
                        if (currentFragSize == MTU_PAYLOAD)
                        {
                            if (remainingBytes == 0)
                            {
                                //Last fragment
                                flags = TTPService.EOF;
                            }
                            else
                            {
                                flags = TTPService.DATA;
                            }

                        }
                        else
                        {
                            flags = TTPService.EOF;
                        }


                        currentIndex = count;
                        for (int i = currentIndex; i < currentIndex + currentFragSize; i++, count++) {
                            fragment[i % MTU_PAYLOAD] = data[i];
                        }

                        currentSequenceNumber++;


                        short currentChecksum = 0;

                        if(flag == TTPService.FILENAME)
                        {
                            flags = TTPService.FILENAME;
                            expectedSequenceNumberFromServer = currentSequenceNumber;
                        }


                        byte[] header = TTPSegment.createHeader(this.sourcePort, this.destPort, currentSequenceNumber, -1, flags, (short) 0, fragment.length);
                        short checksum = TTPSegment.calculateCheckSum(header);
                        byte[] headerWithCheckSum = TTPSegment.putChecksum(header, checksum);
                        byte[] toSendSegment = TTPSegment.getSegment(headerWithCheckSum, fragment);


                        Datagram datagram = new Datagram(sourceIP, destIP, (short) sourcePort, (short) destPort, (short) -1, (short) -1, (Object) toSendSegment);


                        try
                        {
                            if(nextSequenceNumber == baseSeqNumber)
                                timer.restart();

                            if(nextSequenceNumber < baseSeqNumber + windowSize)
                            {
                                datagramService.sendDatagram(datagram);
                                unackDataMap.put(currentSequenceNumber,datagram);
                                //
                                //System.out.println(currentSequenceNumber + " " + Arrays.toString(fragment));

                            }
                            else
                            {
                                synchronized (sendQueue)
                                {
                                    sendQueue.add(datagram);
                                }
                            }

                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }




                    }

            }
            else
            {
                System.out.println("******NOT send*********");
                    //Dont send
            }


        }
	 
	 

	    /**
	     * Sends response to server. The only response
	     * in this case is ACK as the data is being
	     * send in sender thread
	     */
	    public  void sendServerResponse(short flag)
		{

            int ackNum=0;
			short checksum = 0;
			/*increment the currentSequenceNumber and expected AckNumber*/
			//currentSequenceNumber++;
			expectedAckNumber++;


			byte[] AckHeader = TTPSegment.createHeader(sourcePort,destPort,currentSequenceNumber,
					ackNum,flag,checksum,-1);
			Datagram Ack = new Datagram();


			Ack.setSrcaddr(destIP);
			Ack.setSrcport((short)sourcePort);
			Ack.setDstaddr(sourceIP);
			Ack.setDstport((short)destPort);
			Ack.setData(AckHeader);


			try
			{
				datagramService.sendDatagram(Ack);
            }
			catch (Exception e)
			{
				e.printStackTrace();
			}

	    }

        public void sendSynAck(int synpackSeq)
        {

            short checksum = 0;
			/*increment the currentSequenceNumber and expected AckNumber*/
            currentSequenceNumber++;

            baseSeqNumber = currentSequenceNumber;


            byte[] AckHeader = TTPSegment.createHeader(sourcePort,destPort,currentSequenceNumber,
                    synpackSeq,TTPService.SYNACK,checksum,-1);
            checksum = TTPSegment.calculateCheckSum(AckHeader);
            AckHeader = TTPSegment.putChecksum(AckHeader, checksum);

            Datagram Ack = new Datagram();

            Ack.setSrcaddr(destIP);
            Ack.setSrcport((short)sourcePort);
            Ack.setDstaddr(sourceIP);
            Ack.setDstport((short)destPort);
            Ack.setData(AckHeader);


            try
            {
                datagramService.sendDatagram(Ack);
                unackDataMap.put(currentSequenceNumber,Ack);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

        }

    /**
     * Action to be taken when an ack is received.
     * @param datagram
     */
    public void ackAction(Datagram datagram)
        {

            byte[] segment = (byte[]) datagram.getData();
            int ack = TTPSegment.getAckNum(segment);
            System.out.println("ACk received: AckNO:"+ack);
            if(ack == baseSeqNumber)
            {

                unackDataMap.remove(baseSeqNumber);
                timer.restart();
                baseSeqNumber++;
            }


        }

    /**
     * Used by the server to acknowledge the filename.
     * @param datagram
     */
    public void acknowledgeFileName(Datagram datagram)
    {

            currentSequenceNumber++;

            short checksum = 0;
            int seqNum  = TTPSegment.getSeqNum((byte[]) datagram.getData());
            byte[] fileNameAckHeader = TTPSegment.createHeader(sourcePort,destPort,currentSequenceNumber,
                    seqNum,TTPService.ACK,checksum,-1);

            checksum = TTPSegment.calculateCheckSum(fileNameAckHeader);
            fileNameAckHeader = TTPSegment.putChecksum(fileNameAckHeader, checksum);


            Datagram Ack = new Datagram();

            Ack.setSrcaddr(destIP);
            Ack.setSrcport((short)sourcePort);
            Ack.setDstaddr(sourceIP);
            Ack.setDstport((short)destPort);
            Ack.setData(fileNameAckHeader);

            try
            {
                datagramService.sendDatagram(Ack);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            baseSeqNumber = currentSequenceNumber+1;
            System.out.println("Sent filename ack: base is!!"+ baseSeqNumber);
            //
    }

    /**
     * acknowledgeData: Acknowledges the datagram received. Used by client.
     * @param datagram
     */
	public void acknowledegeData()
    {

        currentSequenceNumber++;

        short checksum = 0;

        System.out.println("Acknowledging seqNo:"+expectedSequenceNumberFromServer);

        byte[] fileNameAckHeader = TTPSegment.createHeader(sourcePort,destPort,currentSequenceNumber,
                expectedSequenceNumberFromServer,TTPService.ACK,checksum,-1);

        checksum = TTPSegment.calculateCheckSum(fileNameAckHeader);
        fileNameAckHeader = TTPSegment.putChecksum(fileNameAckHeader, checksum);


        Datagram Ack = new Datagram();


        Ack.setSrcaddr(destIP);
        Ack.setSrcport((short)sourcePort);
        Ack.setDstaddr(sourceIP);
        Ack.setDstport((short)destPort);
        Ack.setData(fileNameAckHeader);

        try
        {
            datagramService.sendDatagram(Ack);
            expectedSequenceNumberFromServer++;

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }


    }

    /**
     * send FinAck();
     */
    public void sendFinAck(Datagram datagram)
    {

        currentSequenceNumber++;

        short checksum = 0;
        int seqNum  = TTPSegment.getSeqNum((byte[]) datagram.getData());

        byte[] fileNameAckHeader = TTPSegment.createHeader(sourcePort,destPort,currentSequenceNumber,
               seqNum ,TTPService.FINACK,checksum,-1);
        checksum = TTPSegment.calculateCheckSum(fileNameAckHeader);
        fileNameAckHeader = TTPSegment.putChecksum(fileNameAckHeader, checksum);

        Datagram Ack = new Datagram();


        Ack.setSrcaddr(destIP);
        Ack.setSrcport((short)sourcePort);
        Ack.setDstaddr(sourceIP);
        Ack.setDstport((short)destPort);
        Ack.setData(fileNameAckHeader);

        try
        {
            datagramService.sendDatagram(Ack);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }




}
