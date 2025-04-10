package io.github.cpsc559.team16.common.utilities;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class StaticIpDriver {
    public static void main(String[] args){
        StaticIP help = new StaticIP();
        try{
            Socket serverSocket = new Socket("www.notchloerobitaille.com", 443);
            System.out.println(serverSocket.getInetAddress().getHostAddress() );
            System.out.println(InetAddress.getLocalHost().getHostAddress() );

            System.out.print(help.setIP(InetAddress.getLocalHost()));


            System.out.println(help.getIP());

        }
        catch(UnknownHostException e){
            e.printStackTrace();
        }
        catch(IOException e){
            e.printStackTrace();
        }
        catch(Error e){
            e.printStackTrace();
        }


    }  
}
