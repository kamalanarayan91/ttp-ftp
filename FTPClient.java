import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.SynchronousQueue;

/**
 * FTP Client: This class is used for requesting a file from the FTP Server and
 * downloading it.
 */
public class FTPClient
{

    public static final byte MD5 = 1;
    public static final byte data = 2;

    //FTPClient
    public static void main(String[] args)
    {
        //  1. Get command line arguments
        if(args.length!=7)
        {
            System.out.println("Usage:java FTPClient <FileName> <ClientIP> <ClientPort> <ServerIP> <ServerPort> <WindowSize> <Interval>");
            System.exit(-1);
        }

        System.out.println("Client for filename:"+args[1]+" clientPort" + args[2]+ " serverport:"+ args[4]);
        DatagramService ds = null;

        //2. Open connection;
        try
        {
            ds = new DatagramService(Short.parseShort(args[2]),10);
        }
        catch(Exception e)
        {
            System.err.println("Error while creting client");
            System.exit(-1);
        }


        //set TTP Connection.
        TTPConnection ttpCon = new TTPConnection();
        ttpCon.setSourceIP(args[1]);
        ttpCon.setSourcePort(Integer.parseInt(args[2]));
        ttpCon.setDestIP(args[3]);
        ttpCon.setDestPort(Integer.parseInt(args[4]));


        //set ds;
        ttpCon.setDatagramService(ds);

        //set parameters
        ttpCon.setWindowSize(Integer.parseInt(args[5]));
        ttpCon.setTimeOutInterval(Double.parseDouble(args[6]));
        ttpCon.configureTimer();
        ttpCon.openConnection();

        //send Filename to Server

        byte[] fileNameBytes= args[0].getBytes();

        ttpCon.sendData(TTPService.FILENAME, fileNameBytes);

        System.out.println("******FN SENT!********");

        //md5 hash is 16 bytes;
        byte[] md5Hash = new byte[16];
        while(true)
        {
            //Receive MD5 Hash
            try
            {


                byte[] received = ttpCon.receiveData();

                if(received==null)
                    continue;
                if (received[0] == FTPClient.MD5)
                {
                    System.out.println("MD5 hash received!!!"+Arrays.toString(received));
                    System.arraycopy(received, 1, md5Hash, 0, md5Hash.length);
                }


                else//file received:
                {
                    System.out.println("CLIENT :::: received length:" +received.length);
                    System.out.println("File received!!!!");

                    MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                    byte[] rv = new byte[received.length-1];


                    System.arraycopy(received,1,rv,0,rv.length);
                    byte[] newMd5Hash = messageDigest.digest(rv);


                    System.out.println("fileSize"+rv.length);
                    System.out.println("Recevied Hash:"+ Arrays.toString(newMd5Hash));
                    System.out.println("Calculated hash:"+ Arrays.toString(md5Hash));
                    String downloadedFileName = args[0]+"_Copy";
                    if (Arrays.equals(newMd5Hash, md5Hash))
                    {

                    	if(args[0].contains("."))
                    	{
                    		int index = args[0].lastIndexOf("."); 
                    		String fileName = args[0].substring(0,index);
                    		downloadedFileName = fileName+"_Copy";
                    		String extensionName = args[0].substring(index,args[0].length());
                    		downloadedFileName = downloadedFileName +  extensionName;                 		
                    	}
                    	
                        //File file = new File(args[0]+"22");
                        File file = new File(downloadedFileName);
                        file.createNewFile();

                        FileOutputStream fos = new FileOutputStream(file);
                        BufferedOutputStream bos = new BufferedOutputStream(fos);
                        bos.write(rv);
                        bos.close();
                        fos.close();
                        System.out.println("MD5 Success!");
                        System.out.println("File Transfer Complete!");
                        ttpCon.closeConnection();
                        break;
                    }
                    else
                    {
                        System.out.println("MD5 failure");
                        ttpCon.closeConnection();
                        break;
                    }
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

        }

    }

}
