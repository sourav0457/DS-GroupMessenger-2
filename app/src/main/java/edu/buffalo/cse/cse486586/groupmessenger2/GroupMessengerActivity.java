package edu.buffalo.cse.cse486586.groupmessenger2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    public class MessageDetails {
        String msgId;
        Double seqNum;
        Boolean isReady;
        String portLabel;

        public MessageDetails(String msgId, Double seqNum, String portLabel){
            this.msgId = msgId;
            this.seqNum = seqNum;
            this.isReady = false;
            this.portLabel = portLabel;
        }

        public void setReady() {
            isReady = true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageDetails that = (MessageDetails) o;
            return msgId.equals(that.msgId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(msgId);
        }
    }

    class MessageComparator implements Comparator<MessageDetails> {
        @Override
        public int compare(MessageDetails x, MessageDetails y){
            return x.seqNum.compareTo(y.seqNum);
        }
    }
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private final Uri mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static ArrayList<String> portList = new ArrayList<String>() {{
        add("11108");
        add("11112");
        add("11116");
        add("11120");
        add("11124");
    }};
//    static String[] portList = {"11108", "11112", "11116", "11120", "11124"};
    static final Map<String, String> portMapping = new HashMap<String, String>() {{
        put("11108", "1");
        put("11112", "4");
        put("11116", "3");
        put("11120", "5");
        put("11124", "2");
    }};
    static final Map<String, String> msgIdMapping = new HashMap<String, String>() {{    // Used to provide a unique message id for each message sent
        put("11108", "a");
        put("11112", "b");
        put("11116", "c");
        put("11120", "d");
        put("11124", "e");
    }};
    PriorityBlockingQueue<MessageDetails> deliverPriority = new PriorityBlockingQueue<MessageDetails>(10,new MessageComparator());
    static Map<String, List<Double>> maxProp = new HashMap<>();
    static Map<String, String> messages = new HashMap<>();
    static final int SERVER_PORT = 10000;
    static String myPort = "";
    String crashedAvdPortValue = "";
    AtomicInteger sequenceNumber = new AtomicInteger(0);
    Integer count = 0;
    AtomicInteger recipientCount = new AtomicInteger(5);
    Integer msg_sent_count = 0;

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        @SuppressLint("MissingPermission") String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        Log.d("Custom", "port number base: "+portStr);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            e.printStackTrace();

            return;
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Button sendButton = (Button) findViewById(R.id.button4);
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));
        
        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {                                        // Since only one parameter was passed (socket instance), sockets[0] contains the socket instance
            ServerSocket serverSocket = sockets[0];

            try {
                while(true) {
                    Socket socket = serverSocket.accept();
                    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                    String[] objectRead = (String[]) ois.readObject();  // Value received: [msgType, msgId, msg, portLabel] OR [msgType, msgId, finalSeq, portLabel] OR [msgType, port, crashedPortLabel, recipientCountToSend]

                    if(objectRead[0].equals("1")){
                        int sentProposal = sequenceNumber.incrementAndGet();
                        String sequenceNumberToSend = "" + sentProposal + "." + portMapping.get(myPort);
                        String[] msgToSend = {objectRead[1], sequenceNumberToSend};
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        out.writeObject(msgToSend);
                        Log.d(TAG, " Proposal Sent: " + myPort + " | " + msgToSend[0] + " | " + msgToSend[1]);

                        // Storing [messageId, message] in messages hashmap
                        messages.put(objectRead[1], objectRead[2]);
                        // Add the object [msgId, sequenceNumber, flag] to priority queue
                        MessageDetails msgObj = new MessageDetails(objectRead[1], Double.valueOf(sequenceNumberToSend), objectRead[3]);
                        deliverPriority.add(msgObj);
                    }
                    else if(objectRead[0].equals("2")){

                        // Create new object and store the msgId along with the final sequence number and portLabel
                        MessageDetails updatedMessage = new MessageDetails(objectRead[1], Double.valueOf(objectRead[2]), objectRead[3]);
                        // Set the flag to true as it can now be removed from the Priority Queue
                        updatedMessage.setReady();

                        // Delete the old instance using the above object as the msgId will be the same (which is being used for comparison)
                        deliverPriority.remove(updatedMessage);
                        // Put the new instance into the priority queue
                        deliverPriority.add(updatedMessage);
                        // If the sequence number received for the new message is greater than personal sequence number, then update it so that the next sequence number being sent is correct
                        if(sequenceNumber.get() <  updatedMessage.seqNum.intValue()){
                            sequenceNumber.set(updatedMessage.seqNum.intValue());
                        }
                        Log.d(TAG, " Final Proposal Received: " + myPort + " | " + updatedMessage.msgId + " | " + messages.get(updatedMessage.msgId) + " | " + updatedMessage.seqNum);

                        // Check if the value is at the top of the priority queue. If yes, then while root.flag = 1 -> send to publish progress with an increment of count value
                        // Also, if the portLabel at the top of the Priority Queue belongs to a failed emulator, then remove it from the priority queue
                        while(!deliverPriority.isEmpty() && (deliverPriority.peek().isReady || deliverPriority.peek().portLabel.equals(crashedAvdPortValue))){
                            if(deliverPriority.peek().portLabel.equals(crashedAvdPortValue)){
                                // Discard the root of the Priority Queue
                                deliverPriority.poll();
                                continue;
                            }
                            MessageDetails messageToDeliver = deliverPriority.poll();
                            publishProgress(messages.get(messageToDeliver.msgId), String.valueOf(messageToDeliver.seqNum));

                        }
                    }
                    else if(objectRead[0].equals("3")){
                        // Code for msgType 3
                        // Check value of recipient count. If it is less than current count, then update value, else ignore
                        crashedAvdPortValue = objectRead[2];
                        if(recipientCount.get() > Integer.parseInt(objectRead[3])){
                            recipientCount.set(Integer.parseInt(objectRead[3]));
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to accept server socket connections");
                e.printStackTrace();
            } catch (ClassNotFoundException e){
                Log.e(TAG, "Could not find Class");
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {

            String strReceived = strings[0].trim();
            TextView view = (TextView) findViewById(R.id.textView1);
            view.append("" + count + "|" + strings[1] +"|" + strReceived + "\t\n");

            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            ContentResolver cr = getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, count++);
            cv.put(VALUE_FIELD, strReceived);
            try {
                cr.insert(mUri, cv);
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {                                                 // msges array contains the msg at index 0 and port at index 1
            try {
                // Calculate the value of msgId
                msg_sent_count += 1;
                String portLabel = msgIdMapping.get(myPort);
                String msgId = msgIdMapping.get(myPort) + msg_sent_count;
                List<Double> propValues = new ArrayList<>();
                // Number of proposals received
                propValues.add(0.0);
                // Value of highest proposal
                propValues.add(0.0);
                maxProp.put(msgId, propValues);
                String msgType = "0";
                boolean hasAvdCrashed = false;
                List<String> crashedAvds = new ArrayList<>();

                for(String port: portList) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(port));
//                        socket.setSoTimeout(5000);
                        msgType = "1";
                        String[] msgToSend = {msgType, msgId, msgs[0], portLabel};
                        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                        out.writeObject(msgToSend);
                        Log.d(TAG, " Initiate Message: " + myPort + " | " + port + " | " + msgToSend[0] + " | " + msgToSend[1] + " | " + msgToSend[2]);

                        ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                        String[] objectRead = null;
                        while ((objectRead = (String[]) ois.readObject()) == null) ;

                        maxProp.get(objectRead[0]).set(0, maxProp.get(objectRead[0]).get(0) + 1);   // Incrementing the count value by 1
                        Log.d(TAG, " Proposal Received: " + myPort + " | " + port + " | " + objectRead[0] + " | " + objectRead[1]);

                        // Setting the value of the maxProp value for the msgId of the received message
                        if (Double.valueOf(objectRead[1]) > maxProp.get(objectRead[0]).get(1)) {
                            maxProp.get(objectRead[0]).set(1, Double.valueOf(objectRead[1]));
                        }
                    }
                    catch(IOException e){
                        // Remove the port from the port list
                        Log.e(TAG, "Inside catch statement for port: " + port);
                        hasAvdCrashed = true;
                        crashedAvds.add(port);
                        String crashedPortLabel = msgIdMapping.get(port);
                        String recipientCountToSend = "" + recipientCount.getAndDecrement();
                        // Broadcast that the avd has died to everyone in the network [Msg Type - 3]
                        msgType = "3";
                        for(String broadcastPort: portList){
                            if(broadcastPort.equals(port)) continue;
                            Socket broadcastSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(broadcastPort));
                            // decrement recipient count and send it as part of the message so that it can be set on the server side
                            String[] broadcastMessage = {msgType, port, crashedPortLabel, recipientCountToSend};
                            ObjectOutputStream broadcastOut = new ObjectOutputStream(broadcastSocket.getOutputStream());
                            broadcastOut.writeObject(broadcastMessage);
                        }
                        continue;
                    }
                }
                if(hasAvdCrashed){
                    // Remove AVDs in crashedAvds from the port list
                    for(String crashedPort: crashedAvds) {
                        portList.remove(crashedPort);
                    }
                    // Once the ports of the crashedAVDs have been removed from the portList, clear the crashedAVDs list
                    crashedAvds.clear();
                    hasAvdCrashed = false;  //redundant statement as next time the client is called, this will be set to false at the top
                }
                if(maxProp.get(msgId).get(0) >= recipientCount.get()){
                    double finalSeqNum = maxProp.get(msgId).get(1);
                    msgType = "2";
                    // Send out the updated sequence number to everyone
                    for(String port: portList){
                        try {
                            Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                    Integer.parseInt(port));
                            String[] msgToSend = {msgType, msgId, String.valueOf(finalSeqNum), portLabel};
                            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                            out.writeObject(msgToSend);
                            Log.d(TAG, " Final Proposal: " + myPort + " | " + port + " | " + msgToSend[1] + " | " + msgToSend[2]);
                        }
                        catch(IOException e){

                            Log.e(TAG, "Inside catch statement for port: " + port);
                            hasAvdCrashed = true;
                            crashedAvds.add(port);
                            String crashedPortLabel = msgIdMapping.get(port);
                            String recipientCountToSend = "" + recipientCount.getAndDecrement();
                            msgType = "3";
                            for(String broadcastPort: portList){
                                if(broadcastPort.equals(port)) continue;
                                Socket broadcastSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                        Integer.parseInt(broadcastPort));
                                // decrement recipient count and send it as part of the message so that it can be set on the server side
                                String[] broadcastMessage = {msgType, port, crashedPortLabel, recipientCountToSend};
                                ObjectOutputStream broadcastOut = new ObjectOutputStream(broadcastSocket.getOutputStream());
                                broadcastOut.writeObject(broadcastMessage);
                            }
                            continue;

                        }
                    }
                }
                if(hasAvdCrashed){
                    // Remove AVDs in crashedAvds from the port list
                    for(String crashedPort: crashedAvds) {
                        portList.remove(crashedPort);
                    }
                    // Once the ports of the crashedAVDs have been removed from the portList, clear the crashedAVDs list
                    crashedAvds.clear();
                    hasAvdCrashed = false;  //redundant statement as next time the client is called, this will be set to false at the top
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}
