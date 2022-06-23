package com.denzygames.remotecommand;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class UDPArchitecture extends Thread {
    enum RunningMode {
        Host, Client,None
    };

    String log_tag = "UDPArchitecture";
    protected InetAddress IPAddress;
    protected int port;
    DatagramSocket socket = null;
    public RunningMode runningMode = RunningMode.None;
    public boolean running;

    int receive_buffer_size = 4096;
    byte[] receive_buffer;

    List<IReceive> subscribers_receive = new ArrayList<IReceive>();

    public UDPArchitecture(InetAddress inetAddress, int net_port) throws SocketException {
        IPAddress = inetAddress;
        port = net_port;

        init(RunningMode.Client);
    }

    public UDPArchitecture(int net_port) throws SocketException, UnknownHostException {
        IPAddress = InetAddress.getByName("0.0.0.0");
        port = net_port;

        init(RunningMode.Host);
    }

    void init(RunningMode mode) throws SocketException {
        try {
            socket = new DatagramSocket(port);
        }catch (Exception ex)
        {
            Log.e(log_tag,"Socks failed!");
            ex.printStackTrace();
        }
        if(socket != null)
        {
            running = true;
            runningMode = mode;
        }

    }

    public void send(byte[] data)
    {
        DatagramPacket packet = new DatagramPacket(data, data.length, IPAddress, port);
        try {
            socket.send(packet);
            Log.i(log_tag, "Sending " + data.length + " bytes to "+ packet.getSocketAddress().toString());
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(log_tag, "Send failed!");
        }
    }

    public void run() {
        while (running) {
            receive_buffer = new byte[receive_buffer_size];

            DatagramPacket packet = new DatagramPacket(receive_buffer, receive_buffer_size);

            Log.i(log_tag, "Waiting to receive message...");

            try {
                socket.receive(packet);
            } catch (IOException e) {
                Log.e(log_tag,"Failed to receive packet");
                return;
                //e.printStackTrace();
            }

            InetAddress packetAddress = packet.getAddress();
            int packetPort = packet.getPort();

            //Change to address we received data from
            IPAddress = packetAddress;
            Log.i(log_tag, "Received " + packet.getLength()+ " bytes. IP: " + packet.getAddress() + " Port:" + packet.getPort());

            for (int i = 0; i < subscribers_receive.size(); i++) {
                sendSubscriptionsReceive(packet.getData(), packet.getSocketAddress(),i);
            }
        }

        Log.i(log_tag,"Receive thread hath finithed");
    }

    void sendSubscriptionsReceive(byte[] data, SocketAddress socketAddress, int subscriber)
    {
        subscribers_receive.get(subscriber).receive(data, socketAddress);
    }

    public void addSubscriptionReceive(IReceive receive)
    {
        subscribers_receive.add(receive);
    }
}

