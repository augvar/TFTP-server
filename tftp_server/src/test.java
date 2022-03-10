import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class test {
  
  /**
  * .
  */
  private static byte[] readBytesFromFile(String fileName) {
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
   * .
   */
  public static void main(String[] args) {
    byte[] allData = readBytesFromFile(Paths.get("./read").toString() + "/RFC1350.txt");
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
      System.out.println();
    }
  }

}