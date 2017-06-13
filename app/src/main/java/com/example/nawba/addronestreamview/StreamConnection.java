package com.example.nawba.addronestreamview;

import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by ebarnaw on 2017-06-13.
 */

public class StreamConnection extends Thread {

    private final static int MAX_BUFFER = 2500000;

    private String address;
    private int port;

    public interface OnNewFrameListener {
        void setNewFrame(byte[] array, int length);
        void onConnected();
        void onError(String message);
    }

    private OnNewFrameListener listener;
    private boolean connected = false;

    public StreamConnection(String address, int port, OnNewFrameListener onNewFrameListener) {
        this.address = address;
        this.port = port;
        listener = onNewFrameListener;
    }

    @Override
    public void run() {

        Socket clientSocket = new Socket();
        try {
            clientSocket.connect(new InetSocketAddress(address, port), 5000);
            Log.i(StreamConnection.class.getSimpleName(), "connected, address: " + address);
            connected = true;
            DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
            listener.onConnected();

            byte buffer[] = new byte[4];
            byte imageBuffer[] = new byte[MAX_BUFFER];

            while(connected) {
                int dataSize = dataInputStream.read(buffer, 0, buffer.length);

                if (dataSize != -1) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, 4);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

                    int imageLength = byteBuffer.getInt();

                    if(imageLength > MAX_BUFFER) {
                        dataInputStream.skipBytes(imageLength);
                    } else {

                        int readed = 0;
                        while (readed < imageLength) {
                            readed += dataInputStream.read(imageBuffer, readed, imageLength - readed);
                        }

                        listener.setNewFrame(imageBuffer, imageLength);
                    }

                    for (int i = 0; i < 4; i++) {
                        buffer[i] = 0;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            listener.onError(e.getMessage());
        }

        try {
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String byteToHexString(char b) {
        String ret = "";
        int intVal = b & 0xff;
        if (intVal < 0x10) ret += "0";
        ret += Integer.toHexString(intVal);
        return ret;
    }

    public void disconnect() {
        connected = false;
    }
}
