import java.nio.ByteBuffer;
import java.util.Arrays;

public class test {
  public static void main(String[] args) {
    ByteBuffer bb = ByteBuffer.allocate(4);

    bb.put((byte)0).put((byte)3).put((byte)80);

    System.out.println(Arrays.toString(bb.array()));
  }
}