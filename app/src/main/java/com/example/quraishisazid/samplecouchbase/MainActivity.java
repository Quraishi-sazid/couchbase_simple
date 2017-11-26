package com.example.quraishisazid.samplecouchbase;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.JsonDocument;
import com.couchbase.lite.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

public class MainActivity extends Activity implements View.OnClickListener, Replication.ChangeListener {
    private static final String DATABASE_NAME = "todo";
    private EditText nameEditText;
    private EditText rollEditText;
    private EditText changedNameEditText;
    private EditText changedRollEditText;
    private TextView finalText;
    private String string="abc";
    public static final String SYNC_URL = "http://193.34.145.251:4984/todo";

    protected static Manager manager;
    private Database database;
    private LiveQuery liveQuery;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        finalText=(TextView)findViewById(R.id.set_query);
        changedNameEditText=(EditText)findViewById(R.id.changebale_name);
        rollEditText=(EditText)findViewById(R.id.changebale_roll);
        Button pushButton=(Button)findViewById(R.id.push_button);
        Button changeButton=(Button)findViewById(R.id.change_button);
        pushButton.setOnClickListener(this);
        changeButton.setOnClickListener(this);
        try {
            startCBLite();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    protected void startCBLite() throws Exception {

        manager = new Manager(new AndroidContext(getApplicationContext()), Manager.DEFAULT_OPTIONS);

        DatabaseOptions options = new DatabaseOptions();
        options.setCreate(true);
        database = manager.openDatabase(DATABASE_NAME, options);
        com.couchbase.lite.View nameView = database.getView("nameView");
        nameView.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Object object = document.get("Name");

                if (object != null) {
                    emitter.emit(object.toString(), document);
                }
            }
        }, "1.0");
        startLiveQuery(nameView);
        startSync();
        runQuery();

    }

    private void runQuery() {
        Query query = database.getView("nameView").createQuery();
        query.setStartKey("a");
        query.setEndKey("z");
        QueryEnumerator result = null;
        try {
            result = query.run();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        for (Iterator<QueryRow> it = result; it.hasNext(); ) {
            QueryRow row = it.next();
            string+=row.getKey()+" value= "+row.getValue()+"\n\n\n";
        }
        finalText.setText(string);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()) {

            case R.id.push_button:
                try {
                    pushIntoDatabase();
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
                break;

            case R.id.change_button:
                try {
                    changeRoll();
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
                break;


        }

    }

    private void changeRoll() throws CouchbaseLiteException {
        changedNameEditText=(EditText)findViewById(R.id.changebale_name);
        changedRollEditText=(EditText)findViewById(R.id.changebale_roll);
        if (!changedNameEditText.getText().toString().equals("") && !changedRollEditText.getText().toString().equals("")){
            Query query = database.getView("nameView").createQuery();
            query.setStartKey(changedNameEditText.getText().toString());
            query.setEndKey(changedNameEditText.getText().toString());
            QueryEnumerator result=null;
            result=query.run();
            for (Iterator<QueryRow>it=result;it.hasNext();){
                QueryRow row=it.next();
                Document document=row.getDocument();
                Map<String, Object> newProperties = new HashMap<String, Object>(document.getProperties());
                newProperties.put("Roll", changedRollEditText.getText().toString());
                document.putProperties(newProperties);
            }
            Toast.makeText(this,rollEditText.getText().toString(),Toast.LENGTH_LONG).show();
        }

    }

    private void pushIntoDatabase() throws CouchbaseLiteException {
        nameEditText=(EditText)findViewById(R.id.name);
        rollEditText=(EditText)findViewById(R.id.roll);
        if (!nameEditText.getText().toString().equals("") && !rollEditText.getText().toString().equals("")){
            Map<String, Object> properties = new HashMap<String, Object>();
            Document document=database.createDocument();
            properties.put("Name",nameEditText.getText().toString());
            properties.put("Roll",rollEditText.getText().toString());
            document.putProperties(properties);
        }

    }

    private void startSync() {

        URL syncUrl;
        try {
            syncUrl = new URL(SYNC_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        Replication pullReplication = database.createPullReplication(syncUrl);
        pullReplication.setContinuous(true);

        Replication pushReplication = database.createPushReplication(syncUrl);
        pushReplication.setContinuous(true);

        pullReplication.start();
        pushReplication.start();

        pullReplication.addChangeListener(this);
        pushReplication.addChangeListener(this);

    }

    @Override
    public void changed(Replication.ChangeEvent event) {
        ;
    }

    private void startLiveQuery(com.couchbase.lite.View view) throws Exception {

        if (liveQuery == null) {

            liveQuery = view.createQuery().toLiveQuery();

            liveQuery.addChangeListener(new LiveQuery.ChangeListener() {
                public void changed(final LiveQuery.ChangeEvent event) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            string="";
                            for (Iterator<QueryRow> it = event.getRows(); it.hasNext();) {
                                QueryRow row=it.next();
                               string+= row.getValue()+"\n\n\n";
                                //grocerySyncArrayAdapter.add(it.next());
                            }
                            finalText.setText(string);

                        }
                    });
                }
            });
            liveQuery.start();
        }
    }
}
