Group 30: A0072292	Chong Yun Long

Using the program:

Client: Client will have a gui allowing them to choose the file they want, destination port and address. Note that while sending file, user is not supposed to start a new file transfer until the current one has finished. (All threads have finished sending)

Server: When the program starts, user will be prompted to pick a directory to store the downloaded file. Server port is fixed at 9001 in the source code. 


Protocol: 

Client first send a packet of seqNo = -1 
Server recognises -1 and replies with ACK 0
Client receives ACK 0 and begins transfer.
Client spawns 5 threads to do the file send. Each thread is in charge of 1 file chunk.
Server acknowledges each packet by sending the corresponding ack to the right client thread (unique port number).
If timeout, client will resend the packet. 
If duplicate packet, server will discard but will still send ACK. Client will ignore duplicate ACKs.
Client will update seqNo if right ACK is received.
Server will update ackNo if right seqNo is received.
File transfer is finished if the packets received matches the size of file.