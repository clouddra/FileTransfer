// Name: Chong Yun Long         Matriculation No: A0072292H

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;

public class UDPServer {

    // convert int to byte array
    public static final byte[] intToByteArray(int value) {
        return new byte[]{
                    (byte) (value >>> 24),
                    (byte) (value >>> 16),
                    (byte) (value >>> 8),
                    (byte) value};
    }

    // convert byte to int array
    public static final int byteArrayToInt(byte[] b) {
        return (b[0] << 24)
                + ((b[1] & 0xFF) << 16)
                + ((b[2] & 0xFF) << 8)
                + (b[3] & 0xFF);
    }

    public static void main(String args[]) {
        //use DatagramSocket for UDP connection
        DatagramSocket dataSocket = null;
        File fileDir = null;
        int size = 0; // size of file receieved
        
        // contains the ACK number of different client threads. Each thread uses a different socket address (different port)
        Hashtable<SocketAddress, Integer> threadAcks = new Hashtable<SocketAddress, Integer>();
        DatagramPacket inPkt;
        DatagramPacket outPkt;
        byte[] no = new byte[4]; // to extract integers
        byte[] fileBuf ; // for storing file data

        try {
            dataSocket = new DatagramSocket(9001);
        } catch (SocketException ex) {
            System.out.println("Socket exception. Please try a different port number.");
            System.exit(1);
        }


        String filename = new String();
        int ackNo = -1;
        byte[] inBuf = new byte[65535];
        try {
            InetAddress addr = InetAddress.getByName("localhost");
        } catch (UnknownHostException ex) {
            System.out.println("Unknown Host. Please enter a valid hostname.");
            System.exit(1);
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        do {
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            fileDir = chooser.getSelectedFile();
        } else {
            fileDir = chooser.getCurrentDirectory();    // default directory
        }
         System.out.println("Directory chosen: " + fileDir);
        } while (!fileDir.isDirectory()) ;




        while (true) {
            do {
                ackNo = -1;
                inPkt = new DatagramPacket(inBuf, inBuf.length);
                try {
                    dataSocket.receive(inPkt);
                } catch (IOException ex) {
                    System.out.println("I/O exception.");
                    continue;
                }
                
                System.arraycopy(inPkt.getData(), 0, no, 0, 4);    // receive request to send packet from client

                if (byteArrayToInt(no) == -1) { // if first packet starts with -1 (connection establishment)
                    System.arraycopy(inPkt.getData(), 4, no, 0, 4);
                    size = byteArrayToInt(no);  // size is from byte 4 onwards
                    filename = new String(inPkt.getData(), 8, inPkt.getLength() - 8);
                    System.out.println(filename);

                    // ackNo 0 tells the client to begin sending
                    ackNo = 0;
                    outPkt = new DatagramPacket(intToByteArray(ackNo), 4, inPkt.getAddress(), inPkt.getPort());
                    System.out.println("Sending ACK " + ackNo + ". Starting file transfer.");

                    try {
                        dataSocket.send(outPkt);
                    } catch (IOException ex) {
                        System.out.println("I/O exception");
                    }

                } else {    // if not just ACK back and do nothing. 
                    // required in case the ACK of the last packet of the previous file sent is lost.
                    outPkt = new DatagramPacket(intToByteArray(byteArrayToInt(no) + inPkt.getLength() - 4), 4, inPkt.getAddress(), inPkt.getPort());
                    System.out.println("Sending ACK " + (byteArrayToInt(no) + inPkt.getLength() - 4));
                    try {
                        dataSocket.send(outPkt);
                    } catch (IOException ex) {
                        System.out.println("I/O exception");
                    }
                }

            } while (ackNo != 0);
            
            
            File f = new File(fileDir.toString() + "\\" + filename);
            System.out.print(f.getPath()) ;
            if (f.exists()) {
                System.out.println("Deleting previous file ...");
                f.delete();
            }
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(f.getPath(), true);
            } catch (FileNotFoundException ex) {
                System.out.println("File not found!");
                System.exit(1);
            }
            fileBuf = new byte[size];

            int received = 0;
            while (received < size) {   // do until we received all packets
                
                // receive packet
                inPkt = new DatagramPacket(inBuf, inBuf.length);
                try {
                    dataSocket.receive(inPkt);
                } catch (IOException ex) {
                    System.out.println("I/O exception");
                    continue;
                }
                System.arraycopy(inPkt.getData(), 0, no, 0, 4);

                // process packet. if first time receiving (not contained in hash table) or if seqNo = expected seqNo.
                if (!threadAcks.containsKey(inPkt.getSocketAddress()) || threadAcks.get(inPkt.getSocketAddress()) == byteArrayToInt(no)) {  // write to file and update ack no if not out of order  

                    if (byteArrayToInt(no) == -1) {     // just in case the ACK for initialisation packet is lost
                        outPkt = new DatagramPacket(intToByteArray(0), 4, inPkt.getAddress(), inPkt.getPort());
                        System.out.println("Sending ACK 0");
                        try {
                            dataSocket.send(outPkt);
                        } catch (IOException ex) {
                            System.out.println("I/O exception");
                        }
                        continue;   // receive again
                    }
                    
                    // store data in buf and update ACK no.
                    threadAcks.put(inPkt.getSocketAddress(), byteArrayToInt(no));
                    System.arraycopy(inPkt.getData(), 4, fileBuf, threadAcks.get(inPkt.getSocketAddress()), inPkt.getLength() - 4);
                    threadAcks.put(inPkt.getSocketAddress(), byteArrayToInt(no) + inPkt.getLength() - 4);
                    received += inPkt.getLength() - 4;

                }
                // send ACK for all data packets regardless of duplicated or not
                outPkt = new DatagramPacket(intToByteArray(threadAcks.get(inPkt.getSocketAddress())), 4, inPkt.getAddress(), inPkt.getPort());
                System.out.println("Sending ACK " + threadAcks.get(inPkt.getSocketAddress()) + " to " + inPkt.getSocketAddress().toString());
                try {
                    dataSocket.send(outPkt);
                } catch (IOException ex) {
                    System.out.println("I/O exception");
                }
            }
            
            // write to file and we are done
            try {
                fos.write(fileBuf);
                fos.close();
            } catch (IOException ex) {
                System.out.println("File I/O exception");
                System.exit(1);
            }

            System.out.println("Finished Transfer!");
        }

    }
}
