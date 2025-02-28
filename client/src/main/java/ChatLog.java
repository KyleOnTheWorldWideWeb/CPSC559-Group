/**
 * Simple stub class for what a chatlog should contain
 */
public class ChatLog extends AbstractChatLog {
    private ArrayList<AbstractMessage> messages;
    
    /*
     * Create a chatlog
     */
    public ChatLog(){
        this.messages = new ArrayList<AbstractMessage>(); 
    }

    /**
     * Allows a new message to be inserted into the message log
     * @param newMessage
     */
    public void addMessage(AbstractMessage newMessage){
        this.messages.add(newMessage); // insert the newMessage
    }

    /**
     * returns all messages? maybe?
     */
    public ArrayList<AbstractMessage> getMessageLog(){
        return this.messages;
    }

}
