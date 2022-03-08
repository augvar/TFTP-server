import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * .
 */
public class TFTPserver {
  public static final int TFTPPORT = 8888;
  public static final int BUFSIZE = 516;
  public static final String READDIR = Paths.get("./read").toString() + "/"; 
  public static final String WRITEDIR = "./TFTP-server/write/"; //custom address at your PC
  // OP codes 
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  /**
   * .
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      System.err.printf("usage: java %s\n", TFTPserver.class.getCanonicalName());
      System.exit(1);
    } 

    //Starting the server
    try {
      TFTPserver server = new TFTPserver();
      server.start();
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }
  
  private void start() throws SocketException {
    byte[] buf = new byte[BUFSIZE];

    // Create socket
    DatagramSocket socket = new DatagramSocket(null);
    
    // Create local bind point 
    SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
    socket.bind(localBindPoint);

    System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

    // Loop to handle client requests 
    while (true) {        
      
      final InetSocketAddress clientAddress = receiveFrom(socket, buf);
      
      // If clientAddress is null, an error occurred in receiveFrom()
      if (clientAddress == null) {
        continue;
      }
      final StringBuffer requestedFile = new StringBuffer();
      final int reqtype = parseRQ(buf, requestedFile);

      new Thread() {
        public void run() {
          try {
            DatagramSocket sendSocket = new DatagramSocket(0);

            // Connect to client
            sendSocket.connect(clientAddress);

            System.out.printf("%s request for %s from %s using port %d\n",
                (reqtype == OP_RRQ) ? "Read" : "Write",
                clientAddress.getHostName(), clientAddress.getAddress(), clientAddress.getPort());  
                
            // Read request
            if (reqtype == OP_RRQ) {      
              requestedFile.insert(0, READDIR);
              handleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
            // Write request
            } else {  
                                 
              requestedFile.insert(0, WRITEDIR);
              handleRQ(sendSocket,requestedFile.toString(),OP_WRQ);  
            }
            sendSocket.close();
          } catch (SocketException e) {
            e.printStackTrace();
          }
        }
      }.start();
    }
  }
  
  /**
   * Reads the first block of data, i.e., the request for an action (read or write).
   * @param socket (socket to read from)
   * @param buf (where to store the read data)
   * @return socketAddress (the socket address of the client)
   */
  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    // Create datagram packet
    DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
    
    // Receive packet
    try {
      socket.receive(datagramPacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    // Get client address and port from the packet
    return new InetSocketAddress(datagramPacket.getAddress(), datagramPacket.getPort());
  }

  /**
   * Parses the request in buf to retrieve the type of request and requestedFile.
   * 
   * @param buf (received request)
   * @param requestedFile (name of file to read/write)
   * @return opcode (request type: RRQ or WRQ)
   */

  private int parseRQ(byte[] buf, StringBuffer requestedFile) {
    // See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
    ByteBuffer wrap = ByteBuffer.wrap(buf);
    short opcode = wrap.getShort();
    
    String file = "";
    String mode = "";
    int index = 0;
    for (int i = 2; i < buf.length; i++) {
      if (buf[i] == 0) {
        file = new String(buf, 2, i - 2);
        index = i;
        requestedFile.append(file);
        System.out.println("Filename: " + file);
        break;
      }
    }

    for (int i = index + 1; i < buf.length; i++) {
      if (buf[i] == 0) {
        mode = new String(buf, index + 1, i - (index + 1));
        System.out.println("Mode: " + mode);
        return opcode;
      }
    }
    
    return 0;
  }

  /**
   * Handles RRQ and WRQ requests.
   * 
   * @param sendSocket (socket used to send/receive packets)
   * @param requestedFile (name of file to read/write)
   * @param opcode (RRQ or WRQ)
   */
  private void handleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) {
    if (opcode == OP_RRQ) {    
      byte[] allData = readBytesFromFile(requestedFile);
      if (allData == null) {
        System.out.println("File empty.");
        //TODO: error packet?
      } else {
        List<byte[]> splitData = new ArrayList<>();

        if (allData.length > 512) {
          int index = 0;
          for (int i = 0; i < allData.length; i++) {
            if (i % 512 == 0) {
              splitData.add(Arrays.copyOfRange(allData, index, i));
              index += 512;
            }
          }
        } else {
          splitData.add(0, allData);
        }
        
        System.out.println("File content: \n");

        boolean result = true;
        for (int i = 0; i < splitData.size(); i++) {
          System.out.println(new String(splitData.get(i)));
          byte[] p = createDataResponsePacket(splitData.get(i), OP_DAT, i + 1);
          if (result) {
            result = send_Data_receive_Ack(sendSocket, p);
          } else {
            System.out.println("Oh no its stupid.");
          }
        }
        // byte[] p = createDataResponsePacket(allData, OP_DAT, 1);
        // send_Data_receive_Ack(sendSocket, p);
      }
      
    } else if (opcode == OP_WRQ) {
      boolean result = receive_Data_send_Ack();
    
    } else {
      System.err.println("Invalid request. Sending an error packet.");
      // See "TFTP Formats" in TFTP specification for the ERROR packet contents
      send_Err();
      return;
    }
  }

  /**
  * Reads and returns a bytearray with 
  * all bytes from a specified file.
  */
  private byte[] readBytesFromFile(String fileName) {
    try {
      File file = new File(fileName);
      FileInputStream fileInput = new FileInputStream(fileName);
      byte[] content = new byte[(int)file.length()];

      fileInput.read(content);
      fileInput.close();

      return content;

    } catch (FileNotFoundException e) {
      System.out.println("File not found.");
    } catch (IOException e) {
      System.out.println("Something went wrong.");
    }
    return null;
  }

  /**
   * Create a data response packet and 
   * return as byte[].
  */
  private byte[] createDataResponsePacket(byte[] data, int opcode, int block) {
    if (data.length < 512 && opcode == OP_DAT) {
      ByteBuffer response = ByteBuffer.allocate(data.length + 4);
      response.putShort((byte)opcode)
              .putShort((byte)block)
              .put(data, 0, data.length);
      return response.array();
    }
    System.out.println("Packet could not be created.");
    return null;
  }
  
  /**
  To be implemented.
  */
  private boolean send_Data_receive_Ack(DatagramSocket sendSocket, byte[] dataPackage) {
    try {
      DatagramPacket dp = new DatagramPacket(dataPackage, dataPackage.length);
      sendSocket.send(dp);

      // Recieve
      byte[] ack = new byte[BUFSIZE];
      sendSocket.receive(new DatagramPacket(ack, ack.length));
      ByteBuffer a = ByteBuffer.wrap(ack);
    
      System.out.println("\nOpcode: " + a.getShort()
                      +  "\nBlock number: " + a.getShort());
      
      return true;
      
    } catch (IOException e) {
      System.out.println("Could not send package!!!! Idjit...");
    }
    return false;
  }
  
  private boolean receive_Data_send_Ack() {
    return true;
  }
  
  private void send_Err() {

  }
}