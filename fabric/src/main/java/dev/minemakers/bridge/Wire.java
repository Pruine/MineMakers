package dev.minemakers.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded TSV snapshots, framed to reject partially written Trailmakers files. */
public final class Wire {
    public static final int MAX_BYTES = 4 * 1024 * 1024, MAX_RECORDS = 8192;
    public record Frame(String session, long sequence, List<String[]> records) {}
    private Wire() {}
    private static boolean token(String s) { return s.matches("[A-Za-z0-9_-]{1,64}"); }
    public static String encode(Frame frame) {
        if (!token(frame.session()) || frame.sequence()<0 || frame.records().size()>MAX_RECORDS)
            throw new IllegalArgumentException("Invalid frame header");
        StringBuilder body = new StringBuilder();
        for (String[] row : frame.records()) {
            if (row.length==0 || row.length>32) throw new IllegalArgumentException("Invalid row");
            for (String value : row) {
                if (value.indexOf('\t')>=0 || value.indexOf('\n')>=0 || value.indexOf('\r')>=0 || value.length()>256)
                    throw new IllegalArgumentException("Invalid field");
            }
            body.append(String.join("\t",row)).append('\n');
        }
        String result="MMT1\t"+frame.session()+"\t"+frame.sequence()+"\t"+frame.records().size()+"\n"+body+
            "END\t"+frame.session()+"\t"+frame.sequence()+"\t"+frame.records().size()+"\n";
        if (result.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new IllegalArgumentException("Frame too large");
        return result;
    }
    public static Frame decode(String text) {
        if (text.length()>MAX_BYTES || text.indexOf('\r')>=0 || !text.endsWith("\n"))
            throw new IllegalArgumentException("Invalid or truncated frame");
        String[] lines=text.split("\n",-1);
        String[] first=lines[0].split("\t",-1);
        if (first.length!=4 || !first[0].equals("MMT1") || !token(first[1])) throw new IllegalArgumentException("Bad header");
        long seq=Long.parseLong(first[2]); int count=Integer.parseInt(first[3]);
        if (seq<0 || count<0 || count>MAX_RECORDS || lines.length!=count+3) throw new IllegalArgumentException("Bad length");
        if (!lines[count+1].equals("END\t"+first[1]+"\t"+seq+"\t"+count)) throw new IllegalArgumentException("Torn frame");
        List<String[]> rows=new ArrayList<>();
        for (int i=1;i<=count;i++) {
            String[] row=lines[i].split("\t",-1);
            if (row.length>32) throw new IllegalArgumentException("Too many fields");
            for (String v:row) if (v.length()>256) throw new IllegalArgumentException("Field too long");
            rows.add(row);
        }
        return new Frame(first[1],seq,List.copyOf(rows));
    }
    public static Frame read(Path file) throws IOException {
        if (Files.size(file)>MAX_BYTES) throw new IOException("Oversized frame");
        return decode(Files.readString(file,StandardCharsets.UTF_8));
    }
    public static void write(Path file, Frame frame) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(temp,encode(frame),StandardCharsets.UTF_8);
        try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
    public static double number(String text) {
        double n=Double.parseDouble(text);
        if (!Double.isFinite(n) || Math.abs(n)>30_000_000) throw new IllegalArgumentException("Invalid coordinate");
        return n;
    }
}
