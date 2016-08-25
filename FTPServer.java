

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;


/**
 * Main class for servicing data to the clients.
 */

public class FTPServer
{
    // FTPServer
    public static final int chunkLimit = 2400;

    public static final byte MD5 = 1;
    public static final byte data = 2;


    public static void main(String[] args)
    {


        //  1. Get command line arguments
        if(args.length!=4)
        {
            System.out.println("Usage: java FTPServer <ServerIP> <ServerPort> <WindowSize> <Interval> ");
            System.exit(-1);
        }

        //2. Create TTP Service
        /**
         *  This will create a datagram Service.
         */
        TTPService ttpService = new TTPService(Integer.parseInt(args[1]));
        ttpService.setWindowSize(Integer.parseInt(args[2]));
        ttpService.setRtTimerInterval(Double.parseDouble(args[3]));


        System.out.println("TTPService created on port "+ args[1]);
        System.out.println("Listening...");


        while(true)
        {
            //Look for connections
            String request = ttpService.receiver();
            if(request != null)
            {

                System.out.println("Received request from client"+request);
                Thread newClientRequest = new Thread(new FileRequestHandler(request,ttpService));
                newClientRequest.start();
            }

        }


    }

    /**
     * Handles sending the file back to the client.
     */
    public static class FileRequestHandler implements Runnable
    {

        private TTPService ttpService;
        private String request;

        public FileRequestHandler(String request, TTPService ttpService)
        {

            this.request = request;
            this.ttpService = ttpService;
        }

        @Override
        public void run()
        {



            String splits[] = request.split(":");
            String clientIP = splits[0];
            String clientPort = splits[1];
            String filename = splits[2];

            System.out.println(filename);


            File file = new File(filename);
            if(!file.exists())
            {
                System.out.println("file doesn't exist?");

            }
            else
            {
                /**
                 * Assumption, client has enough space to store the file.
                 * The file size is enough to be stored in the RAM.
                 */

                //file exists. - send md5 data first.
                FileInputStream fis= null;
                try
                {
                    fis = new FileInputStream(file);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }

                byte[] fileContent = new byte[(int) file.length()];


                try
                {
                    fis.read(fileContent);
                } catch (IOException e)
                {
                    System.err.println("Something wrong with reading the file.");
                }


                //Need to get the hash.
                MessageDigest messageDigest = null;
                try
                {
                     messageDigest = MessageDigest.getInstance("MD5");
                }
                catch(NoSuchAlgorithmException e)
                {
                    e.printStackTrace();
                }
                byte[] hash = messageDigest.digest(fileContent);

                byte[] toSendHash =  new byte[hash.length+1];
                toSendHash[0]= FTPServer.MD5;
                System.arraycopy(hash,0,toSendHash,1,hash.length);
               // System.out.println(Arrays.toString(toSendHash));

                //need to see if this needs to be changed to byte array.


                ttpService.sender(clientIP,clientPort,toSendHash,TTPService.EOF);

                System.out.println("MD5 sent!");
                try
                {
                    Thread.sleep(1000);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }

                byte[] toSendFile = new byte[fileContent.length+1];
                toSendFile[0]= FTPServer.data;

                System.arraycopy(fileContent,0,toSendFile,1,fileContent.length);

                System.out.println("fs:"+fileContent.length);

                if(fileContent.length > TTPConnection.MTU_PAYLOAD)
                {
                    ttpService.sender(clientIP, clientPort, toSendFile, TTPService.DATA);
                }
                else
                {
                    ttpService.sender(clientIP, clientPort, toSendFile, TTPService.EOF);
                }


            }
        }

    }





}
