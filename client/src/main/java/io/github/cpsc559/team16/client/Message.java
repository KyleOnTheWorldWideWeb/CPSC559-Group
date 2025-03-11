package io.github.cpsc559.team16.client; // this will need to be changed when we restructure things later!

import java.util.Date;
/**
 * Wrapper class to abstract away what a message even is
 */
public class Message extends AbstractMessage{
    private String sender;
    private String content;
    private String receiver;
    private Date timeSent;

    public Message(String sender, String receiver, String content){
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timeSent = new Date();// get current time and date
    }

    /**
     * Returns the sender username of the message
     * @return
     */
    public String getSender(){
        return this.sender;
    }

    /**
     * Returns the reciever username of the message
     */
    public String getReciever(){
        return this.receiver;
    }

    /**
     * returns the message content of the message
     */
    public String getContent(){
        return this.content;
    }

}
