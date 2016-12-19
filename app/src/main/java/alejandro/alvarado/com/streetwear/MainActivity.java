package alejandro.alvarado.com.streetwear;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private ImageView mImageView;
    private Uri mImageURI;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageDisplay);
    }

    public void takePicture (View view) {
        // Ask the system whether we have a connection to the internet
        // using wifi or data
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            // The phone is online, we can safely try to take a picture
            // and upload to the server
            dispatchPictureIntent();
        }
    }
/*
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ...
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }
    */
    private void dispatchPictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE); //returns result on onActivityResult method
        }
    }
/*
    private File createImageFile() throws IOException {
        // Create the image file with a unique name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String mImageFileName = "IMAGE_" + timeStamp + "_";
        // Get the directory to our file provider.
        // The information for this provider is given in the manifest as <provider>
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                mImageFileName,
                ".jpg",
                storageDir
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }
*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The image application has returned and if everything is ok, proceed to reading
        // image from URI
        if (requestCode == REQUEST_IMAGE_CAPTURE){
            if (RESULT_OK == -1) {
                mImageURI = data.getData();
                InputStream inputStream;
                try {
                    inputStream = getContentResolver().openInputStream(mImageURI);
                    Bitmap bitmapImage = BitmapFactory.decodeStream(inputStream);
                    mImageView.setImageBitmap(bitmapImage);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Not gallery found", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void uploadImage(View view){
        try {
            // Open the file descriptor from the URI we had saved before
            AssetFileDescriptor assetFileDescriptor =
                    getContentResolver().openAssetFileDescriptor(mImageURI, "r");
            FileDescriptor fileDescriptor = assetFileDescriptor.getFileDescriptor();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // Convert the image at that location into a JPEG file
            BitmapFactory.decodeFileDescriptor(fileDescriptor).
                    compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            // Save that image into a byte array
            byte[] imageBytes = byteArrayOutputStream.toByteArray();
            assetFileDescriptor.close();
            // Try to delete the image from the phone
            // TODO: 12/17/16 Image is not being deleted properly
            this.getContentResolver().delete(mImageURI, null, null);
            // Upload image to the server
            new UploadImage().execute(imageBytes);
        } catch (FileNotFoundException e) {
            Log.d("OnActivityResult", "Image file was not able to be found.");
        } catch (NullPointerException e) {
            Log.d("OnActivityResult", "Image file was not able to be found");
        } catch (IOException e) {
            Log.d("OnActivityResult", "IO Exception thrown.");
        }
    }


    // Async class that will allow us to upload a file to our server
    private class UploadImage extends AsyncTask<byte[], Void, String> {

        // Used to prepare the image for upload
        private final String output_url = "https://streetanalyzer.herokuapp.com/file";
        private final String attachmentName = "bitmap";
        private final String attachmentFileName = "bitmap.bmp";
        private final String crlf = "\r\n";
        private final String twoHyphens = "--";
        private final String boundary = "*****";

        @Override
        protected String doInBackground(byte[]... params) {
            try {
                return uploadImage(params[0]);
            } catch (IOException e) {
                return "Unable to upload image.";
            }
        }

        private String uploadImage(byte[] imageBytes) throws IOException {

            // Open a connection to the StreetWear Server
            URL url = new URL(output_url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                // Apply some settings so that we can execute our POST request
                conn.setDoOutput(true);
                conn.setChunkedStreamingMode(0);
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Cache-Control", "no-cache");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" +
                    this.boundary);

                // Send the message to the server
                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                writeStream(out, imageBytes);

                // Get the response from the server
                InputStream in = new BufferedInputStream(conn.getInputStream());
                readStream(in);
            } finally {
                conn.disconnect();
            }
            return null;
        }

        private void writeStream(DataOutputStream out, byte[] imageBytes) {

            try {
                // Prepare the message to the server
                // Must be formatted to the POST standard
                out.writeBytes(this.twoHyphens + this.boundary + this.crlf);
                out.writeBytes("Content-Disposition: form-data; name=\"" +
                    this.attachmentName + "\";filename=\"" +
                    this.attachmentFileName + "\"" + this.crlf);
                out.writeBytes(this.crlf);
                out.write(imageBytes);
                out.writeBytes(this.crlf);
                out.writeBytes(this.twoHyphens + this.boundary + this.twoHyphens +
                    this.crlf);
                out.flush();
                out.close();
            } catch (IOException e) {
                Log.d("writeStream", "IO Exception when writing to out stream.");
            }
        }
        
        private void readStream(InputStream in) {
            String line = "";
            try {
                // Gather the response from the server
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                StringBuilder response = new StringBuilder(in.available());
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
                reader.close();
                // Do something with the response
                // TODO: 12/17/16 The response will tell us what the results of the analyse were
                // Need to map this result onto a map for client to view
                line = response.toString();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("readStream", "Read the line: " + line);

            }

        }
    }

}
