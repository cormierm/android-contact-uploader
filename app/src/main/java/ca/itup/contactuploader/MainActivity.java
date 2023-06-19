package ca.itup.contactuploader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CONTACTS_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button submitButton = findViewById(R.id.btnSubmit);
        EditText phoneEditText = findViewById(R.id.etPhone);
        EditText pushoverEditView = findViewById(R.id.etPushoverUserKey);
        TextView resultsTextView = findViewById(R.id.tvResults);

        submitButton.setOnClickListener(v -> {
            // Check for contacts permission
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS_PERMISSION);
            } else {
                String phoneNumber = phoneEditText.getText().toString();
                String pushoverUserKey = pushoverEditView.getText().toString();

                sendContactsToAWS(phoneNumber, pushoverUserKey, resultsTextView);
            }
        });
    }

    public void sendContactsToAWS(String phoneNumber, String pushoverUserKey, TextView resultsTextView) {
        Thread networkThread = new Thread(() -> {
            JSONArray contactsList = retrieveContacts(pushoverUserKey);

            sendRequest(contactsList, phoneNumber, pushoverUserKey, resultsTextView);
        });
        networkThread.start();
    }

    private void sendRequest(JSONArray contactsList, String phoneNumber, String pushoverUserKey, TextView resultsTextView) {
        try {
            // add to local.properties 'apiUrl=https://example.com/api/endpoint'
            URL url = new URL(BuildConfig.API_URL);
            resultsTextView.setText("Uploading to " + url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            JSONObject payload = new JSONObject();
            payload.put("contacts", contactsList);
            payload.put("phone_number", phoneNumber);
            payload.put("pushover_user_id", pushoverUserKey);

            Log.d("Payload: ", payload.toString());
            resultsTextView.append("\n\nPayload: " + payload);

            OutputStream outputStream = connection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
            writer.write(payload.toString());
            writer.flush();
            writer.close();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            resultsTextView.append("\n\nResponse Code: " + responseCode);

            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            resultsTextView.append("\n\nException: " + e.toString());
        }
    }

    @SuppressLint("Range")
    private JSONArray retrieveContacts(String pushoverUserKey) {
        JSONArray contactsArray = new JSONArray();

        Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                null,
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range") String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                // Retrieve the phone number for the contact
                @SuppressLint("Range") String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                Cursor phoneCursor = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{contactId},
                        null
                );

                String phoneNumber = "";
                if (phoneCursor != null && phoneCursor.moveToFirst()) {
                    phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    phoneCursor.close();

                    phoneNumber = hashPhoneNumber(pushoverUserKey, formatPhoneNumber(phoneNumber));
                }

                try {
                    JSONObject contactObject = new JSONObject();
                    contactObject.put("number", phoneNumber);
                    contactObject.put("name", contactName);
                    contactsArray.put(contactObject);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } while (cursor.moveToNext());

            cursor.close();
        }

        return contactsArray;
    }

    public String hashPhoneNumber(String pushoverUserKey, String phoneNumber) {
        try {
            String textToHash = pushoverUserKey + phoneNumber;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(textToHash.getBytes());

            // Convert the hashed bytes to a hexadecimal string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String formatPhoneNumber(String phoneNumber) {
        phoneNumber = phoneNumber.replaceAll("[^\\d]", "");

        if (!phoneNumber.startsWith("1")) {
            return "1" + phoneNumber;
        }

        return phoneNumber;
    }
}