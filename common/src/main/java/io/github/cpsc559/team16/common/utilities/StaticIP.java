package io.github.cpsc559.team16.common.utilities;


import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;

import java.net.URI;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;

/**
 * Handles HTTP communication to set and/or get an IP address stored on a dedicated website.
 * Website is found using DNS lookup (normal way)
 * We assume that DNS works and that this website cannot fail.
 */
public final class StaticIP {

    // The URL and path for the request
    // should never need to be changed
    private static String STATIC_URL = "www.notchloerobitaille.com";
    private static String PATH = "/cpsc_559/address_server_ip";


    public static String getIP(){
            // formats an HTTP GET request for the resource
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + STATIC_URL + PATH))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .GET()
                .build();
            // creates an Http request to fetch the reference

            // create an Http client to send the request to the web server
            // connection is handled synchronously; will block
            HttpClient client = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NORMAL)
                .connectTimeout(Duration.ofMinutes(1))
                .proxy(HttpClient.Builder.NO_PROXY)//
                .build();

            // send the request, parse the response
            HttpResponse<String> response;
            try{
                response = client.send(request, BodyHandlers.ofString());
            }
            catch (InterruptedException e){
                e.printStackTrace();
                return null;
            }
            catch(IOException e){
                e.printStackTrace();
                return null;
            }

            if(response.statusCode() != 200){
                // the GET request failed
                throw new Error("HTTP GET request failed");
            }

            // else, return the stored value
            return response.body();

    }


    public static int setIP(InetAddress newIP){
            // get the IP address as a string
            String ip_str = newIP.getHostAddress();

            // formats an HTTP PUT request for the resource
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://" + STATIC_URL + PATH))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .PUT(BodyPublishers.ofString(ip_str + "\r\n"))
                .build();
            // creates an Http request to update the reference

            // create an Http client to send the request to the web server
            // connection is handled synchronously; will block
            HttpClient client = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .proxy(HttpClient.Builder.NO_PROXY)//
                .build();

            // send the request, parse the response
            HttpResponse<String> response;
            try{
                response = client.send(request, BodyHandlers.ofString());

                System.out.println("Response: " + response.toString() + "\n");
            }
            catch (InterruptedException e){
                e.printStackTrace();
                return -1;
            }
            catch(IOException e){
                e.printStackTrace();
                return -1;
            }

            // else, return the reponse status code
            return response.statusCode();
    }


}

    
