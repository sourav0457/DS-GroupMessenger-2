package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */
        ArrayList<String> retrievedValues = new ArrayList<String>();
        Set<Map.Entry<String, Object>> entries = values.valueSet();
        for(Map.Entry<String, Object> entry: entries) {
            retrievedValues.add(entry.getValue().toString());
        }

        Log.d("insert", "value: " + retrievedValues.get(0) + ", key: " + retrievedValues.get(1));
        Log.v("insert", values.toString());

        String fileName = retrievedValues.get(1)+".txt";
        String valueToStore = retrievedValues.get(0);
        Context context = getContext();

        try {
            Log.d("Saving Files: ", "Creating file");
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(fileName, Context.MODE_PRIVATE));
            outputStreamWriter.write(valueToStore);
            outputStreamWriter.close();
            Log.d("Saving Files: ", "Successfully written to file");
        }
        catch(IOException e) {
            Log.e("Exception", "File Write Failed");
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */
        String key = selection;
        String valueRead = "";
        Log.d("FileToOpen => ", key);
        try {
            InputStream inputStream = getContext().openFileInput(key+".txt");
            if(inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                valueRead = bufferedReader.readLine();
                inputStream.close();
                Log.d("Read => ", valueRead);
            }
        }
        catch (FileNotFoundException e){
            Log.e("Read ", "File " + key + " not found!: " + e.toString());
        }
        catch (IOException e) {
            Log.e("Read ", "Cannot read file: " + e.toString());
        }
        Log.v("query", selection);
        String[] columns = new String[]{"key", "value"};
        String[] retrievedValues = new String[]{key, valueRead};
        MatrixCursor cur = new MatrixCursor(columns);
        cur.addRow(retrievedValues);
        return cur;
    }
}
