package com.shieldstream.tv.media.mkv;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.TreeMap;

@UnstableApi
public final class MkvProbe {

  public static void main(String[] args) throws Exception {
    for (String path : args) {
      System.out.println("== " + new File(path).getName());
      run("Media3", new MatroskaExtractor(new DefaultSubtitleParserFactory(), 0), path);
      run("Bambi ", new LenientMatroskaExtractor(new DefaultSubtitleParserFactory(), 0), path);
    }
  }

  private static void run(String label, Extractor extractor, String path) {
    Output output = new Output();
    try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
      long length = file.length();
      if (!extractor.sniff(new DefaultExtractorInput(reader(file, 0), 0, length))) {
        System.out.println(label + ": no lo reconoce como MKV");
        return;
      }
      extractor.init(output);
      PositionHolder seek = new PositionHolder();
      ExtractorInput input = new DefaultExtractorInput(reader(file, 0), 0, length);
      int result;
      while ((result = extractor.read(input, seek)) != Extractor.RESULT_END_OF_INPUT) {
        if (result == Extractor.RESULT_SEEK) {
          input = new DefaultExtractorInput(reader(file, seek.position), seek.position, length);
        }
      }
      System.out.println(label + ": OK  " + output);
    } catch (Exception e) {
      System.out.println(label + ": FALLA  " + e.getClass().getSimpleName() + ": " + e.getMessage()
          + "  (pistas hasta ahí: " + output + ")");
    }
  }

  private static DataReader reader(RandomAccessFile file, long position) throws IOException {
    file.seek(position);
    return (buffer, offset, len) -> {
      int n = file.read(buffer, offset, len);
      return n < 0 ? C.RESULT_END_OF_INPUT : n;
    };
  }

  private static final class Output implements ExtractorOutput {
    private final Map<Integer, Track> tracks = new TreeMap<>();

    @Override
    public TrackOutput track(int id, int type) {
      Track t = tracks.get(id);
      if (t == null) {
        t = new Track();
        tracks.put(id, t);
      }
      return t;
    }

    @Override
    public void endTracks() {}

    @Override
    public void seekMap(SeekMap seekMap) {}

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<Integer, Track> e : tracks.entrySet()) {
        Format f = e.getValue().format;
        sb.append("[pista ").append(e.getKey()).append(' ')
            .append(f == null ? "?" : f.sampleMimeType).append(": ")
            .append(e.getValue().samples).append(" muestras] ");
      }
      return sb.length() == 0 ? "(ninguna)" : sb.toString();
    }
  }

  private static final class Track implements TrackOutput {
    @Nullable Format format;
    int samples;
    private final byte[] scratch = new byte[64 * 1024];

    @Override
    public void format(Format format) {
      this.format = format;
    }

    @Override
    public int sampleData(DataReader input, int length, boolean allowEndOfInput, int part)
        throws IOException {
      int n = input.read(scratch, 0, Math.min(length, scratch.length));
      if (n == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput) return C.RESULT_END_OF_INPUT;
        throw new EOFException();
      }
      return n;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length, int part) {
      data.skipBytes(length);
    }

    @Override
    public void sampleMetadata(
        long timeUs, int flags, int size, int offset, @Nullable CryptoData cryptoData) {
      samples++;
    }
  }
}
