package com.denzygames.remotecommand;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;


public class MainActivity extends AppCompatActivity{
    String log_tag = "UDPMainActivity";

    static final int READ_CODE =123;
    static final int WRITE_CODE =321;

    UDPArchitecture udp;
    int port = -1;

    Button btHost;
    Button btConnect;
    Button btStop;

    EditText etAddress;
    EditText etPort;
    EditText etMessage;

    TextView tvLog;

    InetAddress address = null;

    SharedPreferences sharedPreferences;

    //Stack vs Heap
    byte[] rec_buf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();

        //No penalites. Living dangerously....
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        sharedPreferences = getSharedPreferences(
                formatStr("%s.%s",getPackageName(), getString(R.string.shared_pref_file)),
                Context.MODE_PRIVATE);

        String load_addr = sharedPreferences.getString(getString(R.string.shared_pref_address_key), getString(R.string.default_address));
        int load_port = sharedPreferences.getInt(getString(R.string.shared_pref_port_key), Integer.parseInt(getString(R.string.default_port)));

        etAddress.setText(load_addr);
        etPort.setText(formatStr("%d", load_port));
    }

    void findViews()
    {
        btHost = findViewById(R.id.btHost);
        btConnect = findViewById(R.id.btConnect);
        btStop = findViewById(R.id.btStop);

        etAddress = findViewById(R.id.etAddress);
        etPort = findViewById(R.id.etPort);
        etMessage = findViewById(R.id.etMessage);

        tvLog = findViewById(R.id.tvLog);
    }

    void addReceiver()
    {
        IReceive receive = new IReceive() {
            @Override
            public void receive(byte[] data, SocketAddress socketAddress) {
                try {
                    String head = new String(data,0,1);
                    if(head.charAt(0) == 'M')
                    {
                        String msg = new String(data,1,data.length -1);
                        logme(formatStr("%s > %s", socketAddress.toString().substring(1), msg));
                    }else if (head.charAt(0) == 'F')
                    {
                        saveUDPFile();
                        rec_buf = data;
                    }else
                    {
                        logme("Unsupported message head!");
                    }
                }catch (Exception e)
                {
                    e.printStackTrace();
                    logme(formatStr("Could not decode received message"));
                }
            }
        };
        udp.addSubscriptionReceive(receive);
    }

    public void hostUDP(View view) throws SocketException, UnknownHostException {
        try {
            port = Integer.parseInt(etPort.getText().toString());
        }catch(Exception ex)
        {
            Toast.makeText(this, "Incorrect port to start a host", Toast.LENGTH_SHORT).show();
            return;
        }

        udp = new UDPArchitecture(port);

        if(udp.runningMode != UDPArchitecture.RunningMode.None)
        {
            udp.start();


            disableConButtons(false);
            addReceiver();
            logme(formatStr("Starting on port %d", port));
            saveToPrefs();
        }else
        {
            Toast.makeText(this, "Failed to start host! Try a different port.", Toast.LENGTH_SHORT).show();
        }
    }

    public void connectUDP(View view) throws SocketException {

        //Allows us to get address
        try {
            port = Integer.parseInt(etPort.getText().toString());
        }catch (Exception ex)
        {
            Toast.makeText(this, "Incorrect port", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            address = InetAddress.getByName(etAddress.getText().toString().trim());
        }catch(Exception ex)
        {
            Toast.makeText(this, "Incorrect address", Toast.LENGTH_SHORT).show();
            return;
        }

        udp = new UDPArchitecture(address, port);
        if(udp.runningMode != UDPArchitecture.RunningMode.None)
        {
            udp.start();
            addReceiver();
            disableConButtons(false);
            logme(formatStr("Connected on %s:%d",address.toString().substring(1), port));
            saveToPrefs();
        }else
        {
            Toast.makeText(this, "Unable to connect to host", Toast.LENGTH_SHORT).show();
        }

    }

    public void sendUDPText(View view) throws IOException {
        String message = etMessage.getText().toString();
        if(udp != null) {
            //Head with m = message

            String head = "M";
            byte[] data = new byte[head.getBytes().length + message.getBytes().length];
            System.arraycopy(head.getBytes(), 0, data, 0, head.getBytes().length);
            System.arraycopy(message.getBytes(), 0, data, head.getBytes().length, message.getBytes().length);
            udp.send(data);
            logme(formatStr("Me > %s", message));
        }else {
            Toast.makeText(this, "Connect/Host before sending message", Toast.LENGTH_SHORT).show();
        }
    }

    public void sendUDPFile(View view) throws IOException
    {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        startActivityForResult(Intent.createChooser(intent,"Select a file"),READ_CODE);
    }

    public void saveUDPFile()
    {
        runOnUiThread(() ->
            {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Save File?");
                builder.setMessage("You have received a file. Would you like to save it?");
                builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        logme("Saved file");

                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");

                        startActivityForResult(Intent.createChooser(intent,"Choose where to save"),WRITE_CODE);
                    }
                });

                builder.setNegativeButton("No",null);
                AlertDialog dialog =  builder.show();
            }
        );

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == READ_CODE && resultCode == RESULT_OK)
        {
            Uri selected = data.getData();

            File f;
            try {
                ParcelFileDescriptor fd= getContentResolver().openFileDescriptor(selected,"r");
                InputStream stream = new FileInputStream(fd.getFileDescriptor());

                byte[] file_buffer = new byte[stream.available()];
                int read = stream.read(file_buffer);

                //Write f header
                String head = "F";
                byte[] send_buffer = new byte[head.getBytes().length + file_buffer.length];
                System.arraycopy(head.getBytes(), 0, send_buffer, 0, head.getBytes().length);
                System.arraycopy(file_buffer, 0, send_buffer, head.getBytes().length, file_buffer.length);
                logme(formatStr("Read %d bytes.", read));

                udp.send(send_buffer);
            } catch (Exception e) {
                Toast.makeText(this, "Cant parse URI", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }

        if(requestCode == WRITE_CODE && resultCode == RESULT_OK)
        {
            try {
                OutputStream stream = getContentResolver().openOutputStream(data.getData());
                stream.write(rec_buf);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    void saveToPrefs()
    {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(getString(R.string.shared_pref_address_key), etAddress.getText().toString());
            editor.putInt(getString(R.string.shared_pref_port_key), Integer.parseInt(etPort.getText().toString()));
            editor.apply();
        }catch (Exception ex)
        {
            Log.e(log_tag,"Cannot save preferences");
            ex.printStackTrace();
        }
    }

    String formatStr(String format,Object... args)
    {
        return String.format(Locale.getDefault(),format,args);
    }

    public void writeFile(byte[] data) throws IOException {
        String dir = this.getApplicationInfo().dataDir;
        String file_name = "receive.txt";

        //There is no i know to combine paths in java

        File f = new File(dir+"/"+file_name);
        if(f.createNewFile())
        {
         Log.i(log_tag,"File created...");
        }

        RandomAccessFile file = new RandomAccessFile(f.getPath(),"rw");
        file.write(data);
        file.close();
    }

    public void clear(View view)
    {
        TextView textView = findViewById(R.id.tvLog);
        textView.setText("Log:");
    }

    public void stop(View view)
    {
        disableConButtons(true);
        udp.running = false;
        udp.socket.close();;
        udp = null;
        logme("Closed socket");
    }

    void disableConButtons(boolean state)
    {
        btStop.setEnabled(!state);
        btConnect.setEnabled(state);
        btHost.setEnabled(state);

        etAddress.setEnabled(state);
        etPort.setEnabled(state);
    }

    public void logme(String message)
    {
        runOnUiThread(() -> {

            Log.i(log_tag, message);

            TextView textView = findViewById(R.id.tvLog);
            String text = textView.getText().toString();
            text+="\n"+message;
            textView.setText(text);
        });

    }
}
