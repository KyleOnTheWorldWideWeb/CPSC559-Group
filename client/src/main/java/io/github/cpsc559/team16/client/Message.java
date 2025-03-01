package io.github.cpsc559.team16.client; // this will need to be changed when we restructure things later!

import java.util.Date;
/**
 * Wrapper class to abstract away what a message even is
 */
public class Message extends AbstractMessage{
    private String sender;
    private String content;
    private Date timeSent;

    public Message(String sender, String content){
        this.sender = sender;
        this.content = content;
        this.timeSent = new Date();// get current time and date
    }
    
}
