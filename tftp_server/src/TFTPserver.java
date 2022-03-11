import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
  public static final String WRITEDIR = Paths.get("./write").toString() + "/";
  // OP codes 
  public static final int OP_RRQ = 1;
  public static final int OP_WRQ = 2;
  public static final int OP_DAT = 3;
  public static final int OP_ACK = 4;
  public static final int OP_ERR = 5;

  public static boolean statusFalse;
  
  private List<byte[]> allData = new ArrayList<>();

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
  * Parse a given data package and return the 
  * the data in a byte[]. Returns null if 
  * something went wrong.
   */
  private List<byte[]> parseDataPackage(byte[] data) {
    List<byte[]> parsed = new ArrayList<>();
    ByteBuffer buf = ByteBuffer.wrap(data);
    short opcode = buf.getShort();
    short block = buf.getShort();

    // Check if correct datapackage.
    if (opcode == OP_DAT && block >= 1) {
      System.out.println("\nOpcode: " + opcode
                    +  "\nBlock number: " + block);

      parsed.add(Arrays.copyOfRange(data, 0, 4));

      // Parse for the data
      for (int i = 4; i < data.length; i++) {
        if (data[i] == 0) {
          parsed.add(Arrays.copyOfRange(data, 4, i));
          return parsed;
        }
      }
      parsed.add(Arrays.copyOfRange(data, 4, data.length));
      return parsed;
    }
    // Return null if something went wrong.
    System.out.println("Not a correct datapackage.");
    return null;
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
      try {
        byte[] allData = readBytesFromFile(requestedFile);
        if (allData == null) {
          System.out.println("File empty.");
        } else {
          List<byte[]> splitData = new ArrayList<>();

          if (allData.length > 512) {
            int index = 0;
            for (int i = 0; i < allData.length; i++) {
              if (i % 512 == 0 && i != 0) {
                splitData.add(Arrays.copyOfRange(allData, (i - 512), (i)));
                index = i;
              } 
            }
            splitData.add(Arrays.copyOfRange(allData, index, allData.length));
          } else {
            splitData.add(0, allData);
          }
          

          boolean result = true;
          
          for (int i = 0; i < splitData.size(); i++) {
            // if statusFalse is true then the loop breaks and we end the transmission
            if (statusFalse) {
              break;
            }
            result = send_Data_receive_Ack(sendSocket, 
            createDataResponsePacket(splitData.get(i), OP_DAT, i + 1));

            // If results return false we want to try the same index again
            i = (result) ? i : i - 1;
          }
        }
      } catch (FileNotFoundException e) {
        System.out.println("File not found.");
        send_Err(sendSocket, 1, "File not found"); 
      } catch (IOException e) {
        System.out.println("Something went wrong.");
        send_Err(sendSocket, 0, e.toString());
      }

      
    } else if (opcode == OP_WRQ) {

      File w = new File(requestedFile);
      if (w.exists()) {
        System.out.println("File already exists");
        send_Err(sendSocket, 6, "File already exists");
      } else {
        // Send the write req ack.
        byte[] ack = createAckResponsePacket(OP_ACK, 0);
        try {
          sendSocket.send(new DatagramPacket(ack, ack.length));
        } catch (IOException e) {
          System.out.println("Failed to send packet! Idiot...");
          send_Err(sendSocket, 0, e.toString());
        }

        // Recieve datapackets. 
        boolean result = true;
        
        while (result) {
          result = receive_Data_send_Ack(sendSocket, allData);
          if (allData.get(allData.size() - 1).length < 512) {
            System.out.println("All data packages recieved!:)");
            break;
          }
        }
        
        try {
          FileOutputStream fo = new FileOutputStream(w);
          for (byte[] data : allData) {
            fo.write(data);
          }
          fo.close();
        } catch (FileNotFoundException e) {
          System.out.println("File not found.");
          send_Err(sendSocket, 1, "File not found");
        } catch (IOException e) {
          System.out.println("No bueno.");
          send_Err(sendSocket, 0, e.toString());
        }
        
        
        allData.clear();
      }
    } else {
      System.err.println("Invalid request. Sending an error packet.");
      send_Err(sendSocket, 2, "Access violation");
      return;
    }
  }

  /**
  * Reads and returns a bytearray with 
  * all bytes from a specified file.
  */
  private byte[] readBytesFromFile(String fileName) throws FileNotFoundException, IOException {
    File file = new File(fileName);
    FileInputStream fileInput = new FileInputStream(fileName);
    byte[] content = new byte[(int)file.length()];

    fileInput.read(content);
    fileInput.close();

    return content;
  }

  /**
   * Create a data response packet and 
   * return as byte[].
  */
  private byte[] createDataResponsePacket(byte[] data, int opcode, int block) {
    if (data.length <= 512 && opcode == OP_DAT) {
      ByteBuffer response = ByteBuffer.allocate(data.length + 4);
      response.putShort((byte)opcode)
              .putShort((byte)block)
              .put(data, 0, data.length);
      return response.array();
    }
    System.out.println("Data packet could not be created.");
    return null;
  }

  /**
  * Create an ack-response package and 
  * return this as byte[].
  */
  private byte[] createAckResponsePacket(int opcode, int block) {
    if (opcode == OP_ACK && block >= 0) {
      ByteBuffer ack = ByteBuffer.allocate(4);
      ack.putShort((byte)OP_ACK);
      ack.putShort((byte)block);
      return ack.array();
    }
    System.out.println("Ack-packet could not be created.");
    return null;
  }
  
  /**
  * Creates and sends a datapacket to the given socket. Recieves any ack.
  */
  private boolean send_Data_receive_Ack(DatagramSocket sendSocket, byte[] dataPackage) {
    try {
      
      sendSocket.setSoTimeout(2000);
  
      short opcode = 0;
      short block = 0;

      // Recieve data, try up to 5 times.
      for (int i = 0; i < 5; i++) {
        try {
          DatagramPacket dp = new DatagramPacket(dataPackage, dataPackage.length);
          sendSocket.send(dp); 
          byte[] ack = new byte[4];
          sendSocket.receive(new DatagramPacket(ack, ack.length));
          System.out.println("Data recieved!");
          ByteBuffer a = ByteBuffer.wrap(ack);

          opcode = a.getShort();
          block = a.getShort();

          // Check if the block codes correspond.
          boolean s = (block == ByteBuffer.wrap(dataPackage).getShort(2));
          if (!s) {
            System.out.println("Wrong block number!");
            return false;

          } else if (s) {
            System.out.println("\nOpcode: " + opcode
                    +  "\nBlock number: " + block);
            return true;
          }
        } catch (SocketTimeoutException s) {
          System.out.println("Socket timed out.");
          send_Err(sendSocket, 0, s.toString());
          return false;
        }
      }

      
    } catch (IOException e) {
      System.out.println("Could not send package!!!! Idjit...");
      send_Err(sendSocket, 0, e.toString());
    } 
    statusFalse = true;
    return false;
  }
  
  private boolean receive_Data_send_Ack(DatagramSocket recieveSocket, List<byte[]> data) {
    try {
      // Recieve data.
      byte[] recievedData = new byte[BUFSIZE];
      DatagramPacket dataPackage = new DatagramPacket(recievedData, recievedData.length);
      recieveSocket.receive(dataPackage);
      
      // Check if correct datapackage.
      List<byte[]> parsedData = parseDataPackage(recievedData);

      if (parsedData != null) {
        System.out.println("Data parsed.");

        int block = ByteBuffer.wrap(parsedData.get(0)).getInt();
        
        allData.add(parsedData.get(1));
        System.out.println("Data added.");

        // Send ack with block number.
        byte[] ack = createAckResponsePacket(OP_ACK, block);
        recieveSocket.send(new DatagramPacket(ack, ack.length));
        System.out.println("Ack sent!");
        return true;
      }
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Index out of bounds: " + e);
      send_Err(recieveSocket, 0, e.toString());
    } catch (IOException e) {
      System.out.println("IOException: " + e);
      send_Err(recieveSocket, 0, e.toString());
    }
    System.out.println("Something went wrong.");
    return false;
  }

  // Create and send error message.
  private void send_Err(DatagramSocket sendSocket, int errorCode, String errorMsg) {
    try {
      byte[] msg = errorMsg.getBytes();
      ByteBuffer response = ByteBuffer.allocate(msg.length + 5);
      response.putShort((byte)OP_ERR)
              .putShort((byte)errorCode)
              .put(msg)
              .put((byte)0);
      byte[] fin = response.array();
      sendSocket.send(new DatagramPacket(fin, fin.length)); 
    } catch (IOException e) {
      System.out.println("Error-message went wrong.");
    }
  }
}