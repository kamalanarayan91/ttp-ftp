

import java.io.Serializable;
import java.nio.*;
import java.util.Arrays;

public class TTPSegment implements Serializable{
	

	
    public static final int SIZEOF_HEADER = 24;
    public static final int SIZEOF_INT =4;
    public static final int SIZEOF_SHORT =2 ;




	public static byte[] createHeader(int srcPort,int dstPort,int seqNum,int ackNum,short flags,short checksum,int validBytes)
    {

        byte[] header = new byte[24];

        //srcport
        byte[] srcPortBuffer = ByteBuffer.allocate(SIZEOF_INT).putInt(srcPort).array();
        System.arraycopy(srcPortBuffer,0,header,0,SIZEOF_INT);
        
        //dstport
        byte[] dstPortBuffer = ByteBuffer.allocate(SIZEOF_INT).putInt(dstPort).array();
        System.arraycopy(dstPortBuffer,0,header,4,SIZEOF_INT);

        //Seq Num
        byte[] seqNumBuffer = ByteBuffer.allocate(SIZEOF_INT).putInt(seqNum).array();
        System.arraycopy(seqNumBuffer,0,header,8,SIZEOF_INT);

        //Ack No
        byte[] ackNumBuffer = ByteBuffer.allocate(SIZEOF_INT).putInt(ackNum).array();
        System.arraycopy(ackNumBuffer,0,header,12,SIZEOF_INT);

        //ValidBytes
        byte[] validByteBuffer = ByteBuffer.allocate(SIZEOF_INT).putInt(validBytes).array();
        System.arraycopy(validByteBuffer,0,header,16,SIZEOF_INT);

        //flags
        byte[] flagBuffer = ByteBuffer.allocate(SIZEOF_SHORT).putShort(flags).array();
        System.arraycopy(flagBuffer,0,header,20,SIZEOF_SHORT);

        //checksum
        byte[] checksumBuffer = ByteBuffer.allocate(SIZEOF_SHORT).putShort(checksum).array();
        System.arraycopy(checksumBuffer,0,header,22,SIZEOF_SHORT);

        return header;

    }

    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getSrcPort(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,0,4);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getInt();
    }

    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getDstPort(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,4,8);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getInt();
    }


    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getSeqNum(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,8,12);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getInt();
    }

    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getAckNum(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,12,16);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getInt();
    }

    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getValidPayLoadBytes(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,16,20);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getInt();
    }

    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static int getFlags(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,20,22);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getShort();
    }


    /**
     * Default uses Big Endian, Little Endian - set if needed.
     * @param segment
     * @return
     */
    public static short getCheckSum(byte[] segment)
    {
        byte[] tempPort =  Arrays.copyOfRange(segment,22,24);

        ByteBuffer temp = ByteBuffer.wrap(tempPort);
        //temp.order(ByteOrder.LITTLE_ENDIAN);
        return temp.getShort();
    }


    /**
     * Assumption: Everything is in proper order.
     * @param header
     * @param fragment
     * @return
     */
    public static byte[] getSegment(byte[] header, byte[] fragment)
    {
    	byte[] segment = new byte[header.length + fragment.length];
        System.out.println("headerLength:"+header.length);
        System.out.println("fragmentLength:"+fragment.length);

        System.arraycopy(header,0,segment,0,header.length);
        System.arraycopy(fragment,0,segment,header.length,fragment.length);

        return segment;
    }
    
    /***
    * Extracts only the header
    */
   public static byte[] extractHeader(byte[] segment)
   {
       return Arrays.copyOfRange(segment,0,SIZEOF_HEADER);
   }
   
   public static byte[] extractData(byte[] segment)
   {
       return Arrays.copyOfRange(segment,24,segment.length);
   }
   
   
   

   /**
    * Calculates the checksum. - Assumption- ttpsegment has even number of bytes.
    * @param ttpsegment
    * @return
    */
   public static short calculateCheckSum(byte[] segment)
   {
       short resultChecksum=0;
       int sum=0;
       int bytesServiced = 0;



       int consideredBytes=0;

       int first=0,second=0;
       int byteIndex=0;



       byte[] ttpSegmentBytes = segment;

       while(consideredBytes<ttpSegmentBytes.length)
       {
           first = ttpSegmentBytes[byteIndex];
           second= ttpSegmentBytes[byteIndex+1];

           first = (first<<8); //0xFF00 needed?

           int twoBytes = first|second;
           sum = sum+twoBytes;

           if(sum > 0xFFFF)//carry happened;
           {
               sum = sum & 0xFFFF;
               sum = sum+1;
           }

           consideredBytes+=2;
           byteIndex+=2;

       }

       sum = ~sum;
       sum = sum & 0xFFFF;//16 bits

       resultChecksum = (short) sum;
       //System.out.println("resultCheckSum:"+resultChecksum);
       return resultChecksum;

   }
   
   

   /**
    * putCheckSum: Puts the checksum into the header.
    * @param segment
    * @param checksum
    * @return
    */
   public static byte[] putChecksum(byte[] segment,short checksum)
   {
       byte[] temp = segment;
       byte[] checkSum = ByteBuffer.allocate(SIZEOF_SHORT).putShort(checksum).array();
       temp[22]= checkSum[0];
       temp[23] = checkSum[1];
       return temp;
   }

    /**
     * validate Checksum
     */

    public static boolean validateCheckSum(Datagram datagram)
    {
        //System.out.println("sss:"+Arrays.toString((byte[])datagram.getData()));
        byte[] data = (byte[]) datagram.getData();
        short recvChecksum = TTPSegment.getCheckSum(data);
        data = TTPSegment.putChecksum(data,(short)0);
        short calc = TTPSegment.calculateCheckSum(extractHeader(data));
        //System.out.println("Got checksum:"+recvChecksum);
        //System.out.println("Rec checksum:"+calc);
        if(calc!=recvChecksum)
            return false;
        else
            return true;
    }
    


}
