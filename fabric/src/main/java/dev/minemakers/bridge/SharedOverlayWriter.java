package dev.minemakers.bridge;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Three committed image slots. Readers never wait on per-frame file replacement or flush. */
public final class SharedOverlayWriter implements AutoCloseable {
    public static final int MAX_FRAME=100+1280*720*4;
    public static final int SLOTS=3,STRIDE=16+((MAX_FRAME+7)&~7),SIZE=64+SLOTS*STRIDE;
    private static final VarHandle LONG=MethodHandles.byteBufferViewVarHandle(long[].class,ByteOrder.LITTLE_ENDIAN);
    private final FileChannel channel;
    private final MappedByteBuffer memory;
    private long sequence;
    public SharedOverlayWriter(Path file) throws IOException {
        channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
        memory=channel.map(FileChannel.MapMode.READ_WRITE,0,SIZE);
        memory.order(ByteOrder.LITTLE_ENDIAN);
        // Preserve the epoch across producer restarts while a reader still maps this file.
        long previous=(long)LONG.getVolatile(memory,16);
        if (memory.getInt(0)==0x464d4d4d && memory.getInt(4)==1 && previous>0 && previous<Long.MAX_VALUE/8) sequence=previous>>>2;
        LONG.setVolatile(memory,16,0L);
        memory.put(0,(byte)'M').put(1,(byte)'M').put(2,(byte)'M').put(3,(byte)'F');
        memory.putInt(4,1).putInt(8,SLOTS).putInt(12,MAX_FRAME);
        for (int i=0;i<SLOTS;i++) LONG.setVolatile(memory,64+i*STRIDE,0L);
    }
    public void publish(byte[] frame) {
        if (frame.length<100 || frame.length>MAX_FRAME) throw new IllegalArgumentException("Invalid overlay image size");
        long serial=++sequence;
        int slot=(int)(serial%SLOTS),offset=64+slot*STRIDE;
        LONG.setVolatile(memory,offset,serial*2-1); // announce mutation before touching the slot
        memory.putInt(offset+8,frame.length);
        memory.position(offset+16); memory.put(frame);
        LONG.setRelease(memory,offset,serial*2);
        LONG.setRelease(memory,16,serial*4+slot);
        // No force(): local mappings share coherent pages; disk persistence is unnecessary.
    }
    @Override public void close() throws IOException { channel.close(); }
}
