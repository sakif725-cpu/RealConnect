package com.realconnect.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class WavUtils {

    public static byte[] pcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        if (pcmData == null) return new byte[0];

        int totalDataLen = pcmData.length + 36;
        int byteRate = sampleRate * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // RIFF header
            out.write(new byte[]{'R', 'I', 'F', 'F'});
            out.write(intToByteArray(totalDataLen));
            out.write(new byte[]{'W', 'A', 'V', 'E'});

            // fmt subchunk
            out.write(new byte[]{'f', 'm', 't', ' '});
            out.write(intToByteArray(16)); // SubChunk1Size (16 for PCM)
            out.write(shortToByteArray((short) 1)); // AudioFormat (1 for PCM)
            out.write(shortToByteArray((short) channels));
            out.write(intToByteArray(sampleRate));
            out.write(intToByteArray(byteRate));
            out.write(shortToByteArray((short) blockAlign));
            out.write(shortToByteArray((short) bitsPerSample));

            // data subchunk
            out.write(new byte[]{'d', 'a', 't', 'a'});
            out.write(intToByteArray(pcmData.length));
            out.write(pcmData);

            return out.toByteArray();
        } catch (IOException e) {
            return pcmData;
        }
    }

    private static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff),
                (byte) ((value >> 16) & 0xff),
                (byte) ((value >> 24) & 0xff)
        };
    }

    private static byte[] shortToByteArray(short value) {
        return new byte[]{
                (byte) (value & 0xff),
                (byte) ((value >> 8) & 0xff)
        };
    }
}