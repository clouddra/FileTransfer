// Name: Chong Yun Long         Matriculation No: A0072292H

import java.io.IOException;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class ClientThread implements Runnable {

    private byte[] fileBuf;
    private int end;
    private int length;
    private int threadId;
    private int seqNo;
    private DatagramSocket dataSocket;
    InetAddress addr;
    int port;
    public ClientGUI clientView;
    public boolean finished = false;

    ClientThread(byte[] f, int offset, int len, int id, ClientGUI view, InetAddress add, int p) {
        fileBuf = f;
        end = offset + len;
        length = len;
        threadId = id;
        seqNo = offset;
        clientView = view;
        addr = add;
        port = p;

        try {
            dataSocket = new DatagramSocket();
            dataSocket.setSoTimeout(500);
        } catch (SocketException ex) {
            System.out.println("Socket exception");
            System.exit(1);
        }
        Thread t = new Thread(this);
        t.start();
    }

    @Override
    public void run() {
        byte[] sendBuf;
        byte[] inBuf = new byte[1024];
        DatagramPacket outPkt;
        DatagramPacket inPkt;

        while (seqNo < end) {   // while not end of file chunk
            // split into smaller packets
            byte[] temp;
            // if last packet, send till the end of file
            if (end - seqNo < UDPClient.MAX_PKT_SIZE) {
                temp = new byte[end - seqNo];
            } else {
                temp = new byte[UDPClient.MAX_PKT_SIZE];
            }
            
            // make packet and send
            System.arraycopy(fileBuf, seqNo, temp, 0, temp.length);
            sendBuf = UDPClient.makeByte(UDPClient.intToByteArray(seqNo), temp);
            outPkt = new DatagramPacket(sendBuf, sendBuf.length, addr, port);
            System.out.println("Sending Packet " + seqNo + " from " + dataSocket.getLocalSocketAddress().toString());
            try {
                dataSocket.send(outPkt);
            } catch (IOException ex) {
                System.out.println("I/O exception.");
            }
            
            while (true) {  // ignore all repeat ACKs until we receive correct ACK or timeout
                try {
                    inPkt = new DatagramPacket(inBuf, inBuf.length);
                    dataSocket.receive(inPkt);
                    if (UDPClient.byteArrayToInt(inPkt.getData()) < seqNo) {    // ignore repeated ACK
                        continue;
                    } else {
                        seqNo = UDPClient.byteArrayToInt(inPkt.getData());  // correct ACK received
                        break;
                    }
                } catch (SocketTimeoutException e) {    // packet loss. Resend
                    System.out.println("Timeout for " + seqNo + " packet");
                    break;
                } catch (IOException ex) {
                    System.out.println("I/O exception.");
                }

            }

        }
        clientView.writeOutput("Thread " + threadId + " finished transfer!\n");
        finished = true;
        dataSocket.close();

    }
}
