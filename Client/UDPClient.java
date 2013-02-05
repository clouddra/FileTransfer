// Name: Chong Yun Long         Matriculation No: A0072292H

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class UDPClient {

    public static int MAX = 7048576;
    public static int MAX_PKT_SIZE = 60000; // packet size is set to 60000 bytes
    private File f;
    public ClientGUI clientView;
    private InetAddress addr;
    private int port = 9001;    // default port of 9001

    UDPClient(File temp, ClientGUI view, InetAddress add, int p) {
        f = temp;
        clientView = view;
        addr = add;
        port = p;
    }

    // convert byte array to int
    public static final int byteArrayToInt(byte[] b) {
        return (b[0] << 24)
                + ((b[1] & 0xFF) << 16)
                + ((b[2] & 0xFF) << 8)
                + (b[3] & 0xFF);
    }

    // convert int to byte array
    public static final byte[] intToByteArray(int value) {
        return new byte[]{
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value};
    }

    public static byte[] makeByte(byte[] no, byte[] data) {
        byte[] buf = new byte[(data.length) + 4]; //int is 4 bytes long in java  
        System.arraycopy(no, 0, buf, 0, 4);
        System.arraycopy(data, 0, buf, 4, data.length);   // position 4 onwards are data 
        return buf;
    }

    public void startTransfer() {

        DatagramSocket connect = null;
        DatagramPacket outPkt;

        byte[] sendBuf = new byte[MAX_PKT_SIZE]; // packet size to send out
        byte[] inBuf = new byte[1024];
        DatagramPacket inPkt = null;
        int seqNo = -1;
        int chunkSize;
        FileInputStream fis = null;
        byte[] fileBuf = null;

        try {
            connect = new DatagramSocket();
        } catch (SocketException ex) {
            // quit if cannot connect
            System.out.println("Socket exception");
            System.exit(1);
        }

        try {
            // timeout is set to 500ms
            connect.setSoTimeout(500);
        } catch (SocketException ex) {
            System.out.println("Socket exception");
            System.exit(1);
        }
        int size = (int) f.length();

        // Write request starts with a value of -1 to distinguish it from sequence numbers of data packets
        // It is as though the sequence number of the initialisation packet is -1           
        sendBuf = makeByte(intToByteArray(size), f.getName().getBytes());  // filesize, filename
        sendBuf = makeByte(intToByteArray(seqNo), sendBuf);   // -1, filesize, filename

        // write message to GUI
        clientView.writeOutput("Sending request for file uploading ...\n");
        outPkt = new DatagramPacket(sendBuf, sendBuf.length, addr, port);

        while (seqNo == -1) {

            // send initialisation and wait for server's confirmation
            System.out.println("Sending initialization packet");
            try {
                connect.send(outPkt);
            } catch (IOException ex) {
                System.out.println("I/O exception");
            }
            try {
                inPkt = new DatagramPacket(inBuf, inBuf.length);
                connect.receive(inPkt);
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout for initialization packet");
                continue;
            } catch (IOException e) {
                System.out.println("I/O exception");
            }

            // if right confirmation (ACK 0), we start file transfer
            if (byteArrayToInt(inPkt.getData()) == 0) {
                seqNo = 0;
            }

        }
        clientView.writeOutput("Received reply from server. Beginning file transfer ...\n");


        // open file and read into buffer
        try {
            fis = new FileInputStream(f.getPath());
            fileBuf = new byte[size];
            fis.read(fileBuf);
        } catch (FileNotFoundException ex) {
            System.out.println("File not found!");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("I/O exception");
            System.exit(1);
        }

        // divide the file into chunks for threading
        chunkSize = (int) Math.ceil(size / 5.0);

        ClientThread[] sending = new ClientThread[5];
        for (int i = 0; i < 5; i++) {
            int offset = chunkSize * i;
            int length;
            if (size - offset > chunkSize) {
                length = chunkSize;
            } else {
                length = size - offset;
            }
            clientView.writeOutput("Thread " + (i + 1) + " sending bytes from " + offset + " to " + (length - 1) + "\n");
            sending[i] = new ClientThread(fileBuf, offset, length, i + 1, clientView, addr, port);  // use 5 threads to send data over.

        }
        
        // main thread is done
        try {
            fis.close();
        } catch (IOException ex) {
            System.out.println("I/O exception");
            System.exit(1);
        }
        connect.close();

    }
}
