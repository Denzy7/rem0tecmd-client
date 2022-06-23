package com.denzygames.remotecommand;

import java.net.SocketAddress;

public interface IReceive
{
    public void receive(byte[] data, SocketAddress socketAddress);
}
