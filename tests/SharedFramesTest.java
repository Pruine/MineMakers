import dev.minemakers.bridge.SharedOverlayWriter;
import java.nio.*;
import java.nio.file.Path;
import java.util.Arrays;

/** Concurrent cross-process producer for the Python mapping reader test. */
public final class SharedFramesTest {
    public static void main(String[] args) throws Exception {
        byte[] image=new byte[100+1280*720*4];
        for (int generation=0;generation<2;generation++) try (var writer=new SharedOverlayWriter(Path.of(args[0]))) {
            for (int i=generation==0?1:121;i<=(generation==0?120:121);i++) {
                Arrays.fill(image,100,image.length,(byte)i);
                Arrays.fill(image,0,100,(byte)0);
                var b=ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
                b.put(new byte[]{'M','M','O','V'}).putInt(1).putInt(1280).putInt(720).putLong(i).putLong(0).putInt(0).put("shared_test".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                writer.publish(image);
                try (var file=java.nio.channels.FileChannel.open(Path.of(args[0]))) {
                    var header=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN); file.read(header);
                    if ((header.getLong(16)>>>2)!=i) throw new AssertionError("Mapping epoch must survive producer restart");
                }
                Thread.sleep(3);
            }
        }
    }
}
